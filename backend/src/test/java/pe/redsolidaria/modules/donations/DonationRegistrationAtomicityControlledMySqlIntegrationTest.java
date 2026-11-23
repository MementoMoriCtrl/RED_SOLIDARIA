package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.redsolidaria.modules.donations.api.dto.DonationDtos.*;
import pe.redsolidaria.modules.donations.application.DonationRegistrationService;
import pe.redsolidaria.test.*;

/** Requires separate authorization. No mocks, outer test transaction, cleanup or autoincrement reset. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@WithMockUser(authorities="DONACION_REGISTRAR")
class DonationRegistrationAtomicityControlledMySqlIntegrationTest extends ControlledDonationMySqlSupport {
    @Autowired DonationRegistrationService registrations;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Override protected Map<String,Set<String>> requiredExistingRows() { return PhaseOneConfirmedRows.loadAllConfirmed(); }
    @Override protected boolean registrationFixturesOnly() { return true; }

    @Test void rollsBackFourObservedInsertsWhenAuditPersistenceFails() {
        verifyAtomicity(ControlledRegistrationFailureProbe.Point.AUDIT,4);
    }
    @Test void rollsBackDonationDetailAndMovementWhenMovementPersistenceFails() {
        verifyAtomicity(ControlledRegistrationFailureProbe.Point.MOVEMENT,3);
    }
    private void verifyAtomicity(ControlledRegistrationFailureProbe.Point point,int expected) {
        try {
            assertTrue(AopUtils.isAopProxy(registrations));
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            fixtureAudit.requireTransactionalRegistrationTables();
            var request=new CreateDonationRequest(registrationDonorId,null,collectionId,
                "  "+RegistrationScenarioRelations.LOCATION+"  ","  "+RegistrationScenarioRelations.OBSERVATIONS+"  ",
                List.of(new DonationLineRequest(resourceId,RegistrationScenarioRelations.QUANTITY)));
            try (var probe=ControlledRegistrationFailureProbe.install(entityManagerFactory,fixtureAudit,point)) {
                RuntimeException failure=assertThrows(RuntimeException.class,() -> registrations.register(request,email));
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                var reverted=probe.certify(failure);
                assertEquals(expected,reverted.size());
                assertTrue(reverted.containsKey("donacion")); assertTrue(reverted.containsKey("detalle_donacion"));
                assertTrue(reverted.containsKey("movimiento_donacion"));
                assertEquals(point==ControlledRegistrationFailureProbe.Point.AUDIT,reverted.containsKey("registro_auditoria"));
            }
            fixtureAudit.requireReadyForScenario();
        } catch (RuntimeException | Error failure) {
            ControlledFixtureAudit.blockFurtherScenarios("registration-atomicity-scenario",failure); throw failure;
        }
    }
}
