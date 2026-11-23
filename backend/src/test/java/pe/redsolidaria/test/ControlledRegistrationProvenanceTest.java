package pe.redsolidaria.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;
import pe.redsolidaria.test.ControlledFixtureAudit.*;
import pe.redsolidaria.test.FixtureLedger.*;

/** Synthetic storage and Hibernate events; real Spring transaction listeners. No context or JDBC execution. */
class ControlledRegistrationProvenanceTest {
    static final List<String> TABLES=List.of("donacion","detalle_donacion","movimiento_donacion","registro_auditoria");
    static final class Harness implements AutoCloseable {
        final ControlledFixtureAuditTest fixture=new ControlledFixtureAuditTest();
        final DataSource datasource=mock(DataSource.class);
        final ControlledTransactionCallbacksTest.MemoryManager manager=new ControlledTransactionCallbacksTest.MemoryManager();
        final IntegrityGate gate=new IntegrityGate();
        final RegistrationScenarioRelations policy=new RegistrationScenarioRelations(900101,900102,900104,900109,900105,900106);
        Snapshot durable;
        boolean unreadable,masterUnhealthy;
        final ControlledFixtureAudit audit;
        java.util.function.Consumer<PostInsertEvent> postInsert=event -> new ControlledInsertObserver().onPostInsert(event);
        Harness() { this(false); }
        Harness(boolean protectInventory) {
            if (protectInventory) PhaseOneConfirmedRows.loadAllConfirmed().forEach((table,ids) ->
                ids.forEach(id -> fixture.source.add(table,id,"marker","protected")));
            durable=fixture.source.snapshot();
            audit=new ControlledFixtureAudit(new JdbcTemplate(datasource),manager,UUID.randomUUID().toString(),"registration.unit",gate,
                new Initialization(() -> {},() -> {
                    if (unreadable) throw new AssertionError("Synthetic capture failure"); return durable;
                },() -> { if (masterUnhealthy) throw new AssertionError("Synthetic master failure"); },
                key -> fixture.source.snapshot().tables().get(key.table()).get(key.id())));
            if (protectInventory) audit.requireExistingRows(PhaseOneConfirmedRows.loadAllConfirmed());
            manager.commit=() -> durable=fixture.source.snapshot();
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                base("rol","900101","codigo","registration-role");
                base("usuario","900102","id_rol","900101","estado","ACTIVO","admin_maestro",null);
                base("donante","900104"); base("centro_acopio","900109"); base("unidad_medida","900105");
                base("recurso","900106","id_unidad_medida","900105","estado","ACTIVO");
                audit.authorizeRegistration(policy);
            });
            audit.announce();
        }
        void base(String table,String id,String... pairs) {
            fixture.source.add(table,id,pairs); audit.register(table,Long.parseLong(id));
        }
        Map<String,String> fields(String table) {
            Map<String,String> row=new HashMap<>();
            switch (table) {
                case "donacion" -> {
                    row.put("id_donante","900104"); row.put("id_usuario_registro","900102"); row.put("id_centro_acopio","900109");
                    row.put("id_emergencia",null); row.put("estado","REGISTRADA"); row.put("fecha_ingreso",null);
                    row.put("codigo_trazabilidad","RS-SYNTHETIC"); row.put("fecha_registro","2026-11-13 12:00:00");
                    row.put("observaciones",RegistrationScenarioRelations.OBSERVATIONS);
                }
                case "detalle_donacion" -> {
                    row.put("id_donacion","900201"); row.put("id_recurso","900106"); row.put("cantidad_donada","2.500");
                    for (String column:List.of("cantidad_recibida_acopio","cantidad_examinada","cantidad_no_aceptada",
                            "cantidad_excedente_pendiente","cantidad_excedente_aprobada")) row.put(column,"0.000");
                    row.put("verificacion_cerrada","0");
                }
                case "movimiento_donacion" -> {
                    row.put("id_donacion","900201"); row.put("id_usuario_responsable","900102"); row.put("tipo_evento","DONACION_PRESENTADA");
                    row.put("estado_anterior",null); row.put("estado_nuevo","REGISTRADA"); row.put("fecha_hora","2026-11-13 12:00:00");
                    row.put("ubicacion",RegistrationScenarioRelations.LOCATION); row.put("observaciones",RegistrationScenarioRelations.OBSERVATIONS);
                }
                case "registro_auditoria" -> {
                    row.put("id_usuario","900102"); row.put("id_registro","900201"); row.put("entidad","DONACION"); row.put("accion","REGISTRO_DONACION");
                    row.put("fecha_hora","2026-11-13 12:00:00"); row.put("datos_anteriores",null); row.put("datos_nuevos","{\"codigo\":\"RS-SYNTHETIC\"}");
                }
                default -> throw new AssertionError("Unexpected synthetic row");
            }
            return row;
        }
        Key store(String table,long id,Map<String,String> fields,boolean observe) {
            List<String> pairs=new ArrayList<>(); fields.forEach((column,value) -> { pairs.add(column); pairs.add(value); });
            fixture.source.add(table,Long.toString(id),pairs.toArray(String[]::new));
            if (observe) {
                var mapping=mock(EntityPersister.class); when(mapping.getTableName()).thenReturn(table);
                postInsert.accept(new PostInsertEvent(new Object(),id,new Object[0],mapping,null));
            }
            return new Key(table,Long.toString(id));
        }
        void insert(int index) { store(TABLES.get(index),900201+index,fields(TABLES.get(index)),true); }
        void all() { for (int i=0;i<4;i++) insert(i); }
        FixtureLedger ledger() { return (FixtureLedger)ReflectionTestUtils.getField(audit,"ledger"); }
        void blocked() {
            assertThrows(AssertionError.class,gate::requireSafe);
            assertThrows(AssertionError.class,audit::requireReadyForScenario);
            var entered=new java.util.concurrent.atomic.AtomicBoolean();
            assertThrows(AssertionError.class,() -> new TransactionTemplate(manager).executeWithoutResult(s -> entered.set(true)));
            assertFalse(entered.get(),"A blocked transaction must not execute its body");
        }
        @Override public void close() { audit.close(); verifyNoInteractions(datasource); }
    }
    @Test void allFourRealObservedKeysWaitForCommitAndShareOneTransaction() {
        try (var h=new Harness()) {
            var tx=new TransactionTemplate(h.manager); tx.setName("registration.service");
            tx.executeWithoutResult(status -> {
                h.all(); assertEquals(4,h.ledger().keys(State.ASSIGNED).size());
                assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty());
                assertTrue(h.durable.tables().get("donacion").isEmpty());
            });
            var ids=h.audit.requireCommittedRegistration(); assertEquals(4,ids.size());
            assertEquals("900201",ids.get("donacion")); assertEquals("900204",ids.get("registro_auditoria"));
            assertThrows(UnsupportedOperationException.class,ids::clear);
            var manifest=h.ledger().manifest();
            @SuppressWarnings("unchecked") var rows=(List<Entry>)manifest.get("rows");
            assertEquals(1,rows.stream().filter(e -> e.state()==State.COMMITTED_OPERATION).map(Entry::transaction).distinct().count());
            h.audit.finish();
        }
    }
    @Test void entityManagerPersistPathUsesTheSamePostInsertObserverForTheAudit() {
        try (var h=new Harness()) {
            EntityManager entityManager=mock(EntityManager.class);
            var record=new pe.redsolidaria.modules.audit.domain.AuditRecord(mock(pe.redsolidaria.modules.access.domain.UserAccount.class),
                "DONACION","900201","REGISTRO_DONACION",null,"{}");
            doAnswer(call -> { h.insert(3); return null; }).when(entityManager).persist(record);
            new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                h.insert(0); h.insert(1); h.insert(2); entityManager.persist(record);
                assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty());
            });
            verify(entityManager).persist(record); assertEquals(4,h.audit.requireCommittedRegistration().size());
        }
    }
    @ParameterizedTest @ValueSource(ints={1,2,3,4})
    void rollbackKeepsEveryObservedIdentityUncertifiedAndAbsent(int inserted) {
        try (var h=new Harness()) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                for (int i=0;i<inserted;i++) h.insert(i); status.setRollbackOnly();
            });
            assertEquals(inserted,h.ledger().keys(State.ROLLED_BACK).size());
            assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty());
            h.audit.requireReadyForScenario(); h.audit.finish();
        }
    }
    @Test void uncertainCommitCannotCertifyAnyOfTheFourRows() {
        try (var h=new Harness()) {
            h.manager.commitUncertain=true;
            assertThrows(TransactionSystemException.class,() -> new TransactionTemplate(h.manager).executeWithoutResult(status -> h.all()));
            assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty()); h.blocked();
        }
    }
    @ParameterizedTest @ValueSource(strings={"capture","master"})
    void auditFailureAfterDurableCommitBlocksCertificationOfIntegrityWithoutFakingRollback(String stage) {
        try (var h=new Harness()) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                h.all(); h.unreadable=stage.equals("capture"); h.masterUnhealthy=stage.equals("master");
            });
            assertEquals(4,h.ledger().keys(State.COMMITTED_OPERATION).size());
            assertTrue(h.ledger().keys(State.ROLLED_BACK).isEmpty()); h.blocked();
            assertThrows(AssertionError.class,h.audit::finish);
        }
    }
    @ParameterizedTest @ValueSource(strings={"donacion","detalle_donacion","movimiento_donacion","registro_auditoria"})
    void wrongParentsOrActorsAtInsertAreRejectedAndLatchIntegrity(String table) {
        try (var h=new Harness()) {
            assertThrows(AssertionError.class,() -> new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                int index=TABLES.indexOf(table); for (int i=0;i<index;i++) h.insert(i);
                var values=h.fields(table);
                values.put(switch (table) { case "donacion" -> "id_usuario_registro"; case "detalle_donacion" -> "id_recurso";
                    case "movimiento_donacion" -> "id_donacion"; default -> "id_registro"; },"3");
                h.store(table,900201+index,values,true);
            }));
            h.blocked();
        }
    }
    @ParameterizedTest @ValueSource(strings={"action","entity","event","quantity"})
    void wrongRegistrationMeaningIsRejectedAtObservation(String wrong) {
        try (var h=new Harness()) {
            assertThrows(AssertionError.class,() -> new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                int index=wrong.equals("quantity") ? 1 : wrong.equals("event") ? 2 : 3;
                for (int i=0;i<index;i++) h.insert(i);
                var values=h.fields(TABLES.get(index));
                values.put(switch(wrong) { case "action" -> "accion"; case "entity" -> "entidad"; case "event" -> "tipo_evento"; default -> "cantidad_donada"; },"wrong");
                h.store(TABLES.get(index),900201+index,values,true);
            }));
            h.blocked();
        }
    }
    @ParameterizedTest @ValueSource(ints={1,2,3})
    void partiallyCommittedRegistrationNeverPassesFinalIntegrity(int inserted) {
        try (var h=new Harness()) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> { for (int i=0;i<inserted;i++) h.insert(i); });
            h.blocked();
        }
    }
    @Test void anotherDonationWithValidFixtureParentsCannotAcquireAnObservedIdentity() {
        try (var h=new Harness()) {
            assertThrows(AssertionError.class,() -> new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                h.insert(0); h.store("donacion",900999,h.fields("donacion"),true);
            })); h.blocked();
        }
    }
    @Test void childCannotBeAttributedBeforeTheDonationWasObserved() {
        try (var h=new Harness()) {
            assertThrows(AssertionError.class,() -> new TransactionTemplate(h.manager).executeWithoutResult(status -> h.insert(1)));
            h.blocked();
        }
    }
    @Test void snapshotsNeverAdoptAnUnobservedFourthRow() {
        try (var h=new Harness()) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                h.insert(0); h.insert(1); h.insert(2); h.store("registro_auditoria",900204,h.fields("registro_auditoria"),false);
            });
            assertEquals(3,h.ledger().keys(State.COMMITTED_OPERATION).size()); h.blocked();
        }
    }
    @Test void mutationOfAnOtherwiseMutableDonationColumnIsStillForbiddenForRegistration() {
        try (var h=new Harness()) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                h.all(); h.fixture.change("donacion","900201","estado","RECIBIDA");
            }); h.blocked();
        }
    }
    @Test void unrelatedOperationTablesAreNotPermittedByTheRegistrationPolicy() {
        try (var h=new Harness()) {
            assertThrows(AssertionError.class,() -> new TransactionTemplate(h.manager).executeWithoutResult(status ->
                ControlledFixtureAudit.hibernateInserted("recepcion_operacion","900300")));
            h.blocked();
        }
    }
    @Test void all381IdentitiesIncludingAuditRowsRemainProtectedDuringRegistration() {
        try (var h=new Harness(true)) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> h.all());
            assertEquals(4,h.audit.requireCommittedRegistration().size());
            String protectedAudit=PhaseOneConfirmedRows.loadAllConfirmed().get("registro_auditoria").iterator().next();
            new TransactionTemplate(h.manager).executeWithoutResult(status -> h.fixture.change("registro_auditoria",protectedAudit,"marker","changed"));
            h.blocked();
        }
    }
    @Test void defaultScenarioStillRejectsDonationAndDetailAsOperations() {
        for (String table:List.of("donacion","detalle_donacion")) {
            try (var h=new ControlledTransactionCallbacksTest.Harness()) {
                assertThrows(AssertionError.class,() -> new TransactionTemplate(h.manager).executeWithoutResult(status ->
                    ControlledFixtureAudit.hibernateInserted(table,"900201")));
                assertThrows(AssertionError.class,h.gate::requireSafe);
            }
        }
    }
    @Test void protectedDonationPrimaryKeyCannotBeReusedEvenWithTheRegistrationPolicy() {
        try (var h=new Harness(true)) {
            String existing=PhaseOneConfirmedRows.loadAllConfirmed().get("donacion").iterator().next();
            assertThrows(AssertionError.class,() -> new TransactionTemplate(h.manager).executeWithoutResult(status ->
                h.store("donacion",Long.parseLong(existing),h.fields("donacion"),true)));
            assertTrue(h.ledger().keys(State.COMMITTED_OPERATION).isEmpty()); h.blocked();
        }
    }
    @Test void insertsCannotBeSpreadAcrossDifferentTransactionsEvenAfterRollback() {
        try (var h=new Harness()) {
            new TransactionTemplate(h.manager).executeWithoutResult(status -> { h.insert(0); status.setRollbackOnly(); });
            assertThrows(AssertionError.class,() -> new TransactionTemplate(h.manager).executeWithoutResult(status -> h.insert(1)));
            h.blocked();
        }
    }
    @Test void policyCannotBeEnabledAfterPreparationOrReplaceAnExistingPolicy() {
        try (var h=new Harness()) {
            assertThrows(AssertionError.class,() -> h.audit.authorizeRegistration(new RegistrationScenarioRelations(1,2,3,4,5,6)));
            h.blocked();
        }
    }
    @ParameterizedTest @ValueSource(strings={"missing-id","multiple-tables"})
    void unusablePostInsertEvidenceAlsoLatchesTheScenarioGate(String wrong) {
        try (var h=new Harness()) {
            assertThrows(AssertionError.class,() -> new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                var mapping=mock(EntityPersister.class); when(mapping.hasMultipleTables()).thenReturn(wrong.equals("multiple-tables"));
                new ControlledInsertObserver().onPostInsert(new PostInsertEvent(new Object(),wrong.equals("missing-id") ? null : 900201L,new Object[0],mapping,null));
            })); h.blocked();
        }
    }
}
