package pe.redsolidaria.modules.donations;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import pe.redsolidaria.config.SecurityConfiguration;
import pe.redsolidaria.modules.access.domain.*;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import pe.redsolidaria.modules.donations.api.DonationReceiptController;
import pe.redsolidaria.modules.donations.application.DonationReceiptService;
import pe.redsolidaria.modules.donations.domain.*;
import pe.redsolidaria.modules.donations.persistence.*;
import pe.redsolidaria.modules.resources.domain.*;

// Real HTTP security/controller/service/serialization; persistence mocked, no MySQL/Flyway.
@WebMvcTest(DonationReceiptController.class)
@Import({SecurityConfiguration.class, DonationReceiptService.class, MasterVerificationWebTest.Dependencies.class})
class MasterVerificationWebTest {
    @Autowired MockMvc mvc;
    @Autowired DonationRepository donations;
    @Autowired UserRepository users;
    @Autowired AuditRecordRepository audit;
    @Autowired ReceiptOperationRepository operations;

    @ParameterizedTest
    @CsvSource({"true,true,true", "true,false,true", "false,true,false", "false,false,true"})
    void progressAndReceiptAgreeForMasterAndNormalAdministrators(boolean master, boolean own, boolean allowed) throws Exception {
        reset(donations,users,audit,operations);
        var role=new Role("ADMINISTRADOR","Administrador",null);
        role.asignar(new Permission("ACCESO_ADMINISTRAR",null));
        var actor=new UserAccount(role,"Actor","Test","actor@example.test","unused","ACTIVO");
        ReflectionTestUtils.setField(actor,"id",3L);
        if (master) ReflectionTestUtils.setField(actor,"adminMaestro",true);
        var registrar=mock(UserAccount.class);
        when(registrar.getId()).thenReturn(own ? 3L : 7L);
        var center=mock(CollectionCenter.class); when(center.getId()).thenReturn(1L);
        var donation=new Donation(mock(Donor.class),null,center,registrar,"RS",null,"REGISTRADA",null);
        ReflectionTestUtils.setField(donation,"id",22L);
        var resource=mock(Resource.class); when(resource.getId()).thenReturn(8L); when(resource.getNombre()).thenReturn("Arroz");
        var unit=mock(MeasurementUnit.class); when(unit.getCodigo()).thenReturn("KG"); when(resource.getUnidadMedida()).thenReturn(unit);
        donation.getDetalles().add(new DonationLine(donation,resource,new BigDecimal("10")));
        when(users.findByCorreo(actor.getCorreo())).thenReturn(Optional.of(actor));
        when(donations.findAdministrativeById(22L)).thenReturn(Optional.of(donation));
        when(donations.findByIdForUpdate(22L)).thenReturn(Optional.of(donation));
        when(operations.findByDonacion_IdAndOperacionId(any(),any())).thenReturn(Optional.empty());
        when(audit.saveAndFlush(any())).thenAnswer(c -> c.getArgument(0));
        var principal=user(actor.getCorreo()).authorities(new SimpleGrantedAuthority("ACCESO_ADMINISTRAR"));
        mvc.perform(get("/api/donaciones/administracion/22/verificacion").with(principal))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].puedeVerificar").value(allowed));
        var result=mvc.perform(post("/api/donaciones/administracion/22/recepciones").with(principal)
            .contentType("application/json").content("""
                {"operacionId":"32749418-a706-498d-b721-460dfffdc094","detalles":[
                  {"recursoId":8,"cantidad":1,"examinada":1,"noAceptada":0,"excedente":0,"cerrar":false}]}
                """));
        if (allowed) result.andExpect(status().isOk()).andExpect(jsonPath("$.responsableId").value(3))
            .andExpect(jsonPath("$.registradorId").value(own ? 3 : 7))
            .andExpect(jsonPath("$.autoverificacionMaestro").value(master && own));
        else result.andExpect(status().isForbidden());
    }
    @TestConfiguration static class Dependencies {
        @Bean DonationRepository donations() { return mock(DonationRepository.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean DonationMovementRepository movements() { return mock(DonationMovementRepository.class); }
        @Bean AuditRecordRepository audit() { return mock(AuditRecordRepository.class); }
        @Bean EntityManager entityManager() { return mock(EntityManager.class); }
        @Bean ReceiptOperationRepository operations() { return mock(ReceiptOperationRepository.class); }
        @Bean tools.jackson.databind.json.JsonMapper json() { return tools.jackson.databind.json.JsonMapper.builder().build(); }
    }
}
