package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.*;
import static pe.redsolidaria.modules.donations.api.dto.DonationReceiptDtos.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.donations.application.DonationReceiptService;
import pe.redsolidaria.test.ControlledTestMySqlCondition;
import tools.jackson.databind.json.JsonMapper;

/** Real MySQL only; fixtures are committed and deliberately retained for inspection. */
@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@ExtendWith(ControlledTestMySqlCondition.class)
@WithMockUser(authorities = "ACCESO_ADMINISTRAR")
class ModelBComplementaryMySqlIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        PhaseOneControlledMySqlIntegrationTest.database(registry);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tx;
    @Autowired DonationReceiptService receipts;
    private final PhaseOneControlledMySqlIntegrationTest fixture = new PhaseOneControlledMySqlIntegrationTest();
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach void prepare() {
        fixture.jdbc = jdbc;
        fixture.tx = tx;
        // Reuses DATABASE(), migration-history guards and the existing isolated fixture factory.
        fixture.fixturesInDedicatedDatabaseOnly();
        System.out.printf("MODEL_B_FIXTURE donation=%d line=%d resource=%d%n",
                fixture.donationId, fixture.lineId, fixture.resourceId);
    }
    static BigDecimal q(String value) { return new BigDecimal(value); }
    // JSON storage may change decimal scale; every other response value remains exact.
    static void assertSameResponse(RegisterReceiptResponse expected, RegisterReceiptResponse actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected.donacionId(), actual.donacionId(), "donacionId");
        assertEquals(expected.estado(), actual.estado(), "estado");
        assertEquals(expected.fechaHora(), actual.fechaHora(), "fechaHora");
        assertEquals(expected.ubicacion(), actual.ubicacion(), "ubicacion");
        assertEquals(expected.observaciones(), actual.observaciones(), "observaciones");
        assertEquals(expected.responsableId(), actual.responsableId(), "responsableId");
        assertEquals(expected.centroAcopioId(), actual.centroAcopioId(), "centroAcopioId");
        assertNotNull(expected.detalles());
        assertNotNull(actual.detalles());
        assertEquals(expected.detalles().size(), actual.detalles().size(), "detalles.size");
        for (int i = 0; i < expected.detalles().size(); i++) {
            var left = expected.detalles().get(i);
            var right = actual.detalles().get(i);
            assertNotNull(left);
            assertNotNull(right);
            assertEquals(left.recursoId(), right.recursoId(), "recursoId at " + i);
            assertEquals(left.recursoNombre(), right.recursoNombre(), "recursoNombre at " + i);
            assertEquals(left.cerrada(), right.cerrada(), "cerrada at " + i);
            assertSameQuantity(left.cantidadRecibidaOperacion(), right.cantidadRecibidaOperacion(), "cantidadRecibidaOperacion at " + i);
            assertSameQuantity(left.cantidadRecibidaAcumulada(), right.cantidadRecibidaAcumulada(), "cantidadRecibidaAcumulada at " + i);
            assertSameQuantity(left.cantidadPendiente(), right.cantidadPendiente(), "cantidadPendiente at " + i);
            assertSameQuantity(left.examinada(), right.examinada(), "examinada at " + i);
            assertSameQuantity(left.noAceptada(), right.noAceptada(), "noAceptada at " + i);
            assertSameQuantity(left.excedente(), right.excedente(), "excedente at " + i);
        }
    }
    static void assertSameQuantity(BigDecimal expected, BigDecimal actual, String field) {
        if (expected == null) assertNull(actual, field);
        else {
            assertNotNull(actual, field);
            assertEquals(0, expected.compareTo(actual), () -> field + ": expected " + expected + " but was " + actual);
        }
    }
    RegisterReceiptRequest verification(String accepted, String examined, String rejected, String surplus, boolean close) {
        return new RegisterReceiptRequest(List.of(new ReceiptLineRequest(fixture.resourceId, q(accepted), q(examined),
                q(rejected), q(surplus), close)), null, "Synthetic Model B verification", UUID.randomUUID());
    }
    RegisterReceiptRequest surplus() {
        var request = verification("10", "12", "0", "2", false);
        receipts.register(fixture.donationId, fixture.email, request);
        return request;
    }
    RegisterReceiptResponse decide(RegisterReceiptRequest receipt, DecideSurplusRequest decision) {
        return receipts.decide(fixture.donationId, receipt.operacionId().toString(), fixture.email, decision);
    }
    Object securedDecision(RegisterReceiptRequest receipt, DecideSurplusRequest decision) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(fixture.email, "unused",
                List.of(new SimpleGrantedAuthority("ACCESO_ADMINISTRAR"))));
        SecurityContextHolder.setContext(context);
        try { return decide(receipt, decision); }
        catch (ResponseStatusException failure) { return failure.getStatusCode().value(); }
        finally { SecurityContextHolder.clearContext(); }
    }
    Map<String, Object> decisionRow() {
        return jdbc.queryForMap("select decision,id_usuario_decision,fecha_decision,observaciones_decision,uuid_decision,huella_decision,respuesta_decision,respuesta from recepcion_operacion where id_donacion=?", fixture.donationId);
    }
    long audits(String action) {
        return jdbc.queryForObject("select count(*) from registro_auditoria where entidad='DONACION' and id_registro=? and accion=?",
                Long.class, Long.toString(fixture.donationId), action);
    }
    long movements(String action) {
        return jdbc.queryForObject("select count(*) from movimiento_donacion where id_donacion=? and tipo_evento=?", Long.class, fixture.donationId, action);
    }
    void quantities(String accepted, String examined, String rejected, String pending, String approved) {
        var row = jdbc.queryForMap("select cantidad_donada,cantidad_recibida_acopio,cantidad_examinada,cantidad_no_aceptada,cantidad_excedente_pendiente,cantidad_excedente_aprobada from detalle_donacion where id_detalle_donacion=?", fixture.lineId);
        String[] columns = {"cantidad_donada", "cantidad_recibida_acopio", "cantidad_examinada", "cantidad_no_aceptada", "cantidad_excedente_pendiente", "cantidad_excedente_aprobada"};
        String[] values = {"10", accepted, examined, rejected, pending, approved};
        for (int i = 0; i < columns.length; i++) assertEquals(0, ((BigDecimal) row.get(columns[i])).compareTo(q(values[i])), columns[i]);
    }
    void resolvedOnce(boolean approved) {
        quantities(approved ? "12" : "10", "12", "0", "0", approved ? "2" : "0");
        assertEquals(approved ? "APROBADO" : "RECHAZADO", decisionRow().get("decision"));
        assertEquals(1, audits("AUTORIZACION_EXCEDENTE") + audits("RECHAZO_EXCEDENTE"));
        assertEquals(1, movements("AUTORIZACION_EXCEDENTE") + movements("RECHAZO_EXCEDENTE"));
    }
    @Test void simultaneousIdenticalApprovalsReturnTheSameResponseOnce() throws Exception {
        var receipt = surplus();
        var decision = new DecideSurplusRequest(UUID.randomUUID(), true, "Approved synthetic surplus");
        var results = fixture.parallel(() -> securedDecision(receipt, decision), () -> securedDecision(receipt, decision));
        assertInstanceOf(RegisterReceiptResponse.class, results.getFirst());
        assertSameResponse((RegisterReceiptResponse) results.getFirst(),
                assertInstanceOf(RegisterReceiptResponse.class, results.getLast()));
        assertEquals(0, ((RegisterReceiptResponse) results.getFirst()).detalles().getFirst().cantidadRecibidaAcumulada().compareTo(q("12")));
        assertEquals(decision.operacionId().toString(), decisionRow().get("uuid_decision"));
        resolvedOnce(true);
    }
    @Test void simultaneousDifferentApprovalUuidsHaveOneWinner() throws Exception {
        race(false);
    }
    @Test void simultaneousApprovalAndRejectionHaveOneWinner() throws Exception {
        race(true);
    }
    void race(boolean opposing) throws Exception {
        var receipt = surplus();
        var first = new DecideSurplusRequest(UUID.randomUUID(), true, "First synthetic decision");
        var second = new DecideSurplusRequest(UUID.randomUUID(), !opposing, "Second synthetic decision");
        var results = fixture.parallel(() -> securedDecision(receipt, first), () -> securedDecision(receipt, second));
        assertEquals(1, results.stream().filter(RegisterReceiptResponse.class::isInstance).count());
        assertEquals(1, results.stream().filter(Integer.valueOf(409)::equals).count());
        var winner = results.getFirst() instanceof RegisterReceiptResponse ? first : second;
        assertEquals(winner.operacionId().toString(), decisionRow().get("uuid_decision"));
        var response = (RegisterReceiptResponse) results.stream().filter(RegisterReceiptResponse.class::isInstance).findFirst().orElseThrow();
        assertSameResponse(response, decide(receipt, winner));
        assertEquals(0, response.detalles().getFirst().cantidadRecibidaAcumulada().compareTo(q(winner.aprobar() ? "12" : "10")));
        resolvedOnce(winner.aprobar());
    }
    @Test void rejectionPersistsMetadataAndRetriesWithoutAdditionalEffects() {
        var receipt = surplus();
        String original = decisionRow().get("respuesta").toString();
        var decision = new DecideSurplusRequest(UUID.randomUUID(), false, "Rejected synthetic surplus");
        var response = decide(receipt, decision);
        var before = decisionRow();
        assertSameResponse(response, decide(receipt, decision));
        assertEquals(before, decisionRow());
        assertEquals(original, before.get("respuesta").toString());
        assertEquals(decision.operacionId().toString(), before.get("uuid_decision"));
        assertEquals(decision.observaciones(), before.get("observaciones_decision"));
        assertNotNull(before.get("fecha_decision"));
        long actor = jdbc.queryForObject("select id_usuario from usuario where correo=?", Long.class, fixture.email);
        assertEquals(actor, ((Number) before.get("id_usuario_decision")).longValue());
        assertTrue(before.get("huella_decision").toString().matches("[0-9a-f]{64}"));
        assertSameResponse(response, json.readValue(before.get("respuesta_decision").toString(), RegisterReceiptResponse.class));
        resolvedOnce(false);
    }
    @Test void registrantCannotVerifyAndIndependentUserCan() {
        String registrar = jdbc.queryForObject("select u.correo from usuario u join donacion d on d.id_usuario_registro=u.id_usuario where d.id_donacion=?", String.class, fixture.donationId);
        var request = verification("4", "5", "1", "0", false);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> receipts.register(fixture.donationId, registrar, request)).getStatusCode().value());
        quantities("0", "0", "0", "0", "0");
        assertEquals(0, fixture.operationCount());
        assertEquals(0L, jdbc.queryForObject("select count(*) from movimiento_donacion where id_donacion=?", Long.class, fixture.donationId));
        assertEquals(0L, jdbc.queryForObject("select count(*) from registro_auditoria where entidad='DONACION' and id_registro=?", Long.class, Long.toString(fixture.donationId)));
        receipts.register(fixture.donationId, fixture.email, request);
        quantities("4", "5", "1", "0", "0");
        assertEquals(1, fixture.operationCount());
    }
    @Test void automaticClosureRequiresAllResourcesAndShortClosureCannotReopen() {
        long unit = jdbc.queryForObject("select id_unidad_medida from recurso where id_recurso=?", Long.class, fixture.resourceId);
        long second = fixture.insert("insert into recurso(id_unidad_medida,nombre,categoria,estado) values (?,?,?,?)", unit, "IT-second-" + UUID.randomUUID(), "IT", "ACTIVO");
        long secondLine = fixture.insert("insert into detalle_donacion(id_donacion,id_recurso,cantidad_donada) values (?,?,?)", fixture.donationId, second, q("10"));
        var complete = receipts.register(fixture.donationId, fixture.email, verification("10", "10", "0", "0", false));
        assertTrue(complete.detalles().getFirst().cerrada());
        assertEquals("REGISTRADA", complete.estado());
        assertTrue(jdbc.queryForObject("select verificacion_cerrada from detalle_donacion where id_detalle_donacion=?", Boolean.class, fixture.lineId));
        var shortRequest = new RegisterReceiptRequest(List.of(new ReceiptLineRequest(second, q("4"), q("4"), q("0"), q("0"), true)), null, "Short synthetic closure", UUID.randomUUID());
        receipts.register(fixture.donationId, fixture.email, shortRequest);
        var progress = receipts.progress(fixture.donationId, fixture.email).stream().filter(p -> p.recursoId().equals(second)).findFirst().orElseThrow();
        assertTrue(progress.cerrada());
        assertEquals(0, progress.diferencia().compareTo(q("-6")));
        var before = jdbc.queryForMap("select * from detalle_donacion where id_detalle_donacion=?", secondLine);
        long auditBefore = audits("RECEPCION_DONACION"), movementBefore = movements("RECEPCION_DONACION");
        var reopen = new RegisterReceiptRequest(List.of(new ReceiptLineRequest(second, q("6"))), null, null, UUID.randomUUID());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> receipts.register(fixture.donationId, fixture.email, reopen)).getStatusCode().value());
        assertEquals(before, jdbc.queryForMap("select * from detalle_donacion where id_detalle_donacion=?", secondLine));
        assertEquals(2, fixture.operationCount());
        assertEquals(auditBefore, audits("RECEPCION_DONACION"));
        assertEquals(movementBefore, movements("RECEPCION_DONACION"));
        assertEquals("REGISTRADA", jdbc.queryForObject("select estado from donacion where id_donacion=?", String.class, fixture.donationId));
    }
    @Test void completingTheOnlyResourceAutomaticallyClosesTheDonation() {
        var response = receipts.register(fixture.donationId, fixture.email, verification("10", "10", "0", "0", false));
        assertEquals("RECIBIDA", response.estado());
        assertTrue(response.detalles().getFirst().cerrada());
        assertEquals("RECIBIDA", jdbc.queryForObject("select estado from donacion where id_donacion=?", String.class, fixture.donationId));
        assertTrue(receipts.progress(fixture.donationId, fixture.email).getFirst().cerrada());
        assertTrue(jdbc.queryForObject("select verificacion_cerrada from detalle_donacion where id_detalle_donacion=?", Boolean.class, fixture.lineId));
    }
    @Test void successiveVerificationsPreserveEveryCounterDeclarationAndHistory() {
        var first = verification("3", "4", "1", "0", false);
        var firstResponse = receipts.register(fixture.donationId, fixture.email, first);
        quantities("3", "4", "1", "0", "0");
        var second = verification("4", "6", "2", "0", false);
        var secondResponse = receipts.register(fixture.donationId, fixture.email, second);
        quantities("7", "10", "3", "0", "0");
        var third = verification("3", "6", "1", "2", false);
        var thirdResponse = receipts.register(fixture.donationId, fixture.email, third);
        quantities("10", "16", "4", "2", "0");
        decide(third, new DecideSurplusRequest(UUID.randomUUID(), true, "Approved synthetic surplus"));
        quantities("12", "16", "4", "0", "2");
        var expected = Map.of(first.operacionId().toString(), firstResponse, second.operacionId().toString(), secondResponse, third.operacionId().toString(), thirdResponse);
        var history = receipts.history(fixture.donationId, 0);
        assertEquals(3, history.size());
        history.forEach(item -> assertSameResponse(expected.get(item.get("operacionId")),
                assertInstanceOf(RegisterReceiptResponse.class, item.get("recepcion"))));
        assertSameResponse(thirdResponse, receipts.register(fixture.donationId, fixture.email, third));
        quantities("12", "16", "4", "0", "2");
        assertEquals(3, fixture.operationCount());
    }
    @Test void historicalV2AndV3ResponsesReplayWithoutRewritingHistory() throws Exception {
        String registrar = jdbc.queryForObject("select u.correo from usuario u join donacion d on d.id_usuario_registro=u.id_usuario where d.id_donacion=?", String.class, fixture.donationId);
        long center = jdbc.queryForObject("select id_centro_acopio from donacion where id_donacion=?", Long.class, fixture.donationId);
        var originals = new HashMap<String, Map<String, Object>>();
        for (boolean v3 : List.of(false, true)) {
            UUID uuid = UUID.randomUUID();
            String canonical = "{\"responsable\":\"" + registrar + "\",\"ubicacion\":null,\"observaciones\":null,"
                    + (v3 ? "\"centroAcopioId\":" + center + "," : "")
                    + "\"detalles\":[[\"" + fixture.resourceId + "\",\"1\"]]}";
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
            String response = "{\"donacionId\":" + fixture.donationId + ",\"estado\":\"REGISTRADA\",\"detalles\":[{\"recursoId\":" + fixture.resourceId
                    + ",\"cantidadRecibidaOperacion\":1,\"cantidadRecibidaAcumulada\":1,\"cantidadPendiente\":9}]}";
            fixture.insert("insert into recepcion_operacion(id_donacion,operacion_id,huella,respuesta) values (?,?,?,?)", fixture.donationId, uuid.toString(), hash, response);
            originals.put(uuid.toString(), jdbc.queryForMap("select huella,respuesta from recepcion_operacion where id_donacion=? and operacion_id=?", fixture.donationId, uuid.toString()));
            var request = new RegisterReceiptRequest(List.of(new ReceiptLineRequest(fixture.resourceId, q("1.000"))), null, null, uuid, v3 ? center : null);
            var replay = receipts.register(fixture.donationId, registrar, request);
            assertEquals(json.readValue(response, RegisterReceiptResponse.class), replay);
            assertNull(replay.responsableId());
            assertNull(replay.detalles().getFirst().cerrada());
            var changed = new RegisterReceiptRequest(List.of(new ReceiptLineRequest(fixture.resourceId, q("2"))), null, null, uuid, v3 ? center : null);
            assertEquals(409, assertThrows(ResponseStatusException.class, () -> receipts.register(fixture.donationId, registrar, changed)).getStatusCode().value());
        }
        assertEquals(2, receipts.history(fixture.donationId, 0).size());
        for (var entry : originals.entrySet()) assertEquals(entry.getValue(), jdbc.queryForMap("select huella,respuesta from recepcion_operacion where id_donacion=? and operacion_id=?", fixture.donationId, entry.getKey()));
        quantities("0", "0", "0", "0", "0");
        assertEquals(0, audits("RECEPCION_DONACION"));
        assertEquals(0, movements("RECEPCION_DONACION"));
    }
}
