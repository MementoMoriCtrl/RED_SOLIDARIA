package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import pe.redsolidaria.modules.donations.api.dto.DonationDtos.*;
import pe.redsolidaria.modules.donations.application.DonationRegistrationService;
import pe.redsolidaria.test.*;
import tools.jackson.databind.json.JsonMapper;

/** Real service, repositories and MySQL. Run only by explicit selection after separate authorization. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@WithMockUser(authorities="DONACION_REGISTRAR")
class DonationRegistrationControlledMySqlIntegrationTest extends ControlledDonationMySqlSupport {
    @Autowired DonationRegistrationService registrations;

    @Override protected Map<String,Set<String>> requiredExistingRows() { return PhaseOneConfirmedRows.loadAllConfirmed(); }
    @Override protected boolean registrationFixturesOnly() { return true; }

    @Test void registersDonationDetailMovementAndAuditInOneCommittedTransaction() {
        try {
            var request=new CreateDonationRequest(registrationDonorId,null,collectionId,
                "  "+RegistrationScenarioRelations.LOCATION+"  ","  "+RegistrationScenarioRelations.OBSERVATIONS+"  ",
                List.of(new DonationLineRequest(resourceId,RegistrationScenarioRelations.QUANTITY)));
            // Invoke the injected transactional proxy, without an enclosing test transaction.
            var response=registrations.register(request,email);
            var ids=fixtureAudit.requireCommittedRegistration();
            assertEquals(4,ids.size());
            assertEquals(response.id().toString(),ids.get("donacion"));
            assertTrue(response.codigoTrazabilidad().matches("RS-[A-F0-9]{32}"));
            assertEquals("REGISTRADA",response.estado()); assertNull(response.fechaIngreso()); assertNotNull(response.fechaRegistro());
            assertEquals(registrationDonorId,response.donanteId()); assertEquals(collectionId,response.centroAcopioId());
            assertNull(response.emergenciaId()); assertEquals(1,response.detalles().size());
            assertEquals(resourceId,response.detalles().getFirst().recursoId());
            assertEquals(0,RegistrationScenarioRelations.QUANTITY.compareTo(response.detalles().getFirst().cantidad()));

            var donation=row("donacion","id_donacion",ids.get("donacion"));
            equalId(donation,"id_donante",registrationDonorId); equalId(donation,"id_usuario_registro",actorId);
            equalId(donation,"id_centro_acopio",collectionId); assertNull(donation.get("id_emergencia"));
            assertEquals(response.codigoTrazabilidad(),donation.get("codigo_trazabilidad"));
            assertEquals("REGISTRADA",donation.get("estado")); assertNull(donation.get("fecha_ingreso"));
            assertNotNull(donation.get("fecha_registro")); assertEquals(RegistrationScenarioRelations.OBSERVATIONS,donation.get("observaciones"));
            assertEquals(1L,jdbc.queryForObject("select count(*) from donacion where codigo_trazabilidad=?",Long.class,response.codigoTrazabilidad()));

            var detail=row("detalle_donacion","id_detalle_donacion",ids.get("detalle_donacion"));
            equalId(detail,"id_donacion",response.id()); equalId(detail,"id_recurso",resourceId);
            assertEquals(0,new BigDecimal(detail.get("cantidad_donada").toString()).compareTo(RegistrationScenarioRelations.QUANTITY));
            for (String counter:List.of("cantidad_recibida_acopio","cantidad_examinada","cantidad_no_aceptada",
                    "cantidad_excedente_pendiente","cantidad_excedente_aprobada"))
                assertEquals(0,new BigDecimal(detail.get(counter).toString()).signum(),counter);
            assertEquals(Boolean.FALSE,jdbc.queryForObject("select verificacion_cerrada from detalle_donacion where id_detalle_donacion=?",Boolean.class,ids.get("detalle_donacion")));

            var movement=row("movimiento_donacion","id_movimiento",ids.get("movimiento_donacion"));
            equalId(movement,"id_donacion",response.id()); equalId(movement,"id_usuario_responsable",actorId);
            assertEquals("DONACION_PRESENTADA",movement.get("tipo_evento")); assertNull(movement.get("estado_anterior"));
            assertEquals("REGISTRADA",movement.get("estado_nuevo")); assertNotNull(movement.get("fecha_hora"));
            assertEquals(RegistrationScenarioRelations.LOCATION,movement.get("ubicacion"));
            assertEquals(RegistrationScenarioRelations.OBSERVATIONS,movement.get("observaciones"));

            var audit=row("registro_auditoria","id_registro_auditoria",ids.get("registro_auditoria"));
            equalId(audit,"id_usuario",actorId); assertEquals(response.id().toString(),audit.get("id_registro"));
            assertEquals("DONACION",audit.get("entidad")); assertEquals("REGISTRO_DONACION",audit.get("accion"));
            assertNotNull(audit.get("fecha_hora")); assertNull(audit.get("datos_anteriores"));
            var mapper=JsonMapper.builder().build();
            var expected=mapper.readTree(mapper.writeValueAsString(Map.of("codigo",response.codigoTrazabilidad(),"detallesDeclarados",request.detalles())));
            assertEquals(expected,mapper.readTree(audit.get("datos_nuevos").toString()));
            fixtureAudit.requireReadyForScenario();
        } catch (RuntimeException | Error failure) {
            ControlledFixtureAudit.blockFurtherScenarios("registration-scenario",failure); throw failure;
        }
    }
    private Map<String,Object> row(String table,String pk,String id) {
        // Identifiers are fixed literals from this class; the PK is a bound parameter.
        return jdbc.queryForMap("select * from "+table+" where "+pk+"=?",id);
    }
    private static void equalId(Map<String,Object> row,String column,long expected) {
        assertNotNull(row.get(column),column); assertEquals(Long.toString(expected),row.get(column).toString(),column);
    }
}
