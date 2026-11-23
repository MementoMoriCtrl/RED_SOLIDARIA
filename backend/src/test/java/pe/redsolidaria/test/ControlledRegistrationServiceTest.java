package pe.redsolidaria.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.donations.api.dto.DonationDtos.*;
import pe.redsolidaria.modules.donations.application.*;
import pe.redsolidaria.modules.donations.domain.*;
import pe.redsolidaria.modules.donations.persistence.*;
import pe.redsolidaria.modules.emergencies.persistence.EmergencyRepository;
import pe.redsolidaria.modules.resources.domain.*;
import pe.redsolidaria.modules.resources.persistence.*;
import pe.redsolidaria.test.FixtureLedger.State;
import tools.jackson.databind.json.JsonMapper;

/** Real register() through Spring transactional advice; mocked repositories/EntityManager and synthetic events. */
class ControlledRegistrationServiceTest {
    static final class ServiceHarness implements AutoCloseable {
        final ControlledRegistrationProvenanceTest.Harness storage;
        final EntityManager em=mock(EntityManager.class);
        final DonationRegistrationService service;
        final CreateDonationRequest request=new CreateDonationRequest(900104L,null,900109L,
            "  "+RegistrationScenarioRelations.LOCATION+"  ","  "+RegistrationScenarioRelations.OBSERVATIONS+"  ",
            List.of(new DonationLineRequest(900106L,RegistrationScenarioRelations.QUANTITY)));
        ServiceHarness() { this(new ControlledRegistrationProvenanceTest.Harness()); }
        ServiceHarness(ControlledRegistrationProvenanceTest.Harness storage) {
            this.storage=storage;
            var donations=mock(DonationRepository.class); var lines=mock(DonationLineRepository.class);
            var movements=mock(DonationMovementRepository.class); var donors=mock(DonorRepository.class);
            var resources=mock(ResourceRepository.class); var centers=mock(CollectionCenterRepository.class);
            var users=mock(UserRepository.class); var codes=mock(TrackingCodeGenerator.class);
            var donor=mock(Donor.class); when(donor.getId()).thenReturn(900104L);
            var user=mock(UserAccount.class); when(user.getId()).thenReturn(900102L);
            var center=mock(CollectionCenter.class); when(center.getId()).thenReturn(900109L);
            var resource=mock(Resource.class); when(resource.getId()).thenReturn(900106L); when(resource.getEstado()).thenReturn("ACTIVO");
            when(donors.findById(900104L)).thenReturn(Optional.of(donor));
            when(users.findByCorreo("fixture@integration.test")).thenReturn(Optional.of(user));
            when(centers.findById(900109L)).thenReturn(Optional.of(center));
            when(resources.findByIdForUpdate(900106L)).thenReturn(java.util.Optional.of(resource)); when(codes.nextCode()).thenReturn("RS-SYNTHETIC");
            when(donations.saveAndFlush(any(Donation.class))).thenAnswer(call -> {
                Donation d=call.getArgument(0); ReflectionTestUtils.setField(d,"id",900201L);
                var values=storage.fields("donacion"); values.put("id_usuario_registro",d.getUsuarioRegistro().getId().toString());
                values.put("id_donante",d.getDonante().getId().toString()); values.put("id_centro_acopio",d.getCentroAcopio().getId().toString());
                values.put("estado",d.getEstado()); values.put("observaciones",d.getObservaciones()); values.put("codigo_trazabilidad",d.getCodigoTrazabilidad());
                storage.store("donacion",900201,values,true); return d;
            });
            when(lines.saveAllAndFlush(any())).thenAnswer(call -> {
                Iterable<DonationLine> details=call.getArgument(0); var saved=new ArrayList<DonationLine>();
                for (DonationLine line:details) {
                    ReflectionTestUtils.setField(line,"id",900202L); var values=storage.fields("detalle_donacion");
                    values.put("id_donacion",line.getDonacion().getId().toString()); values.put("id_recurso",line.getRecurso().getId().toString());
                    values.put("cantidad_donada",line.getCantidadDonada().toPlainString()); storage.store("detalle_donacion",900202,values,true); saved.add(line);
                }
                return saved;
            });
            when(movements.saveAndFlush(any(DonationMovement.class))).thenAnswer(call -> {
                DonationMovement movement=call.getArgument(0); var values=storage.fields("movimiento_donacion");
                values.put("id_donacion",movement.getDonacion().getId().toString()); values.put("id_usuario_responsable",movement.getResponsable().getId().toString());
                values.put("tipo_evento",movement.getTipoEvento()); values.put("ubicacion",movement.getUbicacion()); values.put("observaciones",movement.getObservaciones());
                storage.store("movimiento_donacion",900203,values,true); return movement;
            });
            doAnswer(call -> {
                AuditRecord record=call.getArgument(0); var values=storage.fields("registro_auditoria");
                values.put("id_usuario",record.getUsuario().getId().toString()); values.put("id_registro",record.getIdRegistro());
                values.put("entidad",record.getEntidad()); values.put("accion",record.getAccion()); values.put("datos_nuevos",record.getDatosNuevos());
                storage.store("registro_auditoria",900204,values,true);
                assertTrue(storage.ledger().keys(State.COMMITTED_OPERATION).isEmpty()); return null;
            }).when(em).persist(any(AuditRecord.class));
            doAnswer(call -> { Donation donation=call.getArgument(0); ReflectionTestUtils.setField(donation,"fechaRegistro",LocalDateTime.of(2026,11,13,12,0)); return null; })
                .when(em).refresh(any(Donation.class));
            var target=new DonationRegistrationService(donations,lines,movements,donors,resources,mock(EmergencyRepository.class),centers,users,codes,em);
            var proxy=new ProxyFactory(target); proxy.addAdvice(new TransactionInterceptor(storage.manager,new AnnotationTransactionAttributeSource()));
            service=(DonationRegistrationService)proxy.getProxy();
        }
        @Override public void close() { storage.close(); }
    }
    @Test void realServiceCreatesTheFourObservedRowsIncludingPersistedAuditWithStructuralJson() {
        try (var h=new ServiceHarness()) {
            var response=h.service.register(h.request," Fixture@integration.test ");
            assertEquals(900201L,response.id()); assertEquals(4,h.storage.audit.requireCommittedRegistration().size());
            var record=org.mockito.ArgumentCaptor.forClass(AuditRecord.class); verify(h.em).persist(record.capture());
            var mapper=JsonMapper.builder().build();
            assertEquals(mapper.readTree(mapper.writeValueAsString(Map.of("codigo",response.codigoTrazabilidad(),"detallesDeclarados",h.request.detalles()))),
                mapper.readTree(record.getValue().getDatosNuevos()));
            assertEquals("REGISTRO_DONACION",record.getValue().getAccion()); assertNull(record.getValue().getDatosAnteriores());
            h.storage.audit.finish();
        }
    }
    @Test void actualServiceExceptionAtAuditPersistRollsBackAllEarlierObservedRowsAndCanLatchTheScenarioGate() {
        try (var h=new ServiceHarness()) {
            doThrow(new IllegalStateException("Synthetic audit persistence failure")).when(h.em).persist(any(AuditRecord.class));
            assertThrows(IllegalStateException.class,() -> h.storage.gate.initialize("registration-service",() -> h.service.register(h.request,"fixture@integration.test")));
            assertEquals(3,h.storage.ledger().keys(State.ROLLED_BACK).size());
            assertTrue(h.storage.ledger().keys(State.COMMITTED_OPERATION).isEmpty()); h.storage.blocked();
        }
    }
}
