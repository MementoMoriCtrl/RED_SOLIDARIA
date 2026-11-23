package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import pe.redsolidaria.test.*;

/** Metadata and mock fixture planning only; integration classes are never started. */
class ControlledRegistrationSupportTest {
    @Test void onlyTheNewSuiteSelectsRegistrationModeAndRequiresTheCompleteInventory() throws Exception {
        var registration=new DonationRegistrationControlledMySqlIntegrationTest();
        assertEquals(ControlledDonationMySqlSupport.class,registration.getClass().getSuperclass());
        assertTrue(registration.registrationFixturesOnly());
        assertEquals(PhaseOneConfirmedRows.loadAllConfirmed(),registration.requiredExistingRows());
        assertEquals(381,registration.requiredExistingRows().values().stream().mapToInt(Set::size).sum());
        assertNotNull(registration.getClass().getAnnotation(Isolated.class));
        assertNull(AnnotatedElementUtils.findMergedAnnotation(registration.getClass(),Transactional.class));
        for (var method:registration.getClass().getDeclaredMethods()) assertNull(method.getAnnotation(Transactional.class));
        for (var suite:List.of(new PhaseOneControlledMySqlIntegrationTest(),new ModelBComplementaryMySqlIntegrationTest(),new DonationQueryMySqlIntegrationTest()))
            assertFalse(suite.registrationFixturesOnly());
        assertEquals(191,new ModelBComplementaryMySqlIntegrationTest().requiredExistingRows().values().stream().mapToInt(Set::size).sum());
        assertEquals(381,new DonationQueryMySqlIntegrationTest().requiredExistingRows().values().stream().mapToInt(Set::size).sum());
    }
    @Test void preparationAllocatesExactlySixBaseRowsAndNoDonationOrDetail() {
        var suite=new DonationRegistrationControlledMySqlIntegrationTest(); suite.fixtureAudit=mock(ControlledFixtureAudit.class);
        when(suite.fixtureAudit.insertJdbcBase(anyString(),any(Object[].class))).thenReturn(101L,102L,104L,109L,105L,106L);
        suite.prepareRegistrationFixtures(UUID.randomUUID().toString());
        var sql=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(suite.fixtureAudit,times(6)).insertJdbcBase(sql.capture(),any(Object[].class));
        assertEquals(List.of("rol","usuario","donante","centro_acopio","unidad_medida","recurso"),sql.getAllValues().stream()
            .map(value -> value.substring("insert into ".length(),value.indexOf('('))).toList());
        var policy=org.mockito.ArgumentCaptor.forClass(RegistrationScenarioRelations.class);
        verify(suite.fixtureAudit).authorizeRegistration(policy.capture()); verifyNoMoreInteractions(suite.fixtureAudit);
        assertEquals(102L,ReflectionTestUtils.getField(policy.getValue(),"actor"));
        assertEquals(104L,suite.registrationDonorId); assertEquals(0,suite.donationId); assertEquals(0,suite.lineId);
    }
    @Test void atomicitySuiteUsesTheSameGuardsWithoutStartingOrMockingTheIntegrationContext() {
        var suite=new DonationRegistrationAtomicityControlledMySqlIntegrationTest();
        assertEquals(ControlledDonationMySqlSupport.class,suite.getClass().getSuperclass());
        assertTrue(suite.registrationFixturesOnly()); assertEquals(PhaseOneConfirmedRows.loadAllConfirmed(),suite.requiredExistingRows());
        assertNotNull(suite.getClass().getAnnotation(Isolated.class));
        assertNull(AnnotatedElementUtils.findMergedAnnotation(suite.getClass(),Transactional.class));
        assertEquals(2,Arrays.stream(suite.getClass().getDeclaredMethods()).filter(method -> method.isAnnotationPresent(Test.class)).count());
        for (var method:suite.getClass().getDeclaredMethods()) assertNull(method.getAnnotation(Transactional.class));
        assertEquals(Set.of(pe.redsolidaria.modules.donations.application.DonationRegistrationService.class,jakarta.persistence.EntityManagerFactory.class),
            Arrays.stream(suite.getClass().getDeclaredFields()).map(java.lang.reflect.Field::getType).collect(java.util.stream.Collectors.toSet()));
    }
}
