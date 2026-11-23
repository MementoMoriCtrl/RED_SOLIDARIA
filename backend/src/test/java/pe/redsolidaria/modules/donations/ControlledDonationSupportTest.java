package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.*;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.util.ReflectionTestUtils;
import pe.redsolidaria.test.*;

/** Metadata and test fixture planning only. Never asks Spring to start an integration context. */
class ControlledDonationSupportTest {
    @Test void bothSuitesInheritOneProtectedLifecycleDatasourceAndExclusiveExecutionPolicy() throws Exception {
        for (Class<?> suite:List.of(PhaseOneControlledMySqlIntegrationTest.class,ModelBComplementaryMySqlIntegrationTest.class)) {
            assertEquals(ControlledDonationMySqlSupport.class,suite.getSuperclass());
            assertNotNull(suite.getAnnotation(Isolated.class));
            assertEquals("red_solidaria_test",suite.getAnnotation(ResourceLock.class).value());
            assertEquals(ExecutionMode.SAME_THREAD,suite.getAnnotation(Execution.class).value());
            assertArrayEquals(new Class<?>[]{ControlledMySqlConfiguration.class},AnnotatedElementUtils.findMergedAnnotation(suite,Import.class).value());
            assertEquals(9,Arrays.stream(suite.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(Test.class)).count());
            assertTrue(Arrays.stream(suite.getDeclaredFields()).noneMatch(f -> f.getType()==PhaseOneControlledMySqlIntegrationTest.class));
            assertTrue(Arrays.stream(suite.getDeclaredMethods()).noneMatch(m -> m.isAnnotationPresent(BeforeEach.class) || m.isAnnotationPresent(AfterEach.class)));
        }
        var initialization=ControlledDonationMySqlSupport.class.getDeclaredMethod("prepareProtectedScenario",TestInfo.class);
        var finish=ControlledDonationMySqlSupport.class.getDeclaredMethod("verifyProtectedScenario");
        assertTrue(Modifier.isFinal(initialization.getModifiers())); assertTrue(initialization.isAnnotationPresent(BeforeEach.class));
        assertTrue(Modifier.isFinal(finish.getModifiers())); assertTrue(finish.isAnnotationPresent(AfterEach.class));
        assertTrue(ControlledDonationMySqlSupport.class.getDeclaredMethod("database",DynamicPropertyRegistry.class).isAnnotationPresent(DynamicPropertySource.class));
    }
    @Test void onlyTheMultiresourceScenarioAddsTwoExplicitBaseInsertsBeforeRelationsAreFrozen() {
        for (var method:ModelBComplementaryMySqlIntegrationTest.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Test.class)) continue;
            var model=new ModelBComplementaryMySqlIntegrationTest(); var audit=mock(ControlledFixtureAudit.class);
            model.fixtureAudit=audit; model.unitId=105; model.donationId=111;
            when(audit.insertJdbcBase(anyString(),any(Object[].class))).thenReturn(200L,201L);
            model.prepareAdditionalBaseFixtures("ModelBComplementaryMySqlIntegrationTest."+method.getName());
            if (method.getName().equals("automaticClosureRequiresAllResourcesAndShortClosureCannotReopen")) {
                verify(audit).insertJdbcBase(startsWith("insert into recurso("),eq(105L),anyString(),eq("IT"),eq("ACTIVO"));
                verify(audit).insertJdbcBase(startsWith("insert into detalle_donacion("),eq(111L),eq(200L),eq(new java.math.BigDecimal("10")));
                assertEquals(Map.of(201L,200L),ReflectionTestUtils.getField(model,"additionalLines"));
            } else verifyNoInteractions(audit);
        }
    }
    @Test void querySuiteRequires381AndHasAnExplicitAnonymousRequestWithoutLocalLifecycleOverrides() throws Exception {
        var query=new DonationQueryMySqlIntegrationTest();
        assertEquals(ControlledDonationMySqlSupport.class,query.getClass().getSuperclass());
        assertEquals(PhaseOneConfirmedRows.loadAllConfirmed(),query.requiredExistingRows());
        assertEquals(381,query.requiredExistingRows().values().stream().mapToInt(Set::size).sum());
        assertNotNull(query.getClass().getDeclaredMethod("readsKnownDonationFromLocalMySqlByTrackingCode")
            .getAnnotation(org.springframework.security.test.context.support.WithAnonymousUser.class));
        assertNotNull(query.getClass().getAnnotation(Isolated.class));
        assertTrue(Modifier.isPrivate(ControlledDonationMySqlSupport.class.getDeclaredMethod("fixturesInDedicatedDatabaseOnly").getModifiers()));
        assertThrows(NullPointerException.class,() -> query.insert("insert into rol(codigo) values (?)","unobserved"));
        assertTrue(Arrays.stream(query.getClass().getDeclaredMethods()).noneMatch(m -> m.isAnnotationPresent(BeforeEach.class) || m.isAnnotationPresent(AfterEach.class)));
    }
    @Test void queryPreparationUsesOneNamedTransactionAndChecksCallbacksBeforeReadingTrackingCode() {
        var query=new DonationQueryMySqlIntegrationTest();
        query.tx=mock(org.springframework.transaction.PlatformTransactionManager.class);
        var status=mock(org.springframework.transaction.TransactionStatus.class);
        when(query.tx.getTransaction(any())).thenReturn(status);
        query.fixtureAudit=mock(ControlledFixtureAudit.class); query.jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        query.donationId=111;
        query.prepareOperationFixtures();
        var ordered=inOrder(query.tx,query.fixtureAudit,query.jdbc);
        ordered.verify(query.tx).getTransaction(argThat(d -> "donation-query.presented-movement".equals(d.getName())));
        ordered.verify(query.fixtureAudit).insertPresentedDonationMovement();
        ordered.verify(query.tx).commit(status);
        ordered.verify(query.fixtureAudit).requireReadyForScenario();
        ordered.verify(query.jdbc).queryForObject(anyString(),eq(String.class),eq(111L));
    }
    @Test void queryDoesNotReadOrContinueAfterCallbackReadinessFailure() {
        var query=new DonationQueryMySqlIntegrationTest(); query.tx=mock(org.springframework.transaction.PlatformTransactionManager.class);
        query.fixtureAudit=mock(ControlledFixtureAudit.class); query.jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        doThrow(new AssertionError("callback failed")).when(query.fixtureAudit).requireReadyForScenario();
        assertThrows(AssertionError.class,query::prepareOperationFixtures); verifyNoInteractions(query.jdbc);
    }
    @Test void modelBRequiresAll191IdentitiesBeforePreparingAnyScenario() {
        var model=new ModelBComplementaryMySqlIntegrationTest();
        assertEquals(PhaseOneConfirmedRows.load(),model.requiredExistingRows());
        assertEquals(191,model.requiredExistingRows().values().stream().mapToInt(Set::size).sum());
    }
}
