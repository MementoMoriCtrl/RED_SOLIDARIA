package pe.redsolidaria.modules.donations;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.redsolidaria.test.SessionWebTestUsers.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.redsolidaria.config.SecurityConfiguration;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.donations.api.*;
import pe.redsolidaria.modules.donations.api.dto.CustodyDtos.*;
import pe.redsolidaria.modules.donations.application.*;
import pe.redsolidaria.modules.donations.domain.*;
import pe.redsolidaria.modules.donations.persistence.*;
import pe.redsolidaria.modules.resources.domain.*;

@WebMvcTest({DonationQueryController.class,DonationAdministrationController.class})
@Import({SecurityConfiguration.class,DonationCustodyPrivacyWebTest.Dependencies.class})
class DonationCustodyPrivacyWebTest {
    static final String PRIVATE_ADDRESS="Jr. Los Laureles 245, Castilla, Piura";
    @Autowired MockMvc mvc;
    @Autowired DonationRepository donations;
    @Autowired DonationMovementRepository movements;
    @Autowired DonationCustodyRepository logistics;
    @BeforeEach void fixture() {
        var donation=mock(Donation.class); var line=mock(DonationLine.class);
        var resource=mock(pe.redsolidaria.modules.resources.domain.Resource.class); var unit=mock(MeasurementUnit.class);
        var donor=mock(Donor.class); var center=mock(CollectionCenter.class);
        var responsible=mock(UserAccount.class); var initial=mock(DonationMovement.class);
        var time=LocalDateTime.of(2026,11,12,10,0);
        when(donations.findByCodigoTrazabilidad("RS-PRIVATE")).thenReturn(Optional.of(donation));
        when(donations.findAdministrativeById(1L)).thenReturn(Optional.of(donation));
        when(donation.getId()).thenReturn(1L); when(donation.getCodigoTrazabilidad()).thenReturn("RS-PRIVATE");
        when(donation.getEstado()).thenReturn("REGISTRADA"); when(donation.getFechaRegistro()).thenReturn(time);
        when(donation.getDonante()).thenReturn(donor); when(donor.getNombre()).thenReturn("DONANTE_SECRETO");
        when(donation.getObservaciones()).thenReturn("NOTA_SECRETA");
        when(donation.getCentroAcopio()).thenReturn(center); when(center.getNombre()).thenReturn("Acopio institucional");
        when(center.getUbicacion()).thenReturn("Dirección institucional pública");
        when(donation.getDetalles()).thenReturn(List.of(line)); when(line.getId()).thenReturn(11L);
        when(line.getRecurso()).thenReturn(resource); when(resource.getNombre()).thenReturn("Arroz");
        when(resource.getUnidadMedida()).thenReturn(unit); when(unit.getCodigo()).thenReturn("KG");
        when(line.getCantidadDonada()).thenReturn(BigDecimal.TEN); when(line.getCantidadRecibidaAcopio()).thenReturn(BigDecimal.TEN);
        when(initial.getTipoEvento()).thenReturn("DONACION_PRESENTADA"); when(initial.getEstadoNuevo()).thenReturn("REGISTRADA");
        when(initial.getUbicacion()).thenReturn(PRIVATE_ADDRESS); when(initial.getObservaciones()).thenReturn("NOTA_SECRETA");
        when(initial.getResponsable()).thenReturn(responsible); when(responsible.getNombre()).thenReturn("PERSONA_SECRETA");
        when(initial.getFechaHora()).thenReturn(time);
        when(movements.findAllByDonacion_IdOrderByFechaHoraAscIdAsc(1L)).thenReturn(List.of(initial));
        var place=new Place("ACOPIO","Acopio institucional","Dirección institucional pública");
        when(logistics.events(1)).thenReturn(List.of(new DonationCustodyRepository.Row(11,
            new Event("RECEPCION_ACOPIO",time.plusHours(1),"RECIBIDA",null,place,null,null,BigDecimal.TEN),
            "INTERNAL_UUID",5L,"PERSONA_SECRETA","NOTA_SECRETA",false)));
    }
    @Test void anonymousResponseIncludesInstitutionalCustodyButNoPrivateDataAnywhere() throws Exception {
        var body=mvc.perform(get("/api/donaciones/RS-PRIVATE")).andExpect(status().isOk())
            .andExpect(jsonPath("$.trazabilidad.recursos[0].ultimaCustodiaConfirmada.nombre").value("Acopio institucional"))
            .andExpect(jsonPath("$.trazabilidad.recursos[0].ultimaCustodiaConfirmada.direccionInstitucional").value("Dirección institucional pública"))
            .andExpect(jsonPath("$.trazabilidad.recursos[0].movimientos[0].cantidadRecibida").value(10))
            .andExpect(jsonPath("$.trazabilidadAdministrativa").doesNotExist()).andReturn().getResponse().getContentAsString();
        for (var secret:List.of(PRIVATE_ADDRESS,"DONANTE_SECRETO","PERSONA_SECRETA","NOTA_SECRETA","INTERNAL_UUID",
            "responsableNombre","responsableId","observaciones","password","correo","telefono")) assertFalse(body.contains(secret),secret);
    }
    @Test void authorizedAdministrativeResponseRetainsExactAddressAndOperationalData() throws Exception {
        mvc.perform(get("/api/donaciones/administracion/1").with(user("admin").authorities(() -> "ACCESO_ADMINISTRAR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trazabilidadAdministrativa.lugarPresentacion").value(PRIVATE_ADDRESS))
            .andExpect(jsonPath("$.trazabilidadAdministrativa.operaciones[0].responsableNombre").value("PERSONA_SECRETA"))
            .andExpect(jsonPath("$.trazabilidadAdministrativa.operaciones[0].observaciones").value("NOTA_SECRETA"));
    }
    @Test void administrativeDetailRequiresExistingAuthority() throws Exception {
        mvc.perform(get("/api/donaciones/administracion/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/donaciones/administracion/1").with(user("operator").authorities(() -> "DONACION_REGISTRAR")))
            .andExpect(status().isForbidden());
    }
    @Test void unknownPublicCodeDoesNotEchoPrivateDataInErrors() throws Exception {
        when(donations.findByCodigoTrazabilidad("missing")).thenReturn(Optional.empty());
        var body=mvc.perform(get("/api/donaciones/missing")).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(PRIVATE_ADDRESS)); assertFalse(body.contains("NOTA_SECRETA"));
    }
    @Test void completePublicJourneyContainsRealPlacesQuantitiesAndFinalZone() throws Exception {
        var time=LocalDateTime.of(2026,11,12,10,0);
        var collection=new Place("ACOPIO","Centro de Acopio Prueba 2","Dirección institucional acopio");
        var distribution=new Place("DISTRIBUCION","Centro de Distribución Piura","Dirección institucional distribución");
        var rows=new java.util.ArrayList<DonationCustodyRepository.Row>();
        rows.add(new DonationCustodyRepository.Row(11,new Event("RECEPCION_ACOPIO",time.plusHours(1),"RECIBIDA",null,collection,null,null,BigDecimal.TEN),"r",5L,"PERSONA_SECRETA","NOTA_SECRETA",false));
        rows.add(new DonationCustodyRepository.Row(11,new Event("SALIDA_TRASLADO",time.plusHours(2),"EN_TRANSITO",collection,distribution,null,BigDecimal.TEN,null),"t",5L,"PERSONA_SECRETA","NOTA_SECRETA",true));
        rows.add(new DonationCustodyRepository.Row(11,new Event("RECEPCION_DISTRIBUCION",time.plusHours(3),"RECIBIDO",collection,distribution,null,BigDecimal.TEN,BigDecimal.TEN),"t",5L,"PERSONA_SECRETA","NOTA_SECRETA",true));
        rows.add(new DonationCustodyRepository.Row(11,new Event("ASIGNACION_RECURSOS",time.plusHours(4),"ASIGNADA",null,null,"A.H. Tácala - Sector B",null,BigDecimal.TEN),"a",5L,"PERSONA_SECRETA","NOTA_SECRETA",true));
        rows.add(new DonationCustodyRepository.Row(11,new Event("ENTREGA_POBLACION",time.plusHours(5),"ENTREGADA",null,null,"A.H. Tácala - Sector B",null,BigDecimal.TEN),"e",5L,"PERSONA_SECRETA","NOTA_SECRETA",true));
        when(logistics.events(1)).thenReturn(rows.reversed());
        var body=mvc.perform(get("/api/donaciones/RS-PRIVATE")).andExpect(status().isOk())
            .andExpect(jsonPath("$.trazabilidad.recepcionAcopioConfirmada").value(true))
            .andExpect(jsonPath("$.trazabilidad.recursos[0].estadoLogistico").value("ENTREGADA"))
            .andExpect(jsonPath("$.trazabilidad.recursos[0].cantidades.entregada").value(10))
            .andExpect(jsonPath("$.trazabilidad.recursos[0].cantidades.enDistribucion").value(0))
            .andExpect(jsonPath("$.trazabilidad.recursos[0].ultimaCustodiaConfirmada.nombre").value("A.H. Tácala - Sector B"))
            .andExpect(jsonPath("$.movimientos[1].destino.nombre").value("Centro de Acopio Prueba 2"))
            .andExpect(jsonPath("$.movimientos[2].origen.nombre").value("Centro de Acopio Prueba 2"))
            .andExpect(jsonPath("$.movimientos[3].destino.nombre").value("Centro de Distribución Piura"))
            .andExpect(jsonPath("$.movimientos[4].zona").value("A.H. Tácala - Sector B"))
            .andExpect(jsonPath("$.movimientos[5].cantidadRecibida").value(10))
            .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(PRIVATE_ADDRESS)); assertFalse(body.contains("PERSONA_SECRETA")); assertFalse(body.contains("NOTA_SECRETA"));
    }
    @Test void publicResponseAlwaysIncludesCustodyEvenWhenNoOperationsExist() throws Exception {
        var donation=donations.findByCodigoTrazabilidad("RS-PRIVATE").orElseThrow();
        when(donation.getDetalles().getFirst().getCantidadRecibidaAcopio()).thenReturn(BigDecimal.ZERO);
        when(logistics.events(1)).thenReturn(List.of());
        mvc.perform(get("/api/donaciones/RS-PRIVATE")).andExpect(status().isOk())
            .andExpect(jsonPath("$.trazabilidad").isMap())
            .andExpect(jsonPath("$.trazabilidad.recepcionAcopioConfirmada").value(false))
            .andExpect(jsonPath("$.trazabilidad.recursos[0].estadoLogistico").value("PENDIENTE_DE_VERIFICACION"))
            .andExpect(jsonPath("$.trazabilidad.recursos[0].posiciones").isEmpty())
            .andExpect(jsonPath("$.trazabilidad.recursos[0].cantidades.entregada").value(0));
    }
    @TestConfiguration static class Dependencies {
        @Bean DonationRepository donations() { return mock(DonationRepository.class); }
        @Bean DonationMovementRepository movements() { return mock(DonationMovementRepository.class); }
        @Bean DonationCustodyRepository logistics() { return mock(DonationCustodyRepository.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean DonationCustodyService custody(DonationCustodyRepository repository) { return new DonationCustodyService(repository); }
        @Bean DonationQueryService queries(DonationRepository donations,DonationMovementRepository movements,DonationCustodyService custody) {
            return new DonationQueryService(donations,movements,custody);
        }
    }
}
