package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static pe.redsolidaria.test.SessionWebTestUsers.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static pe.redsolidaria.modules.donations.SurplusApprovalFixture.q;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.redsolidaria.config.SecurityConfiguration;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import pe.redsolidaria.modules.donations.api.DonationReceiptController;
import pe.redsolidaria.modules.donations.application.DonationReceiptService;
import pe.redsolidaria.modules.donations.persistence.*;
import tools.jackson.databind.json.JsonMapper;

/** Integration of HTTP, authorization, controller, real service and serialization; persistence is mocked. */
@WebMvcTest(DonationReceiptController.class)
@Import({SecurityConfiguration.class,DonationReceiptService.class,SurplusApprovalWebTest.Dependencies.class})
class SurplusApprovalWebTest {
    @Autowired MockMvc mvc;
    @Autowired DonationRepository donations;
    @Autowired UserRepository users;
    @Autowired DonationMovementRepository movements;
    @Autowired AuditRecordRepository audit;
    @Autowired EntityManager em;
    @Autowired ReceiptOperationRepository operations;
    @Autowired JsonMapper json;
    SurplusApprovalFixture f;
    @BeforeEach void fixture() {
        reset(donations,users,movements,audit,em,operations);
        f=new SurplusApprovalFixture(donations,users,movements,audit,em,operations,json);
    }
    String endpoint() { return "/api/donaciones/administracion/22/recepciones/"+f.receiptUuid+"/excedente"; }
    @ParameterizedTest
    @CsvSource({"true,true,true", "true,false,true", "false,true,false", "false,false,true"})
    void enforcesAllFourScenariosThroughTheActualEndpoint(boolean master,boolean own,boolean allowed) throws Exception {
        f.prepare(master,own);
        var result=mvc.perform(post(endpoint()).with(user(f.actor.getCorreo()).authorities(() -> "ACCESO_ADMINISTRAR"))
            .contentType("application/json").content(json.writeValueAsString(f.approval())));
        if (!allowed) {
            result.andExpect(status().isForbidden());
            assertNull(f.operation.getDecision()); assertEquals(q("10.000"),f.line.getCantidadRecibidaAcopio());
            verifyNoInteractions(audit,movements);
        } else {
            result.andExpect(status().isOk()).andExpect(jsonPath("$.responsableId").value(17))
                .andExpect(jsonPath("$.detalles[0].cantidadRecibidaOperacion").value(2));
            assertEquals(q("12.000"),f.line.getCantidadRecibidaAcopio());
            assertEquals(q("10.000"),f.line.getCantidadDonada());
            assertEquals(q("13.000"),f.line.getExaminada()); assertEquals(q("1.000"),f.line.getNoAceptada());
            assertEquals(q("2.000"),f.line.getExcedenteAprobado());
            var captured=ArgumentCaptor.forClass(AuditRecord.class); verify(audit).saveAndFlush(captured.capture());
            var data=json.readTree(captured.getValue().getDatosNuevos());
            assertEquals(master && own,data.get("aprobacionPropiaExcepcionalMaestro").asBoolean());
            assertEquals(own ? 17L : 29L,data.get("registradorId").asLong());
            assertEquals(17L,data.get("decisorId").asLong());
        }
    }
    @Test void forgedIdentityInJsonAndPrincipalCannotReplaceTheDatabaseAccountFlag() throws Exception {
        f.prepare(false,true);
        String body="""
            {"operacionId":"93765908-ff42-4681-99a2-514461402fd1","aprobar":true,"observaciones":"Apto",
             "adminMaestro":true,"admin_maestro":1,"decisorId":3,"registradorId":999,
             "aprobacionPropiaExcepcionalMaestro":true}
            """;
        var response=mvc.perform(post(endpoint()).with(user(f.actor.getCorreo()).authorities(
            () -> "ACCESO_ADMINISTRAR",() -> "ADMIN_MAESTRO"))
            .contentType("application/json").content(body)).andReturn().getResponse();
        // Either reject unknown fields or reject the ordinary registrant; neither policy may mutate anything.
        assertTrue(response.getStatus()==400 || response.getStatus()==403);
        assertNull(f.operation.getDecision()); assertEquals(q("10.000"),f.line.getCantidadRecibidaAcopio());
        verifyNoInteractions(audit,movements);
    }
    @Test void anonymousAndAccountsWithoutAdministrativeAccessCannotApproveEvenAMarkedAccount() throws Exception {
        f.prepare(true,true); String body=json.writeValueAsString(f.approval());
        mvc.perform(post(endpoint()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(endpoint()).with(user(f.actor.getCorreo()).authorities(() -> "DONACION_REGISTRAR"))
            .contentType("application/json").content(body)).andExpect(status().isForbidden());
        assertNull(f.operation.getDecision()); verifyNoInteractions(audit,movements);
    }
    @Test void httpRetriesDoNotIncreaseAuthorizedQuantitiesOrRepeatAudit() throws Exception {
        f.prepare(true,true); String body=json.writeValueAsString(f.approval());
        for (int attempt=0;attempt<2;attempt++) mvc.perform(post(endpoint())
            .with(user(f.actor.getCorreo()).authorities(() -> "ACCESO_ADMINISTRAR"))
            .contentType("application/json").content(body)).andExpect(status().isOk());
        assertEquals(q("12.000"),f.line.getCantidadRecibidaAcopio());
        verify(audit,times(1)).saveAndFlush(any()); verify(movements,times(1)).saveAndFlush(any());
    }
    @TestConfiguration static class Dependencies {
        @Bean DonationRepository donations() { return mock(DonationRepository.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean DonationMovementRepository movements() { return mock(DonationMovementRepository.class); }
        @Bean AuditRecordRepository audit() { return mock(AuditRecordRepository.class); }
        @Bean EntityManager em() { return mock(EntityManager.class); }
        @Bean ReceiptOperationRepository operations() { return mock(ReceiptOperationRepository.class); }
        @Bean JsonMapper json() { return JsonMapper.builder().build(); }
    }
}
