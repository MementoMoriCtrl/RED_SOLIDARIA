package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import java.math.BigDecimal;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import pe.redsolidaria.modules.donations.api.dto.DonationReceiptDtos.*;
import pe.redsolidaria.modules.donations.application.DonationReceiptService;
import pe.redsolidaria.modules.deliveries.api.dto.DeliveryDtos.SaveDeliveryRequest;
import pe.redsolidaria.modules.deliveries.application.DeliveryManagementService;
import pe.redsolidaria.modules.emergencies.persistence.EmergencyBalanceRepository;
import pe.redsolidaria.test.ControlledTestMySqlCondition;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@ExtendWith(ControlledTestMySqlCondition.class)
class PhaseOneControlledMySqlIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tx;
    @Autowired DonationReceiptService receipts;
    @Autowired DeliveryManagementService deliveries;
    @Autowired EmergencyBalanceRepository balances;
    @MockitoSpyBean AuditRecordRepository audit;
    long donationId, lineId, resourceId, emergencyId, need1, need2;
    String email;

    @BeforeEach void fixturesInDedicatedDatabaseOnly() {
        assertEquals("red_solidaria_test", jdbc.queryForObject("select database()", String.class));
        assertEquals(2L, jdbc.queryForObject("select count(*) from flyway_schema_history where version in ('1','2') and success = 1", Long.class));
        new TransactionTemplate(tx).executeWithoutResult(status -> {
            String token = UUID.randomUUID().toString();
            email = token + "@integration.test";
            long role = insert("insert into rol(codigo,nombre) values (?,?)", "IT-" + token, "Integration fixture");
            long actor = insert("insert into usuario(id_rol,nombre,apellido,correo,password_hash,estado) values (?,?,?,?,?,?)",
                    role, "Integration", "Fixture", email, "unused-no-login", "ACTIVO");
            long donor = insert("insert into donante(nombre) values (?)", "IT-" + token);
            long unit = insert("insert into unidad_medida(codigo,nombre) values (?,?)", token.substring(0, 30), "Integration unit");
            resourceId = insert("insert into recurso(id_unidad_medida,nombre,categoria,estado) values (?,?,?,?)", unit, "IT-" + token, "IT", "ACTIVO");
            emergencyId = insert("insert into emergencia(nombre,tipo,fecha_inicio,estado) values (?,?,?,?)", "IT-" + token, "TEST", java.sql.Date.valueOf("2026-01-01"), "ACTIVA");
            long zone = insert("insert into zona_afectada(id_emergencia,nombre) values (?,?)", emergencyId, "Integration zone");
            donationId = insert("insert into donacion(id_donante,id_emergencia,id_usuario_registro,codigo_trazabilidad,estado) values (?,?,?,?,?)",
                    donor, emergencyId, actor, "IT-" + token, "REGISTRADA");
            lineId = insert("insert into detalle_donacion(id_donacion,id_recurso,cantidad_donada) values (?,?,?)", donationId, resourceId, new BigDecimal("10.000"));
            need1 = insert("insert into necesidad(id_zona_afectada,id_recurso,cantidad_requerida,prioridad,estado) values (?,?,?,?,?)", zone, resourceId, 8, "ALTA", "PENDIENTE");
            need2 = insert("insert into necesidad(id_zona_afectada,id_recurso,cantidad_requerida,prioridad,estado) values (?,?,?,?,?)", zone, resourceId, 8, "ALTA", "PENDIENTE");
        });
    }
    RegisterReceiptRequest receipt(String quantity) {
        return new RegisterReceiptRequest(List.of(new ReceiptLineRequest(resourceId, new BigDecimal(quantity))), null, null);
    }
    @Test void concurrentRetriesPersistOnceAndANewOperationCanReceiveAgain() throws Exception {
        var request = receipt("3.000");
        parallel(() -> receipts.register(donationId, email, request), () -> receipts.register(donationId, email, request));
        assertEquals(0, received().compareTo(new BigDecimal("3")));
        assertEquals(1L, operationCount());
        receipts.register(donationId, email, receipt("3.000"));
        assertEquals(0, received().compareTo(new BigDecimal("6")));
        assertEquals(2L, operationCount());
        var changed = new RegisterReceiptRequest(List.of(new ReceiptLineRequest(resourceId, BigDecimal.ONE)), null, null, request.operacionId());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> receipts.register(donationId, email, changed)).getStatusCode().value());
        assertEquals(0, received().compareTo(new BigDecimal("6")));
    }
    @Test void auditFailureRollsBackReceiptMovementAndOperationIdentity() {
        doThrow(new IllegalStateException("Injected audit failure")).when(audit).saveAndFlush(any(AuditRecord.class));
        assertThrows(IllegalStateException.class, () -> receipts.register(donationId, email, receipt("2")));
        assertEquals(0, received().signum());
        assertEquals(0L, operationCount());
        assertEquals(0L, jdbc.queryForObject("select count(*) from movimiento_donacion where id_donacion = ?", Long.class, donationId));
    }
    @Test void competingNeedsCannotOverallocateAndDeliveryDoesNotReserveStockTwice() throws Exception {
        receipts.register(donationId, email, receipt("10"));
        var results = parallel(() -> allocate(need1), () -> allocate(need2));
        assertEquals(1, results.stream().filter(value -> value.equals("OK")).count());
        assertEquals(1, results.stream().filter(value -> value.equals("409")).count());
        long winner = jdbc.queryForObject("select id_necesidad from detalle_entrega where id_detalle_donacion = ? limit 1", Long.class, lineId);
        var target = new SaveDeliveryRequest(new BigDecimal("8"), new BigDecimal("5"));
        deliveries.save(winner, lineId, email, target);
        assertTrue(deliveries.save(winner, lineId, email, target).repetida());
        assertThrows(ResponseStatusException.class, () -> deliveries.save(winner, lineId, email,
                new SaveDeliveryRequest(new BigDecimal("8"), new BigDecimal("9"))));
        var balance = balances.balance(emergencyId).getFirst();
        assertEquals(0, balance.requerida().compareTo(new BigDecimal("16")));
        assertEquals(0, balance.recibidaAcopio().compareTo(new BigDecimal("10")));
        assertEquals(0, balance.asignada().compareTo(new BigDecimal("8")));
        assertEquals(0, balance.entregada().compareTo(new BigDecimal("5")));
        assertEquals(0, balance.disponible().compareTo(new BigDecimal("2")));
        assertEquals(0, balance.faltanteAbastecimiento().compareTo(new BigDecimal("6")));
    }
    String allocate(long need) {
        try { deliveries.save(need, lineId, email, new SaveDeliveryRequest(new BigDecimal("8"), BigDecimal.ZERO)); return "OK"; }
        catch (ResponseStatusException failure) { return String.valueOf(failure.getStatusCode().value()); }
    }
    BigDecimal received() { return jdbc.queryForObject("select cantidad_recibida_acopio from detalle_donacion where id_detalle_donacion = ?", BigDecimal.class, lineId); }
    long operationCount() { return jdbc.queryForObject("select count(*) from recepcion_operacion where id_donacion = ?", Long.class, donationId); }
    long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }
    List<Object> parallel(Callable<?> first, Callable<?> second) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
            var futures = List.of(first, second).stream().map(task -> pool.submit(() -> {
                ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                return (Object) task.call();
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            return List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
        }
    }
}
