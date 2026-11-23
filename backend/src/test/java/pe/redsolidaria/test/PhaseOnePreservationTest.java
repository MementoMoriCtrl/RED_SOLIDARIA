package pe.redsolidaria.test;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import pe.redsolidaria.test.FixtureLedger.Key;

class PhaseOnePreservationTest {
    @Test void frozenInventoryHasExactlyThe191CommittedIdentitiesAndExcludesRollbackAttempts() {
        var inventory=PhaseOneConfirmedRows.load();
        assertEquals(191,inventory.values().stream().mapToInt(Set::size).sum());
        assertEquals(28,inventory.get("registro_auditoria").size());
        assertEquals(19,inventory.get("movimiento_donacion").size());
        assertFalse(inventory.get("movimiento_donacion").contains("16"));
        assertFalse(inventory.get("traslado_recurso").contains("f8a02053-fa0d-43f2-a30f-a985d3973cf0"));
        assertThrows(UnsupportedOperationException.class,() -> inventory.get("rol").clear());
    }
    @Test void all191RowsAreRequiredAndEachIsImmutableAndCannotDisappearFromLaterCaptures() {
        assertEveryIdentityIsProtected(PhaseOneConfirmedRows.load());
    }
    @Test void all381RowsAreRequiredAndEachIsImmutableAndCannotDisappearFromLaterCaptures() {
        assertEveryIdentityIsProtected(PhaseOneConfirmedRows.loadAllConfirmed());
    }
    private void assertEveryIdentityIsProtected(Map<String,Set<String>> inventory) {
        var fixture=new ControlledFixtureAuditTest(); var source=fixture.source;
        List<Key> identities=new ArrayList<>();
        inventory.forEach((table,ids) -> ids.forEach(id -> { source.add(table,id,"marker","original"); identities.add(new Key(table,id)); }));
        var before=source.snapshot(); assertEquals(21,before.tables().size());
        ControlledFixtureAudit.verifyRequiredRows(before,inventory);
        for (Key key:identities) {
            source.add(key.table(),key.id(),"marker","changed");
            assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verify(before,source.snapshot(),Set.of(),Set.of()),key.toString());
            source.remove(key.table(),key.id());
            assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verifyRequiredRows(source.snapshot(),inventory),key.toString());
            assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verify(before,source.snapshot(),Set.of(),Set.of()),key.toString());
            source.add(key.table(),key.id(),"marker","original");
        }
        assertDoesNotThrow(() -> ControlledFixtureAudit.verify(before,source.snapshot(),Set.of(),Set.of()));
    }
    @Test void missingPhaseOneRowsLatchInitializationFailureBeforeAnyNewFixtureWork() {
        IntegrityGate gate=new IntegrityGate(); var source=new ControlledFixtureAuditTest.MemorySource();
        assertThrows(AssertionError.class,() -> gate.initialize("required-PhaseOne-baseline",() -> {
            ControlledFixtureAudit.verifyRequiredRows(source.snapshot(),PhaseOneConfirmedRows.load()); return null;
        }));
        assertThrows(AssertionError.class,() -> gate.initialize("next-scenario",() -> fail("new fixture must not start")));
    }
    @Test void missingModelBIdentityBlocksThe381RowPrerequisiteEvenWhenThe191PhaseOneRowsExist() {
        var source=new ControlledFixtureAuditTest.MemorySource();
        PhaseOneConfirmedRows.load().forEach((table,ids) -> ids.forEach(id -> source.add(table,id,"marker","original")));
        assertDoesNotThrow(() -> ControlledFixtureAudit.verifyRequiredRows(source.snapshot(),PhaseOneConfirmedRows.load()));
        IntegrityGate gate=new IntegrityGate();
        assertThrows(AssertionError.class,() -> gate.initialize("required-381-baseline",() -> {
            ControlledFixtureAudit.verifyRequiredRows(source.snapshot(),PhaseOneConfirmedRows.loadAllConfirmed()); return null;
        }));
        assertThrows(AssertionError.class,gate::requireSafe);
    }
    @Test void aSecondResourceCannotReuseARestoredUnitOrSwapItsDeclaredDonation() {
        var fixture=new ControlledFixtureAuditTest(); var before=fixture.source.snapshot(); fixture.fixture();
        fixture.addBase("recurso","200","id_unidad_medida","105");
        fixture.addBase("detalle_donacion","201","id_donacion","111","id_recurso","200");
        var ledger=fixture.attributedLedger(false);
        var relations=new ScenarioRelations(111,102,112,106,105,109,110,Set.of(113L,114L),Map.of(201L,200L),103L);
        assertDoesNotThrow(() -> relations.verify(before,fixture.source.snapshot(),ledger));
        fixture.change("recurso","200","id_unidad_medida","50");
        assertThrows(AssertionError.class,() -> relations.verify(before,fixture.source.snapshot(),ledger));
        fixture.change("recurso","200","id_unidad_medida","105"); fixture.change("detalle_donacion","201","id_donacion","999");
        assertThrows(AssertionError.class,() -> relations.verify(before,fixture.source.snapshot(),ledger));
    }
}
