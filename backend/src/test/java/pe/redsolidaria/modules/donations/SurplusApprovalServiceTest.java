package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static pe.redsolidaria.modules.donations.SurplusApprovalFixture.q;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.domain.*;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.donations.api.dto.DonationReceiptDtos.DecideSurplusRequest;

class SurplusApprovalServiceTest {
    @ParameterizedTest
    @CsvSource({"true,true,true", "true,false,true", "false,true,false", "false,false,true"})
    void enforcesAccountExceptionAndRecordsRealDecision(boolean master,boolean own,boolean allowed) {
        var f=new SurplusApprovalFixture(); f.prepare(master,own);
        assertSame(f.actor.getRol(),f.donation.getUsuarioRegistro().getRol());
        var request=f.approval(); var original=f.operation.getRespuesta();
        if (!allowed) {
            assertEquals(403,assertThrows(ResponseStatusException.class,() ->
                f.service.decide(22L,f.receiptUuid,f.actor.getCorreo(),request)).getStatusCode().value());
            assertNull(f.operation.getDecision()); assertEquals(q("10.000"),f.line.getCantidadRecibidaAcopio());
            assertEquals(q("2.000"),f.line.getExcedentePendiente()); verifyNoInteractions(f.audit,f.movements);
        } else {
            var response=f.service.decide(22L,f.receiptUuid,f.actor.getCorreo(),request);
            assertEquals(q("12.000"),f.line.getCantidadRecibidaAcopio());
            assertEquals(q("2.000"),f.line.getExcedenteAprobado());
            assertEquals(0,f.line.getExcedentePendiente().signum());
            assertEquals(17L,f.operation.getDecisorId()); assertEquals(17L,response.responsableId());
            assertNotNull(f.operation.getFechaDecision());
            assertEquals("Excedente apto comprobado",f.operation.getObservacionesDecision());
            assertEquals(q("2.000"),response.detalles().getFirst().cantidadRecibidaOperacion());
            var captured=ArgumentCaptor.forClass(AuditRecord.class); verify(f.audit).saveAndFlush(captured.capture());
            assertSame(f.actor,captured.getValue().getUsuario());
            assertEquals("AUTORIZACION_EXCEDENTE",captured.getValue().getAccion());
            var payload=f.json.readTree(captured.getValue().getDatosNuevos());
            assertEquals(own ? 17L : 29L,payload.get("registradorId").asLong());
            assertEquals(17L,payload.get("decisorId").asLong());
            assertEquals(master && own,payload.get("aprobacionPropiaExcepcionalMaestro").asBoolean());
            assertEquals(f.operation.getFechaDecision(),f.json.readValue(payload.get("fechaDecision").toString(),java.time.LocalDateTime.class));
            assertEquals(2,payload.get("resultado").get("detalles").get(0).get("cantidadRecibidaOperacion").asInt());
            verify(f.movements).saveAndFlush(argThat(m -> m.getResponsable()==f.actor));
        }
        assertEquals(original,f.operation.getRespuesta());
        assertEquals(q("10.000"),f.line.getCantidadDonada());
        assertEquals(q("13.000"),f.line.getExaminada()); assertEquals(q("1.000"),f.line.getNoAceptada());
    }
    @Test void replayDoesNotAuthorizeOrAuditTwiceAndChangedUuidConflicts() {
        var f=new SurplusApprovalFixture(); f.prepare(true,true); var request=f.approval();
        var response=f.service.decide(22L,f.receiptUuid,f.actor.getCorreo(),request);
        assertEquals(response,f.service.decide(22L,f.receiptUuid,f.actor.getCorreo(),request));
        assertEquals(409,assertThrows(ResponseStatusException.class,() -> f.service.decide(22L,f.receiptUuid,f.actor.getCorreo(),
            new DecideSurplusRequest(UUID.randomUUID(),true,request.observaciones()))).getStatusCode().value());
        assertEquals(q("12.000"),f.line.getCantidadRecibidaAcopio());
        verify(f.audit,times(1)).saveAndFlush(any()); verify(f.movements,times(1)).saveAndFlush(any());
    }
    @ParameterizedTest @CsvSource({"INACTIVO,true", "ACTIVO,false"})
    void flagAloneCannotGrantTheSelfApprovalException(String state,boolean access) {
        var f=new SurplusApprovalFixture(); f.prepare(true,true); f.actor.cambiarEstado(state);
        if (!access) f.actor.cambiarRol(new Role("ADMINISTRADOR","Sin acceso",null));
        assertEquals(403,assertThrows(ResponseStatusException.class,() ->
            f.service.decide(22L,f.receiptUuid,f.actor.getCorreo(),f.approval())).getStatusCode().value());
        assertNull(f.operation.getDecision()); verifyNoInteractions(f.audit,f.movements);
    }
    @Test void aReservedRolePermissionCannotReplaceTheExclusiveAccountFlag() {
        var f=new SurplusApprovalFixture(); f.prepare(false,true);
        f.actor.getRol().asignar(new Permission("ADMIN_MAESTRO",null));
        assertEquals(403,assertThrows(ResponseStatusException.class,() ->
            f.service.decide(22L,f.receiptUuid,f.actor.getCorreo(),f.approval())).getStatusCode().value());
        verifyNoInteractions(f.audit,f.movements);
    }
    @Test void masterCannotBypassDecimalLimitsOrRequiredJustification() {
        var f=new SurplusApprovalFixture(); f.prepare(true,true);
        assertEquals(400,assertThrows(ResponseStatusException.class,() -> f.service.decide(22L,f.receiptUuid,f.actor.getCorreo(),
            new DecideSurplusRequest(UUID.randomUUID(),true," "))).getStatusCode().value());
        ReflectionTestUtils.setField(f.line,"cantidadRecibidaAcopio",q("99999999999.999"));
        assertEquals(400,assertThrows(ResponseStatusException.class,() ->
            f.service.decide(22L,f.receiptUuid,f.actor.getCorreo(),f.approval())).getStatusCode().value());
        assertNull(f.operation.getDecision()); verifyNoInteractions(f.audit,f.movements);
    }
    @Test void ordinaryRegistrantsExistingRejectionFlowRemainsAvailable() {
        var f=new SurplusApprovalFixture(); f.prepare(false,true);
        f.service.decide(22L,f.receiptUuid,f.actor.getCorreo(),new DecideSurplusRequest(UUID.randomUUID(),false,"No apto"));
        assertEquals("RECHAZADO",f.operation.getDecision());
        assertEquals(q("10.000"),f.line.getCantidadRecibidaAcopio());
        assertEquals(0,f.line.getExcedenteAprobado().signum());
        var captured=ArgumentCaptor.forClass(AuditRecord.class); verify(f.audit).saveAndFlush(captured.capture());
        assertFalse(f.json.readTree(captured.getValue().getDatosNuevos()).get("aprobacionPropiaExcepcionalMaestro").asBoolean());
    }
}
