package pe.redsolidaria.test;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import pe.redsolidaria.test.ControlledFixtureAudit.*;
import pe.redsolidaria.test.FixtureLedger.*;
import pe.redsolidaria.test.FixtureSchema.*;
import tools.jackson.databind.json.JsonMapper;

/** In-memory capture source and pure state machines only. No JDBC connections or Spring context. */
class ControlledFixtureAuditTest {
    static final class MemorySource implements Source {
        final Map<String,Shape> shapes=new HashMap<>(FixtureSchema.EXPECTED);
        final Map<String,List<RowImage>> rows=new HashMap<>();
        final Map<String,Map<String,Map<String,String>>> values=new HashMap<>();
        String unreadable,wrongCount;
        MemorySource() { FixtureSchema.EXPECTED.keySet().forEach(t -> { rows.put(t,new ArrayList<>()); values.put(t,new HashMap<>()); }); }
        @Override public Set<String> tables() { return rows.keySet(); }
        @Override public Shape shape(String table) { return shapes.get(table); }
        @Override public long count(String table) { return rows.get(table).size()+(table.equals(wrongCount) ? 1 : 0); }
        @Override public List<RowImage> rows(String table,Shape shape) {
            if (table.equals(unreadable)) throw new IllegalStateException("Read failed"); return rows.get(table);
        }
        void add(String table,String id,String... pairs) {
            Map<String,String> fields=new TreeMap<>();
            fields.put(shapes.get(table).primaryKey().getFirst(),id);
            for (int i=0;i<pairs.length;i+=2) fields.put(pairs[i],pairs[i+1]);
            RowImage row=ControlledFixtureAudit.image(shapes.get(table).primaryKey(),fields);
            values.get(table).put(row.key(),Collections.unmodifiableMap(new TreeMap<>(fields)));
            rows.get(table).removeIf(r -> r.key().equals(row.key())); rows.get(table).add(row);
        }
        void remove(String table,String key) { rows.get(table).removeIf(r -> r.key().equals(key)); }
        Snapshot snapshot() { return ControlledFixtureAudit.capture(this); }
    }
    final MemorySource source=new MemorySource();
    final Set<Key> base=new HashSet<>();
    ControlledFixtureAuditTest() {
        source.add("rol","4","codigo","ADMINISTRADOR");
        source.add("usuario","3","id_rol","4","admin_maestro","1","estado","ACTIVO","correo","PRIVATE_EMAIL","password_hash","PRIVATE_PASSWORD");
        source.add("permiso","1","codigo","ACCESO_ADMINISTRAR"); source.add("permiso","2","codigo","DONACION_REGISTRAR");
        source.add("rol_permiso","4","id_permiso","1"); source.add("rol_permiso","4","id_permiso","2");
        source.add("unidad_medida","50","nombre","Restored unit");
        source.add("emergencia","60","estado","ACTIVA"); source.add("zona_afectada","70","id_emergencia","60");
    }
    void addBase(String table,String id,String... pairs) { source.add(table,id,pairs); base.add(new Key(table,id)); }
    void fixture() {
        addBase("rol","101","codigo","IT-role");
        addBase("usuario","102","id_rol","101","admin_maestro",null,"estado","ACTIVO");
        addBase("usuario","103","id_rol","101","admin_maestro",null,"estado","ACTIVO");
        addBase("donante","104","nombre","Fixture"); addBase("unidad_medida","105","nombre","Fixture");
        addBase("recurso","106","id_unidad_medida","105"); addBase("emergencia","107","estado","ACTIVA");
        addBase("zona_afectada","108","id_emergencia","107"); addBase("centro_acopio","109"); addBase("centro_distribucion","110");
        addBase("donacion","111","id_donante","104","id_emergencia","107","id_centro_acopio","109","id_usuario_registro","103","estado","REGISTRADA","fecha_ingreso",null);
        addBase("detalle_donacion","112","id_donacion","111","id_recurso","106","cantidad_donada","10.000","cantidad_recibida_acopio","0.000");
        addBase("necesidad","113","id_zona_afectada","108","id_recurso","106","cantidad_requerida","8.000");
        addBase("necesidad","114","id_zona_afectada","108","id_recurso","106","cantidad_requerida","8.000");
    }
    void operations() {
        source.add("recepcion_operacion","115","id_donacion","111","operacion_id","receipt-uuid","id_usuario_decision",null,"decision",null);
        source.add("movimiento_donacion","116","id_donacion","111","id_usuario_responsable","102");
        source.add("traslado_recurso","transfer-uuid","id_detalle_donacion","112","id_centro_acopio","109","id_centro_distribucion","110",
            "id_unidad_medida","105","id_usuario_salida","102","id_usuario_recepcion",null,"estado","EN_TRANSITO");
        source.add("entrega","117","id_usuario_responsable","102");
        source.add("detalle_entrega","118","id_entrega","117","id_detalle_donacion","112","id_necesidad","113");
        source.add("registro_auditoria","119","id_usuario","102","entidad","DONACION","id_registro","111");
        source.add("registro_auditoria","120","id_usuario","102","entidad","TRASLADO","id_registro","transfer-uuid");
        source.add("registro_auditoria","121","id_usuario","102","entidad","ENTREGA","id_registro","117");
    }
    void verify(Snapshot before) { ControlledFixtureAudit.verify(before,source.snapshot(),base,Set.of()); }
    void change(String table,String id,String column,String value) {
        Map<String,String> values=new TreeMap<>(source.values.get(table).get(id));
        values.put(column,value); source.add(table,id,values.entrySet().stream().flatMap(e -> java.util.stream.Stream.of(e.getKey(),e.getValue())).toArray(String[]::new));
    }
    @Test void validGraphAndAllConfirmedOperationTypesRemainOwned() {
        Snapshot before=source.snapshot(); fixture(); operations(); assertDoesNotThrow(() -> verify(before));
    }
    @Test void anExactExistingParentExceptionIsAllowedButTheParentRemainsProtected() {
        Snapshot before=source.snapshot(); fixture(); source.add("recurso","106","id_unidad_medida","50");
        var allowed=new ExistingReference("recurso","106","id_unidad_medida","unidad_medida","50");
        assertDoesNotThrow(() -> ControlledFixtureAudit.verify(before,source.snapshot(),base,Set.of(allowed)));
        source.add("unidad_medida","50","nombre","Altered");
        assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verify(before,source.snapshot(),base,Set.of(allowed)));
    }
    @Test void exceptionForOneChildCannotAuthorizeAnotherChildOrANonexistentParent() {
        Snapshot before=source.snapshot(); fixture(); source.add("recurso","106","id_unidad_medida","50");
        assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verify(before,source.snapshot(),base,
            Set.of(new ExistingReference("recurso","999","id_unidad_medida","unidad_medida","50"))));
        assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verify(before,source.snapshot(),base,
            Set.of(new ExistingReference("recurso","106","id_unidad_medida","unidad_medida","999"))));
    }
    @Test void registeredDonationCannotReferenceTheRestoredMaster() {
        Snapshot before=source.snapshot(); fixture(); change("donacion","111","id_usuario_registro","3");
        assertThrows(AssertionError.class,() -> verify(before));
    }
    @Test void registeredUserCannotAcquireTheRestoredAdministratorRole() {
        Snapshot before=source.snapshot(); fixture(); change("usuario","102","id_rol","4");
        assertThrows(AssertionError.class,() -> verify(before));
    }
    @ParameterizedTest @ValueSource(strings={"emergency","zone"})
    void registeredRowsCannotReferenceRestoredEmergenciesOrZones(String target) {
        Snapshot before=source.snapshot(); fixture();
        if (target.equals("emergency")) change("donacion","111","id_emergencia","60");
        else change("necesidad","113","id_zona_afectada","70");
        assertThrows(AssertionError.class,() -> verify(before));
    }
    @Test void missingAndNullMandatoryReferencesAreRejected() {
        Snapshot before=source.snapshot(); fixture(); source.add("recurso","106","id_unidad_medida","999");
        assertThrows(AssertionError.class,() -> verify(before));
        source.add("recurso","106","id_unidad_medida",null); assertThrows(AssertionError.class,() -> verify(before));
    }
    @Test void nullableRelationshipsCanLegitimatelyBeAbsent() {
        Snapshot before=source.snapshot(); fixture();
        source.add("donacion","111","id_donante","104","id_centro_acopio","109","id_emergencia",null,"id_usuario_registro","103");
        assertDoesNotThrow(() -> verify(before));
    }
    @Test void fkManipulatedAfterIdAssignmentIsRejectedEvenWhenBothParentsBelongToTheFixture() {
        Snapshot before=source.snapshot(); fixture(); Snapshot assigned=source.snapshot(); Key key=new Key("donacion","111");
        Map<Key,RowImage> images=Map.of(key,assigned.tables().get("donacion").get("111"));
        change("donacion","111","id_usuario_registro","102");
        assertDoesNotThrow(() -> verify(before)); // Ownership alone cannot detect this mutation; the assigned image must.
        assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verifyAllocatedImages(source.snapshot(),images,Set.of(key)));
    }
    @Test void confirmedBaseAndLaterOperationRowsCannotDisappear() {
        Snapshot before=source.snapshot(); fixture(); Snapshot setup=source.snapshot(); source.remove("necesidad","114");
        assertThrows(AssertionError.class,() -> verify(before));
        fixture(); operations(); Snapshot confirmed=source.snapshot(); source.remove("registro_auditoria","119");
        assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verifyCheckpoint(before,confirmed,source.snapshot()));
        assertDoesNotThrow(() -> ControlledFixtureAudit.verifyCheckpoint(before,setup,confirmed));
    }
    @Test void legitimateQuantityUpdatesAreAllowedButDeclarationsAndBaseFksStayImmutable() {
        Snapshot before=source.snapshot(); fixture(); Snapshot setup=source.snapshot();
        source.add("detalle_donacion","112","id_donacion","111","id_recurso","106","cantidad_donada","10.000","cantidad_recibida_acopio","6.000");
        source.add("necesidad","113","id_zona_afectada","108","id_recurso","106","cantidad_requerida","100.000");
        assertDoesNotThrow(() -> ControlledFixtureAudit.verifyCheckpoint(before,setup,source.snapshot()));
        source.add("detalle_donacion","112","id_donacion","111","id_recurso","106","cantidad_donada","20.000","cantidad_recibida_acopio","6.000");
        assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verifyCheckpoint(before,setup,source.snapshot()));
    }
    @Test void legitimateReceiptDecisionsAndTransferConfirmationsAreAllowed() {
        Snapshot before=source.snapshot(); fixture(); operations(); Snapshot checkpoint=source.snapshot();
        source.add("recepcion_operacion","115","id_donacion","111","operacion_id","receipt-uuid","id_usuario_decision","102","decision","APROBADO");
        source.add("traslado_recurso","transfer-uuid","id_detalle_donacion","112","id_centro_acopio","109","id_centro_distribucion","110",
            "id_unidad_medida","105","id_usuario_salida","102","id_usuario_recepcion","102","estado","RECIBIDO");
        assertDoesNotThrow(() -> { verify(before); ControlledFixtureAudit.verifyCheckpoint(before,checkpoint,source.snapshot()); });
    }
    @ParameterizedTest @ValueSource(strings={"usuario","rol","rol_permiso","permiso","donacion"})
    void protectedRowsCannotBeModifiedOrDeleted(String table) {
        if (table.equals("donacion")) source.add("donacion","90","id_usuario_registro","3");
        Snapshot before=source.snapshot(); RowImage old=source.rows.get(table).getFirst(); source.rows.get(table).removeFirst();
        assertThrows(AssertionError.class,() -> verify(before));
        source.rows.get(table).add(new RowImage(old.key(),"changed",old.references(),old.fields()));
        assertThrows(AssertionError.class,() -> verify(before));
    }
    @Test void unrelatedInsertionsCrossFixtureOperationsAndAuditTargetsAreRejected() {
        Snapshot before=source.snapshot(); fixture(); source.add("permiso","999","codigo","UNEXPECTED");
        assertThrows(AssertionError.class,() -> verify(before)); source.remove("permiso","999");
        source.add("movimiento_donacion","116","id_donacion","999","id_usuario_responsable","102");
        assertThrows(AssertionError.class,() -> verify(before)); source.remove("movimiento_donacion","116");
        source.add("registro_auditoria","119","id_usuario","102","entidad","DONACION","id_registro","999");
        assertThrows(AssertionError.class,() -> verify(before));
    }
    @Test void orphanDeliveriesAndUnexpectedSchemaChangesAreRejected() {
        Snapshot before=source.snapshot(); fixture(); source.add("entrega","117","id_usuario_responsable","102");
        assertThrows(AssertionError.class,() -> verify(before)); source.remove("entrega","117");
        source.rows.put("extra_table",new ArrayList<>()); source.shapes.put("extra_table",FixtureSchema.table("id_extra"));
        assertThrows(AssertionError.class,() -> verify(before));
    }
    @Test void allTwentyOneProtectedTablesAreRequiredIncludingEmptyTables() {
        assertEquals(21,FixtureSchema.EXPECTED.size());
        source.rows.remove("flyway_schema_history"); assertThrows(AssertionError.class,source::snapshot);
    }
    @Test void catalogNamesAndForeignKeysMatchTheUnchangedMigrationFiles() throws Exception {
        Set<String> tables=new HashSet<>(),foreignKeys=new HashSet<>();
        Pattern create=Pattern.compile("(?i)CREATE TABLE\\s+(\\w+)\\s*\\("),alter=Pattern.compile("(?i)ALTER TABLE\\s+(\\w+)");
        Pattern fk=Pattern.compile("(?i)FOREIGN KEY\\s*\\(\\s*(\\w+)\\s*\\)\\s*REFERENCES\\s+(\\w+)\\s*\\(\\s*(\\w+)\\s*\\)");
        try (var files=Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path file:files.filter(p -> p.getFileName().toString().matches("V[1-5]__.*\\.sql")).toList()) {
                for (String statement:Files.readString(file).split(";")) {
                    var created=create.matcher(statement); var altered=alter.matcher(statement);
                    String table=null;
                    if (created.find()) { table=created.group(1); tables.add(table); }
                    else if (altered.find()) table=altered.group(1);
                    var reference=fk.matcher(statement);
                    while (reference.find()) {
                        assertNotNull(table); foreignKeys.add(table+"/"+reference.group(1)+"/"+reference.group(2)+"/"+reference.group(3));
                    }
                }
            }
        }
        tables.add("flyway_schema_history"); assertEquals(FixtureSchema.EXPECTED.keySet(),tables);
        Set<String> expected=new HashSet<>(); FixtureSchema.EXPECTED.forEach((table,shape) -> shape.foreignKeys().forEach(
            key -> expected.add(table+"/"+key.column()+"/"+key.table()+"/"+key.parentColumn())));
        assertEquals(expected,foreignKeys);
    }
    @Test void missingPrimaryKeyOrForeignKeyCannotBeIgnored() {
        source.shapes.put("donacion",new Shape(List.of(),List.of())); assertThrows(AssertionError.class,source::snapshot);
        source.shapes.put("donacion",new Shape(List.of("id_donacion"),List.of())); assertThrows(AssertionError.class,source::snapshot);
    }
    @Test void unreadableTablesAndIncompleteOrDuplicatedCapturesFailClosed() {
        source.unreadable="usuario"; assertThrows(IllegalStateException.class,source::snapshot); source.unreadable=null;
        source.wrongCount="usuario"; assertThrows(AssertionError.class,source::snapshot); source.wrongCount=null;
        source.rows.get("usuario").add(source.rows.get("usuario").getFirst()); assertThrows(AssertionError.class,source::snapshot);
    }
    @Test void hashesAndSnapshotsAreIndependentOfRowAndColumnIterationOrderAndDistinguishNull() {
        Snapshot first=source.snapshot(); source.rows.values().forEach(Collections::reverse); assertEquals(first,source.snapshot());
        Map<String,String> a=new LinkedHashMap<>(); a.put("id_usuario","1"); a.put("estado",null);
        Map<String,String> b=new LinkedHashMap<>(); b.put("estado",null); b.put("id_usuario","1");
        assertEquals(ControlledFixtureAudit.image(List.of("id_usuario"),a),ControlledFixtureAudit.image(List.of("id_usuario"),b));
        b.put("estado",""); assertNotEquals(ControlledFixtureAudit.image(List.of("id_usuario"),a).digest(),ControlledFixtureAudit.image(List.of("id_usuario"),b).digest());
    }
    @ParameterizedTest @ValueSource(strings={"acceso_administrar","Acceso_Administrar","donacion_registrar","DONACION_REGISTRAR "})
    void essentialPermissionCodesAreComparedExactlyInJava(String wrong) {
        List<String> codes=wrong.toLowerCase(Locale.ROOT).startsWith("acceso") ? List.of(wrong,"DONACION_REGISTRAR") : List.of("ACCESO_ADMINISTRAR",wrong);
        assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verifyMaster(1,"ACTIVO",codes));
    }
    @Test void masterRequiresExactlyOneMarkedActiveAccountAndBothPermissions() {
        assertDoesNotThrow(() -> ControlledFixtureAudit.verifyMaster(1,"ACTIVO",List.of("ACCESO_ADMINISTRAR","DONACION_REGISTRAR")));
        for (int count:List.of(0,2)) assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verifyMaster(count,"ACTIVO",List.of()));
        assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verifyMaster(1,"INACTIVO",List.of("ACCESO_ADMINISTRAR","DONACION_REGISTRAR")));
        assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verifyMaster(1,"ACTIVO",List.of("ACCESO_ADMINISTRAR")));
    }
    @Test void idAssignmentCommitAndRollbackHaveDistinctStatesAndPresenceRules() {
        FixtureLedger ledger=new FixtureLedger(); var preparation=ledger.begin("setup",Phase.PREPARATION);
        Key role=new Key("rol","101"); ledger.allocated(role,preparation);
        assertEquals(Set.of(role),ledger.keys(State.ASSIGNED)); assertTrue(ledger.keys(State.COMMITTED_BASE).isEmpty());
        assertThrows(AssertionError.class,ledger::requireSettled);
        ledger.complete(preparation,Outcome.COMMITTED); source.add("rol","101","codigo","IT");
        assertEquals(Set.of(role),ledger.keys(State.COMMITTED_BASE)); assertDoesNotThrow(() -> ControlledFixtureAudit.verifyAllocations(source.snapshot(),ledger));
        source.remove("rol","101"); assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verifyAllocations(source.snapshot(),ledger));
    }
    @Test void laterCommittedOperationsAreRequiredButRolledBackRowsMustBeAbsent() {
        FixtureLedger ledger=new FixtureLedger(); var committed=ledger.begin("operation",Phase.SCENARIO); Key operation=new Key("recepcion_operacion","115");
        ledger.allocated(operation,committed); ledger.complete(committed,Outcome.COMMITTED);
        assertEquals(Set.of(operation),ledger.keys(State.COMMITTED_OPERATION));
        source.add("recepcion_operacion","115","id_donacion","111");
        var rollback=ledger.begin("rejected",Phase.SCENARIO); Key rejected=new Key("movimiento_donacion","116");
        ledger.allocated(rejected,rollback); ledger.complete(rollback,Outcome.ROLLED_BACK);
        assertEquals(Set.of(rejected),ledger.keys(State.ROLLED_BACK));
        assertDoesNotThrow(() -> { ledger.requireSettled(); ControlledFixtureAudit.verifyAllocations(source.snapshot(),ledger); });
        source.add("movimiento_donacion","116","id_donacion","111");
        assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verifyAllocations(source.snapshot(),ledger));
    }
    @Test void aRolledBackPreparationDoesNotRequireItsAllocatedRowsAndUnknownOutcomesCannotPass() {
        FixtureLedger ledger=new FixtureLedger(); var tx=ledger.begin(null,Phase.PREPARATION); Key key=new Key("rol","101");
        ledger.allocated(key,tx); ledger.complete(tx,Outcome.ROLLED_BACK);
        assertDoesNotThrow(() -> ControlledFixtureAudit.verifyAllocations(source.snapshot(),ledger));
        assertThrows(AssertionError.class,() -> ledger.observedCommitted(key));
        assertThrows(AssertionError.class,() -> ledger.complete(tx,Outcome.COMMITTED));
        var unknown=ledger.begin("uncertain",Phase.SCENARIO);
        assertThrows(AssertionError.class,() -> ledger.complete(unknown,Outcome.UNKNOWN)); assertThrows(AssertionError.class,ledger::requireSettled);
    }
    @Test void observedOperationCommitDoesNotPretendToKnowUnreturnedAutoincrementIds() {
        FixtureLedger ledger=new FixtureLedger(); Key key=new Key("registro_auditoria","119");
        assertThrows(AssertionError.class,() -> ledger.observedCommitted(key));
        assertTrue(ledger.keys(State.COMMITTED_OPERATION).isEmpty());
    }
    @Test void aSiblingCommitMayBeVisibleBeforeItsAfterCommitCallbackWithoutLosingTheTransactionOutcome() {
        FixtureLedger ledger=new FixtureLedger(); var tx=ledger.begin("concurrent-operation",Phase.SCENARIO);
        Key key=new Key("movimiento_donacion","116"); ledger.allocated(key,tx);
        assertDoesNotThrow(() -> ledger.observedCommitted(key)); assertEquals(Set.of(key),ledger.keys(State.ASSIGNED));
        ledger.complete(tx,Outcome.COMMITTED); assertEquals(Set.of(key),ledger.keys(State.COMMITTED_OPERATION));
        FixtureLedger inconsistent=new FixtureLedger(); var rejected=inconsistent.begin("rejected",Phase.SCENARIO);
        inconsistent.allocated(key,rejected); inconsistent.observedCommitted(key);
        assertThrows(AssertionError.class,() -> inconsistent.complete(rejected,Outcome.ROLLED_BACK));
        assertThrows(AssertionError.class,inconsistent::requireSettled);
    }
    public interface SavedRepository { Saved saveAndFlush(Saved value); }
    public static final class Saved { private Long id; }
    @Test void repositoryObserverRecordsAssignedIdsEvenWhenFlushThrowsAndNoIdGetterExists() {
        FixtureLedger ledger=new FixtureLedger(); var tx=ledger.begin("save",Phase.SCENARIO);
        ProxyFactory factory=new ProxyFactory((SavedRepository) value -> { value.id=116L; throw new IllegalStateException("flush failure"); });
        factory.addAdvice(ControlledFixtureAudit.savedIdObserver("movimiento_donacion",key -> ledger.allocated(key,tx)));
        SavedRepository repository=(SavedRepository)factory.getProxy();
        assertThrows(IllegalStateException.class,() -> repository.saveAndFlush(new Saved()));
        assertEquals(Set.of(new Key("movimiento_donacion","116")),ledger.keys(State.ASSIGNED));
        ledger.complete(tx,Outcome.ROLLED_BACK);
        assertEquals(Set.of(new Key("movimiento_donacion","116")),ledger.keys(State.ROLLED_BACK));
        assertDoesNotThrow(() -> ControlledFixtureAudit.verifyAllocations(source.snapshot(),ledger));
    }
    @Test void repositoryObserverUsesTheSavedEntityIdAndWaitsForCommitBeforeRequiringItsRow() {
        FixtureLedger ledger=new FixtureLedger(); var tx=ledger.begin("save",Phase.SCENARIO);
        ProxyFactory factory=new ProxyFactory((SavedRepository) value -> { Saved saved=new Saved(); saved.id=117L; return saved; });
        factory.addAdvice(ControlledFixtureAudit.savedIdObserver("entrega",key -> ledger.allocated(key,tx)));
        ((SavedRepository)factory.getProxy()).saveAndFlush(new Saved());
        Key key=new Key("entrega","117"); assertEquals(Set.of(key),ledger.keys(State.ASSIGNED));
        assertDoesNotThrow(() -> ControlledFixtureAudit.verifyAllocations(source.snapshot(),ledger));
        ledger.complete(tx,Outcome.COMMITTED);
        assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verifyAllocations(source.snapshot(),ledger));
        source.add("entrega","117","id_usuario_responsable","102");
        assertDoesNotThrow(() -> ControlledFixtureAudit.verifyAllocations(source.snapshot(),ledger));
    }
    @Test void manifestCarriesOnlyIdsAndOutcomesNotPrivateFieldsOrHashes() {
        Snapshot before=source.snapshot(); fixture(); operations();
        String manifest=JsonMapper.builder().build().writeValueAsString(ControlledFixtureAudit.newRecords(before,source.snapshot()));
        assertTrue(manifest.contains("receipt-uuid")); assertTrue(manifest.contains("115"));
        assertFalse(manifest.contains("PRIVATE_")); assertFalse(manifest.contains("password")); assertFalse(manifest.contains("digest"));
    }
    FixtureLedger attributedLedger(boolean withOperations) {
        FixtureLedger ledger=new FixtureLedger("fixture-correlation/scenario");
        var preparation=ledger.begin("setup",Phase.PREPARATION);
        base.forEach(key -> ledger.allocated(key,preparation)); ledger.complete(preparation,Outcome.COMMITTED);
        if (withOperations) {
            var operation=ledger.begin("service-operation",Phase.SCENARIO);
            FixtureSchema.OPERATIONS.forEach(table -> source.rows.get(table).forEach(row -> ledger.allocated(new Key(table,row.key()),operation)));
            ledger.complete(operation,Outcome.COMMITTED);
        }
        return ledger;
    }
    ScenarioRelations expectedRelations() { return new ScenarioRelations(111,102,112,106,105,109,110,Set.of(113L,114L)); }
    @Test void foreignInsertionWithValidFixtureParentsIsRejectedWithoutAnObservedInsert() {
        Snapshot before=source.snapshot(); fixture(); FixtureLedger ledger=attributedLedger(false);
        source.add("movimiento_donacion","116","id_donacion","111","id_usuario_responsable","102");
        assertDoesNotThrow(() -> verify(before)); // FK ownership alone is insufficient.
        assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verifyProvenance(before,source.snapshot(),ledger));
        assertThrows(AssertionError.class,() -> ledger.observedCommitted(new Key("movimiento_donacion","116")));
        assertTrue(ledger.keys(State.COMMITTED_OPERATION).isEmpty());
    }
    @Test void attributedCommittedOperationsMustHaveTheirObservedImagesAndExactScenarioRelations() {
        Snapshot before=source.snapshot(); fixture(); operations(); FixtureLedger ledger=attributedLedger(true);
        Snapshot confirmed=source.snapshot(); Map<Key,RowImage> images=new HashMap<>();
        ledger.keys(State.COMMITTED_OPERATION).forEach(key -> images.put(key,confirmed.tables().get(key.table()).get(key.id())));
        assertDoesNotThrow(() -> {
            ControlledFixtureAudit.verifyProvenance(before,confirmed,ledger);
            expectedRelations().verify(before,confirmed,ledger);
            ControlledFixtureAudit.verifyOperationImages(confirmed,images,ledger.keys(State.COMMITTED_OPERATION));
        });
        assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verifyOperationImages(confirmed,Map.of(),ledger.keys(State.COMMITTED_OPERATION)));
        change("movimiento_donacion","116","id_usuario_responsable","103"); // A valid fixture parent, but the wrong actor.
        assertDoesNotThrow(() -> verify(before));
        assertThrows(AssertionError.class,() -> expectedRelations().verify(before,source.snapshot(),ledger));
        assertThrows(AssertionError.class,() -> ControlledFixtureAudit.verifyOperationImages(source.snapshot(),images,ledger.keys(State.COMMITTED_OPERATION)));
    }
    @ParameterizedTest @ValueSource(strings={"recepcion_operacion","traslado_recurso","entrega","detalle_entrega","registro_auditoria"})
    void wrongScenarioRelationsFailEvenWhenTheirParentsAreInTheFixture(String table) {
        Snapshot before=source.snapshot(); fixture(); operations(); FixtureLedger ledger=attributedLedger(true);
        switch (table) {
            case "recepcion_operacion" -> change(table,"115","id_usuario_decision","103");
            case "traslado_recurso" -> change(table,"transfer-uuid","id_usuario_recepcion","103");
            case "entrega" -> change(table,"117","id_usuario_responsable","103");
            case "detalle_entrega" -> {
                source.add("detalle_entrega","118","id_entrega","117","id_detalle_donacion","112","id_necesidad","114");
                assertThrows(AssertionError.class,() -> new ScenarioRelations(111,102,112,106,105,109,110,Set.of(113L)).verify(before,source.snapshot(),ledger));
                return;
            }
            case "registro_auditoria" -> change(table,"119","id_usuario","103");
        }
        assertDoesNotThrow(() -> verify(before));
        assertThrows(AssertionError.class,() -> expectedRelations().verify(before,source.snapshot(),ledger));
    }
    @Test void operationInsertImageAllowsOnlyTheExistingAuthorizedLaterUpdates() {
        fixture(); operations(); FixtureLedger ledger=attributedLedger(true); Snapshot confirmed=source.snapshot();
        Map<Key,RowImage> images=new HashMap<>();
        ledger.keys(State.COMMITTED_OPERATION).forEach(key -> images.put(key,confirmed.tables().get(key.table()).get(key.id())));
        change("recepcion_operacion","115","decision","APROBADO"); change("traslado_recurso","transfer-uuid","estado","RECIBIDO");
        assertDoesNotThrow(() -> ControlledFixtureAudit.verifyOperationImages(source.snapshot(),images,ledger.keys(State.COMMITTED_OPERATION)));
    }
}
