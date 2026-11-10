package pe.redsolidaria.modules.donations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.donations.api.dto.DonationReceiptDtos.*;
import pe.redsolidaria.modules.donations.application.DonationReceiptService;
import pe.redsolidaria.modules.donations.domain.*;
import pe.redsolidaria.modules.donations.persistence.*;
import pe.redsolidaria.modules.resources.domain.*;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import tools.jackson.databind.json.JsonMapper;
class DonationVerificationServiceTest {
    DonationRepository donations=mock(DonationRepository.class);
    UserRepository users=mock(UserRepository.class);
    DonationMovementRepository movements=mock(DonationMovementRepository.class);
    AuditRecordRepository audit=mock(AuditRecordRepository.class);
    EntityManager em=mock(EntityManager.class);
    ReceiptOperationRepository operations=mock(ReceiptOperationRepository.class);
    DonationReceiptService service=new DonationReceiptService(donations,users,movements,audit,em,operations,JsonMapper.builder().build());
    UserAccount registrar=mock(UserAccount.class), verifier=mock(UserAccount.class);
    Donation donation; DonationLine line; Map<String,ReceiptOperation> stored=new HashMap<>();
    static BigDecimal q(String v) { return new BigDecimal(v); }
    @BeforeEach void setup() {
        when(registrar.getId()).thenReturn(1L); when(verifier.getId()).thenReturn(2L);
        var center=mock(CollectionCenter.class); when(center.getId()).thenReturn(3L);
        donation=new Donation(mock(Donor.class),null,center,registrar,"RS",null,"REGISTRADA",null);
        ReflectionTestUtils.setField(donation,"id",22L);
        var resource=mock(Resource.class); when(resource.getId()).thenReturn(8L); when(resource.getNombre()).thenReturn("Arroz");
        var unit=mock(MeasurementUnit.class); when(unit.getCodigo()).thenReturn("KG"); when(resource.getUnidadMedida()).thenReturn(unit);
        line=new DonationLine(donation,resource,q("10.000")); donation.getDetalles().add(line);
        when(donations.findByIdForUpdate(22L)).thenReturn(Optional.of(donation));
        when(donations.findAdministrativeById(22L)).thenReturn(Optional.of(donation));
        when(users.findByCorreo("verifier")).thenReturn(Optional.of(verifier)); when(users.findByCorreo("registrar")).thenReturn(Optional.of(registrar));
        when(operations.findByDonacion_IdAndOperacionId(any(),any())).thenAnswer(c -> Optional.ofNullable(stored.get(c.getArgument(1))));
        when(operations.saveAndFlush(any())).thenAnswer(c -> { ReceiptOperation op=c.getArgument(0); stored.put(op.getOperacionId(),op); return op; });
        when(audit.saveAndFlush(any())).thenAnswer(c -> c.getArgument(0));
    }
    RegisterReceiptRequest request(String examined,String accepted,String rejected,String surplus,boolean close) {
        return new RegisterReceiptRequest(List.of(new ReceiptLineRequest(8L,q(accepted),q(examined),q(rejected),q(surplus),close)),null,"Verificación física",UUID.randomUUID());
    }
    @Test void registrantCannotVerifyButSameIndependentVerifierCanReceivePartials() {
        var first=request("4","4","0","0",false);
        assertEquals(403,assertThrows(ResponseStatusException.class,() -> service.register(22L,"registrar",first)).getStatusCode().value());
        service.register(22L,"verifier",first); service.register(22L,"verifier",request("6","6","0","0",true));
        assertEquals(0,line.getCantidadRecibidaAcopio().compareTo(q("10"))); assertEquals(q("10.000"),line.getCantidadDonada());
        assertEquals(2,stored.size()); assertTrue(line.isCerrada());
    }
    @Test void masterCanVerifyOwnDonationWithRealIdsAndDurableReplay() {
        when(registrar.esAdministradorMaestro()).thenReturn(true);
        assertTrue(service.progress(22L,"registrar").getFirst().puedeVerificar());
        var request=request("4","4","0","0",false);
        var response=service.register(22L,"registrar",request);
        assertEquals(1L,response.registradorId()); assertEquals(1L,response.responsableId());
        assertTrue(response.autoverificacionMaestro());
        assertEquals(response,service.register(22L,"registrar",request));
        assertEquals(0,line.getCantidadRecibidaAcopio().compareTo(q("4")));
        var captured=org.mockito.ArgumentCaptor.forClass(pe.redsolidaria.modules.audit.domain.AuditRecord.class);
        verify(audit).saveAndFlush(captured.capture());
        assertSame(registrar,captured.getValue().getUsuario());
        var data=JsonMapper.builder().build().readTree(captured.getValue().getDatosNuevos());
        assertEquals(1L,data.get("registradorId").asLong()); assertEquals(1L,data.get("verificadorId").asLong());
        assertTrue(data.get("autoverificacionMaestro").asBoolean());
        assertEquals("Verificaci\u00f3n f\u00edsica",data.get("observaciones").asText());
        assertEquals(4,data.get("recursos").get(0).get("examinada").asInt());
        verify(movements,times(1)).saveAndFlush(any());
    }
    @Test void masterCanVerifyOtherUsersDonationWithoutClaimingSelfVerification() {
        when(verifier.esAdministradorMaestro()).thenReturn(true);
        assertTrue(service.progress(22L,"verifier").getFirst().puedeVerificar());
        var response=service.register(22L,"verifier",request("1","1","0","0",false));
        assertEquals(1L,response.registradorId()); assertEquals(2L,response.responsableId());
        assertFalse(response.autoverificacionMaestro());
    }
    @Test void normalAdministratorProgressPreservesIndependence() {
        assertFalse(service.progress(22L,"registrar").getFirst().puedeVerificar());
        assertTrue(service.progress(22L,"verifier").getFirst().puedeVerificar());
    }
    @Test void masterCannotBypassAmountsClosureOrSurplusApproval() {
        when(registrar.esAdministradorMaestro()).thenReturn(true);
        assertEquals(400,assertThrows(ResponseStatusException.class,() -> service.register(22L,"registrar",request("12","12","0","0",false))).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,() -> service.register(22L,"registrar",request("10","1","0","0",false))).getStatusCode().value());
        var r=request("12","10","0","2",true);
        service.register(22L,"registrar",r);
        assertEquals(0,line.getCantidadRecibidaAcopio().compareTo(q("10")));
        assertEquals(0,line.getExcedentePendiente().compareTo(q("2")));
        assertEquals(409,assertThrows(ResponseStatusException.class,() -> service.register(22L,"registrar",request("1","1","0","0",false))).getStatusCode().value());
        var decision=new DecideSurplusRequest(UUID.randomUUID(),true,"Apto");
        service.decide(22L,r.operacionId().toString(),"registrar",decision);
        service.decide(22L,r.operacionId().toString(),"registrar",decision);
        assertEquals(0,line.getCantidadRecibidaAcopio().compareTo(q("12")));
        assertEquals(q("10.000"),line.getCantidadDonada());
    }
    @Test void closesWithZeroAcceptedWithoutInventoryOrIngressDate() {
        service.register(22L,"verifier",request("10","0","10","0",true));
        assertEquals(0,line.getCantidadRecibidaAcopio().signum()); assertNull(donation.getFechaIngreso());
        assertTrue(line.isCerrada()); assertEquals(0,line.getNoAceptada().compareTo(q("10")));
        assertEquals("CERRADA",service.progress(22L,"verifier").getFirst().avance());
    }
    @Test void rejectsUnexplainedDifferenceAndInvalidConservationBeforeWriting() {
        var r=request("10","0","10","0",true);
        assertThrows(ResponseStatusException.class,() -> service.register(22L,"verifier",new RegisterReceiptRequest(r.detalles(),null,null,r.operacionId())));
        assertThrows(ResponseStatusException.class,() -> service.register(22L,"verifier",request("10","4","1","0",false)));
        assertEquals(0,line.getCantidadRecibidaAcopio().signum()); verifyNoInteractions(audit,movements);
    }
    @Test void pendingSurplusDoesNotBecomeStockUntilDurableApprovalAndCannotReplayChangedDecision() {
        var request=request("12","10","0","2",true); service.register(22L,"verifier",request);
        assertEquals(0,line.getCantidadRecibidaAcopio().compareTo(q("10"))); assertEquals(0,line.getExcedentePendiente().compareTo(q("2")));
        var decision=new DecideSurplusRequest(UUID.randomUUID(),true,"Excedente apto autorizado");
        service.decide(22L,request.operacionId().toString(),"verifier",decision);
        service.decide(22L,request.operacionId().toString(),"verifier",decision);
        assertEquals(0,line.getCantidadRecibidaAcopio().compareTo(q("12"))); assertEquals(0,line.getExcedentePendiente().signum());
        assertEquals(q("10.000"),line.getCantidadDonada());
        assertThrows(ResponseStatusException.class,() -> service.decide(22L,request.operacionId().toString(),"verifier",new DecideSurplusRequest(decision.operacionId(),false,"Cambio")));
        // A receipt retry returns its original response and never restores a resolved surplus.
        service.register(22L,"verifier",request); assertEquals(0,line.getCantidadRecibidaAcopio().compareTo(q("12")));
    }
    @Test void rejectedSurplusNeverIncreasesStockAndOriginalHistoryRemainsIntact() {
        var request=request("12","10","0","2",true); service.register(22L,"verifier",request);
        var op=stored.get(request.operacionId().toString()); String original=op.getRespuesta();
        service.decide(22L,request.operacionId().toString(),"verifier",new DecideSurplusRequest(UUID.randomUUID(),false,"No autorizado"));
        assertEquals(0,line.getCantidadRecibidaAcopio().compareTo(q("10"))); assertEquals("RECHAZADO",op.getDecision()); assertEquals(original,op.getRespuesta());
    }
    @Test void closedShortReceiptCannotBeReopenedByANewUuid() {
        service.register(22L,"verifier",request("4","4","0","0",true));
        assertThrows(ResponseStatusException.class,() -> service.register(22L,"verifier",request("6","6","0","0",false)));
        assertEquals(0,line.getCantidadRecibidaAcopio().compareTo(q("4")));
    }
    @Test void replaysAnOriginalV2FingerprintBeforeApplyingTheNewVerifierRule() throws Exception {
        String uuid=UUID.randomUUID().toString();
        String canonical="{\"responsable\":\"registrar\",\"ubicacion\":null,\"observaciones\":null,\"detalles\":[[\"8\",\"1\"]]}";
        String fingerprint=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String response="{\"donacionId\":22,\"estado\":\"REGISTRADA\",\"detalles\":[{\"recursoId\":8,\"cantidadRecibidaOperacion\":1,\"cantidadRecibidaAcumulada\":1,\"cantidadPendiente\":9}]}";
        stored.put(uuid,new ReceiptOperation(donation,uuid,fingerprint,response));
        var result=service.register(22L,"registrar",new RegisterReceiptRequest(List.of(new ReceiptLineRequest(8L,BigDecimal.ONE)),null,null,UUID.fromString(uuid)));
        assertEquals(0,result.detalles().getFirst().cantidadRecibidaAcumulada().compareTo(BigDecimal.ONE));
        assertEquals(0,line.getCantidadRecibidaAcopio().signum()); verifyNoInteractions(audit,movements);
    }
    @Test void legacyStoredResponseCanStillBeReadWithoutNewFields() {
        var json=JsonMapper.builder().build();
        var response=json.readValue("{\"donacionId\":22,\"estado\":\"RECIBIDA\",\"detalles\":[{\"recursoId\":8,\"cantidadRecibidaOperacion\":1,\"cantidadRecibidaAcumulada\":1,\"cantidadPendiente\":0}]}",RegisterReceiptResponse.class);
        assertNull(response.detalles().getFirst().cerrada()); assertNull(response.responsableId());
    }
}
