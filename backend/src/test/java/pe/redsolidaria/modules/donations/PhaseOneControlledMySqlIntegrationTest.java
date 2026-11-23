package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import pe.redsolidaria.modules.donations.api.dto.DonationReceiptDtos.*;
import pe.redsolidaria.modules.donations.application.DonationReceiptService;
import pe.redsolidaria.modules.deliveries.api.dto.DeliveryDtos.SaveDeliveryRequest;
import pe.redsolidaria.modules.deliveries.application.DeliveryManagementService;
import pe.redsolidaria.modules.emergencies.persistence.EmergencyBalanceRepository;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.sql.init.mode=never"})
class PhaseOneControlledMySqlIntegrationTest extends ControlledDonationMySqlSupport {
    @Autowired DonationReceiptService receipts;
    @Autowired DeliveryManagementService deliveries;
    @Autowired EmergencyBalanceRepository balances;
    @Autowired pe.redsolidaria.modules.logistics.LogisticsService logistics;
    @MockitoSpyBean AuditRecordRepository audit;
    @Autowired pe.redsolidaria.modules.logistics.LogisticsRepository transferIds;

    @Override protected void observeJdbcRepositories() { fixtureAudit.observeTransferIds(transferIds); }
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
        var dispatch = new pe.redsolidaria.modules.logistics.LogisticsDtos.Dispatch(UUID.randomUUID(),lineId,distributionId,new BigDecimal("10"),null);
        logistics.dispatch(dispatch,email);
        assertThrows(ResponseStatusException.class, () -> deliveries.save(need1,lineId,email,new SaveDeliveryRequest(BigDecimal.ONE,BigDecimal.ZERO)));
        logistics.receive(dispatch.operacionId().toString(),new pe.redsolidaria.modules.logistics.LogisticsDtos.Receive(UUID.randomUUID(),new BigDecimal("10"),null),email);
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
    @Test void logisticsRetriesAndPartialDeliveryForA100KgNeed() throws Exception {
        // Keep the existing fixture-only update, but make its outcome observable by the safety ledger.
        new TransactionTemplate(tx).executeWithoutResult(status ->
            jdbc.update("update necesidad set cantidad_requerida=100 where id_necesidad=?",need1));
        receipts.register(donationId,email,receipt("10"));
        var sent=new pe.redsolidaria.modules.logistics.LogisticsDtos.Dispatch(UUID.randomUUID(),lineId,distributionId,new BigDecimal("10"),"Lote de arroz");
        parallel(() -> logistics.dispatch(sent,email),() -> logistics.dispatch(sent,email));
        assertEquals(1L,jdbc.queryForObject("select count(*) from traslado_recurso where id_detalle_donacion=?",Long.class,lineId));
        assertEquals(0,balances.balance(emergencyId).getFirst().disponible().signum());
        var received=new pe.redsolidaria.modules.logistics.LogisticsDtos.Receive(UUID.randomUUID(),new BigDecimal("9"),"Diferencia de 1 kg");
        parallel(() -> logistics.receive(sent.operacionId().toString(),received,email),() -> logistics.receive(sent.operacionId().toString(),received,email));
        assertEquals("RECIBIDO_CON_DIFERENCIA",logistics.receive(sent.operacionId().toString(),received,email).estado());
        deliveries.save(need1,lineId,email,new SaveDeliveryRequest(new BigDecimal("8"),new BigDecimal("3")));
        var balance=balances.balance(emergencyId).getFirst();
        assertEquals(0,balance.disponible().compareTo(BigDecimal.ONE));
        assertEquals(0,balance.trasladada().compareTo(new BigDecimal("10")));
        assertEquals(0,balance.recibidaDistribucion().compareTo(new BigDecimal("9")));
        assertEquals(0,balance.diferencia().compareTo(BigDecimal.ONE));
        assertEquals(0,balance.entregada().compareTo(new BigDecimal("3")));
        assertEquals(0,jdbc.queryForObject("select count(*) from traslado_recurso where id_detalle_donacion=? and estado='EN_TRANSITO'",Integer.class,lineId));
    }
    @Test void auditFailureRollsBackDispatchAndItsDurableIdentity() {
        receipts.register(donationId,email,receipt("10"));
        doThrow(new IllegalStateException("Injected audit failure")).when(audit).saveAndFlush(any(AuditRecord.class));
        var sent=new pe.redsolidaria.modules.logistics.LogisticsDtos.Dispatch(UUID.randomUUID(),lineId,distributionId,new BigDecimal("10"),null);
        assertThrows(IllegalStateException.class,() -> logistics.dispatch(sent,email));
        assertEquals(0L,jdbc.queryForObject("select count(*) from traslado_recurso where id_detalle_donacion=?",Long.class,lineId));
    }
    @Test void auditFailureRollsBackDistributionReceipt() {
        receipts.register(donationId,email,receipt("10"));
        var sent=new pe.redsolidaria.modules.logistics.LogisticsDtos.Dispatch(UUID.randomUUID(),lineId,distributionId,new BigDecimal("10"),null);
        logistics.dispatch(sent,email);
        doThrow(new IllegalStateException("Injected audit failure")).when(audit).saveAndFlush(any(AuditRecord.class));
        var received=new pe.redsolidaria.modules.logistics.LogisticsDtos.Receive(UUID.randomUUID(),new BigDecimal("10"),null);
        assertThrows(IllegalStateException.class,() -> logistics.receive(sent.operacionId().toString(),received,email));
        assertEquals("EN_TRANSITO",jdbc.queryForObject("select estado from traslado_recurso where operacion_id=?",String.class,sent.operacionId().toString()));
        assertEquals(0,balances.balance(emergencyId).getFirst().disponible().signum());
    }
    @Test void surplusAuthorizationIsDurable() {
        var request=new RegisterReceiptRequest(List.of(new ReceiptLineRequest(resourceId,new BigDecimal("10"),new BigDecimal("12"),BigDecimal.ZERO,new BigDecimal("2"),true)),null,"Excedente apto",UUID.randomUUID());
        receipts.register(donationId,email,request);
        assertEquals(0,received().compareTo(new BigDecimal("10")));
        var decision=new DecideSurplusRequest(UUID.randomUUID(),true,"Autorizado");
        receipts.decide(donationId,request.operacionId().toString(),email,decision);
        receipts.decide(donationId,request.operacionId().toString(),email,decision);
        assertEquals(0,received().compareTo(new BigDecimal("12")));
        assertEquals(0,jdbc.queryForObject("select cantidad_excedente_pendiente from detalle_donacion where id_detalle_donacion=?",BigDecimal.class,lineId).signum());
    }
    @Test void zeroAcceptedClosurePersistsWithoutStock() {
        var request=new RegisterReceiptRequest(List.of(new ReceiptLineRequest(resourceId,BigDecimal.ZERO,new BigDecimal("10"),new BigDecimal("10"),BigDecimal.ZERO,true)),null,"Material no apto",UUID.randomUUID());
        receipts.register(donationId,email,request);
        assertEquals(0,received().signum());
        assertTrue(jdbc.queryForObject("select verificacion_cerrada from detalle_donacion where id_detalle_donacion=?",Boolean.class,lineId));
        assertNull(jdbc.queryForObject("select fecha_ingreso from donacion where id_donacion=?",java.sql.Timestamp.class,donationId));
    }
    @Test void approvalAuditFailureRollsBackInventoryAndDecision() {
        var request=new RegisterReceiptRequest(List.of(new ReceiptLineRequest(resourceId,new BigDecimal("10"),new BigDecimal("12"),BigDecimal.ZERO,new BigDecimal("2"),true)),null,"Excedente apto",UUID.randomUUID());
        receipts.register(donationId,email,request);
        doThrow(new IllegalStateException("Injected failure")).when(audit).saveAndFlush(any(AuditRecord.class));
        assertThrows(IllegalStateException.class,() -> receipts.decide(donationId,request.operacionId().toString(),email,new DecideSurplusRequest(UUID.randomUUID(),true,"Autorizado")));
        assertEquals(0,received().compareTo(new BigDecimal("10")));
        assertNull(jdbc.queryForObject("select decision from recepcion_operacion where id_donacion=?",String.class,donationId));
    }
    String allocate(long need) {
        try { deliveries.save(need, lineId, email, new SaveDeliveryRequest(new BigDecimal("8"), BigDecimal.ZERO)); return "OK"; }
        catch (ResponseStatusException failure) { return String.valueOf(failure.getStatusCode().value()); }
    }
}
