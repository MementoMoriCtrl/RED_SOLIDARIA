package pe.redsolidaria.test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import java.sql.Statement;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.util.ReflectionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import pe.redsolidaria.test.FixtureSchema.*;
import pe.redsolidaria.test.FixtureLedger.*;

/** Test-only SELECT audit. Existing rows are immutable; fixture mutations have an explicit policy. */
public final class ControlledFixtureAudit implements AutoCloseable {
    private static final IntegrityGate INTEGRITY=new IntegrityGate();
    private static final ThreadLocal<ControlledFixtureAudit> INSERT_OWNER=new ThreadLocal<>();
    record RowImage(String key,String digest,Map<String,String> references,Map<String,String> fields) {}
    record Snapshot(Map<String,Shape> shapes,Map<String,Map<String,RowImage>> tables) {}
    record ExistingReference(String childTable,String childKey,String column,String parentTable,String parentKey) {}
    interface Source {
        Set<String> tables();
        Shape shape(String table);
        long count(String table);
        List<RowImage> rows(String table,Shape shape);
    }
    private final JdbcTemplate jdbc;
    private final TransactionTemplate read;
    private final ConfigurableTransactionManager manager;
    private final String correlation,method;
    private final Snapshot baseline;
    private Snapshot checkpoint;
    private final FixtureLedger ledger;
    private final Set<ExistingReference> exceptions=new HashSet<>();
    private final Map<Key,RowImage> allocationImages=new HashMap<>();
    private Map<String,Set<String>> requiredExisting=Map.of();
    private final List<HistoricalReceiptSeed> historicalSeeds=new ArrayList<>();
    private final Set<Key> presentedMovements=new HashSet<>();
    record HistoricalReceiptSeed(Key key,String scenario,String transaction,int version,String operationId,
            long donation,long scenarioActor,long historicalActor,long collection) {}
    private final Set<Key> validatedAllocations=new HashSet<>();
    private final Map<TransactionExecution,FixtureLedger.Transaction> transactions=new IdentityHashMap<>();
    private final ThreadLocal<FixtureLedger.Transaction> current=new ThreadLocal<>();
    private final Map<Advised,List<MethodInterceptor>> allocationObservers=new IdentityHashMap<>();
    private final TransactionExecutionListener listener;
    private Phase phase=Phase.PREPARATION;
    private AssertionError callbackFailure;
    private boolean closed;
    private ScenarioRelations relations;
    private RegistrationScenarioRelations registration;
    private String rollbackTarget;
    private FixtureLedger.Transaction rollbackTransaction;
    private RegistrationRollbackEvidence rollbackEvidence;
    private boolean rollbackCertified;
    public static final class RegistrationRollbackEvidence {
        private final String correlation,transaction,target;
        private final Map<String,String> ids;
        private RegistrationRollbackEvidence(String correlation,String transaction,String target,Map<String,String> ids) {
            this.correlation=correlation; this.transaction=transaction; this.target=target; this.ids=Map.copyOf(ids);
        }
        public String correlation() { return correlation; }
        public String transaction() { return transaction; }
        public String target() { return target; }
        public Map<String,String> ids() { return ids; }
    }
    private final IntegrityGate gate;
    private final Initialization initialization;
    record Initialization(Runnable identity,java.util.function.Supplier<Snapshot> capture,Runnable master,
            java.util.function.Function<Key,RowImage> insertedImage,java.util.function.BiFunction<String,Object[],Long> jdbcInsert) {
        Initialization(Runnable identity,java.util.function.Supplier<Snapshot> capture,Runnable master) { this(identity,capture,master,null,null); }
        Initialization(Runnable identity,java.util.function.Supplier<Snapshot> capture,Runnable master,
                java.util.function.Function<Key,RowImage> image) { this(identity,capture,master,image,null); }
    }

    public ControlledFixtureAudit(JdbcTemplate template,PlatformTransactionManager tx,String correlation,String method) {
        this(template,tx,correlation,method,INTEGRITY,null);
    }
    ControlledFixtureAudit(JdbcTemplate template,PlatformTransactionManager tx,String correlation,String method,
            IntegrityGate gate,Initialization initialization) {
        this.gate=gate; this.initialization=initialization;
        String stage="identity";
        try {
        gate.requireSafe();
        if (initialization==null) ControlledTestMySqlCondition.verifyDataSource(template.getDataSource());
        else initialization.identity().run();
        stage="transaction-manager";
        if (!(tx instanceof ConfigurableTransactionManager configurable)) throw new AssertionError("Transaction outcome observation is required");
        manager=configurable;
        jdbc=new JdbcTemplate(Objects.requireNonNull(template.getDataSource())); jdbc.setQueryTimeout(5);
        read=new TransactionTemplate(tx); read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        stage="scenario-identity";
        this.correlation=UUID.fromString(correlation).toString(); this.method=method;
        ledger=new FixtureLedger(this.correlation+"/"+method);
        stage="capture";
        baseline=initialization==null ? capture() : initialization.capture().get(); checkpoint=baseline;
        stage="master";
        if (initialization==null) assertMasterHealthy(); else initialization.master().run();
        listener=new TransactionExecutionListener() {
            @Override public void beforeBegin(TransactionExecution execution) {
                if (!execution.isReadOnly()) gate.requireSafe();
            }
            @Override public void afterBegin(TransactionExecution execution,Throwable failure) {
                if (execution.isReadOnly() || failure!=null) return;
                synchronized (ControlledFixtureAudit.this) {
                    if (closed) return;
                    var transaction=ledger.begin(execution.getTransactionName(),phase);
                    if (current.get()!=null || INSERT_OWNER.get()!=null) throw new AssertionError("Nested write transactions are not supported");
                    transactions.put(execution,transaction); current.set(transaction); INSERT_OWNER.set(ControlledFixtureAudit.this);
                    if (rollbackTarget!=null && phase==Phase.SCENARIO) {
                        if (rollbackTransaction!=null || !"pe.redsolidaria.modules.donations.application.DonationRegistrationService.register".equals(transaction.name)) {
                            AssertionError unexpected=new AssertionError("Rollback probe requires exactly the real register() transaction");
                            failCallback(unexpected); throw unexpected;
                        }
                        rollbackTransaction=transaction;
                    }
                }
            }
            @Override public void afterCommit(TransactionExecution execution,Throwable failure) {
                completed(execution,failure==null ? Outcome.COMMITTED : Outcome.UNKNOWN);
            }
            @Override public void afterRollback(TransactionExecution execution,Throwable failure) {
                completed(execution,failure==null ? Outcome.ROLLED_BACK : Outcome.UNKNOWN);
            }
        };
        stage="listener";
        manager.addListener(listener);
        } catch (RuntimeException | Error failure) {
            gate.fail("audit-construction/"+stage,failure); throw failure;
        }
    }
    private synchronized void completed(TransactionExecution execution,Outcome outcome) {
        if (execution.isReadOnly() || closed) return;
        var transaction=transactions.remove(execution); current.remove(); INSERT_OWNER.remove();
        if (transaction==null) { failCallback(new AssertionError("Untracked write transaction completion")); return; }
        try {
            ledger.complete(transaction,outcome);
            // Observe committed rows after EACH write transaction, including operations from worker threads.
            // Read-only REQUIRES_NEW captures do not enter this listener's ledger.
            checkCurrent();
        } catch (RuntimeException | AssertionError failure) {
            // The commit may already be durable. Do not pretend a callback error rolled it back.
            failCallback(failure);
        }
    }
    private void failCallback(Throwable failure) {
        gate.fail("transaction-callback",failure);
        if (callbackFailure==null) callbackFailure=new AssertionError("Transaction integrity callback failed: "+
            (failure instanceof AssertionError ? failure.getMessage() : failure.getClass().getSimpleName()),failure);
    }
    public synchronized void register(String table,long id) {
        gate.requireSafe();
        if (phase!=Phase.PREPARATION || !FixtureSchema.BASE.contains(table)) throw new AssertionError("Unexpected base fixture allocation");
        String key=Long.toString(id);
        if (baseline.tables().get(table).containsKey(key)) throw new AssertionError("Fixture allocation overlaps a protected row");
        Key allocated=new Key(table,key);
        ledger.allocated(allocated,current.get());
        allocationImages.put(allocated,readImage(allocated));
    }
    public synchronized void requireExistingRows(Map<String,Set<String>> required) {
        gate.requireSafe();
        if (phase!=Phase.PREPARATION || !ledger.attributedKeys().isEmpty()) throw new AssertionError("Protected inventory must be checked before fixture creation");
        verifyRequiredRows(baseline,required);
        Map<String,Set<String>> frozen=new TreeMap<>(); required.forEach((table,ids) -> frozen.put(table,Set.copyOf(ids)));
        requiredExisting=Map.copyOf(frozen);
    }
    static void verifyRequiredRows(Snapshot snapshot,Map<String,Set<String>> required) {
        required.forEach((table,ids) -> {
            if (!snapshot.tables().containsKey(table) || !snapshot.tables().get(table).keySet().containsAll(ids))
                throw new AssertionError("Missing previously confirmed rows: "+table);
        });
    }
    private void requireWriteTransaction(Phase expected) {
        gate.requireSafe();
        if (closed || phase!=expected || current.get()==null || current.get().outcome!=Outcome.ACTIVE
                || !TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new AssertionError("Explicit controlled write transaction required before JDBC insertion");
    }
    public synchronized long insertJdbcBase(String sql,Object... args) {
        requireWriteTransaction(Phase.PREPARATION);
        var statement=java.util.regex.Pattern.compile("(?i)^\\s*insert\\s+into\\s+([a-z_]+)\\s*\\(").matcher(sql);
        if (!statement.find() || !FixtureSchema.BASE.contains(statement.group(1).toLowerCase(Locale.ROOT)))
            throw new AssertionError("Only explicit base fixture INSERTs are allowed during preparation");
        long id=generatedInsert(sql,args); register(statement.group(1).toLowerCase(Locale.ROOT),id); return id;
    }
    /** Only the two synthetic replay formats are admitted. All parents come from the frozen scenario. */
    public synchronized long insertHistoricalReceipt(UUID operationId,int version,String fingerprint,String response) {
        requireWriteTransaction(Phase.SCENARIO);
        if (relations==null || relations.registrar()==null || operationId==null || (version!=2 && version!=3)
                || fingerprint==null || !fingerprint.matches("[0-9a-f]{64}"))
            throw new AssertionError("Explicit historical receipt identity and scenario actors are required");
        relations.verify(baseline,checkpoint,ledger,presentedMovements);
        var document=JsonMapper.builder().build().readTree(response);
        if (!document.path("donacionId").isIntegralNumber() || document.path("donacionId").asLong()!=relations.donation()
                || !document.path("detalles").isArray() || document.path("detalles").isEmpty())
            throw new AssertionError("Historical response belongs to another donation or has no resources");
        for (var detail:document.path("detalles")) if (!detail.path("recursoId").isIntegralNumber()
                || !relations.permitsResource(detail.path("recursoId").asLong()))
            throw new AssertionError("Historical response contains an unauthorized resource");
        long id=generatedInsert("insert into recepcion_operacion(id_donacion,operacion_id,huella,respuesta) values (?,?,?,?)",
            new Object[]{relations.donation(),operationId.toString(),fingerprint,response});
        Key key=new Key("recepcion_operacion",Long.toString(id));
        insertedOperation(key);
        RowImage inserted=allocationImages.get(key);
        if (!operationId.toString().equals(inserted.references().get("operacion_id"))
                || !Long.toString(relations.donation()).equals(inserted.references().get("id_donacion")))
            throw new AssertionError("Historical INSERT did not return the expected generated row");
        historicalSeeds.add(new HistoricalReceiptSeed(key,correlation+"/"+method,current.get().id,version,operationId.toString(),
            relations.donation(),relations.actor(),relations.registrar(),relations.collection()));
        return id;
    }
    /** Exact synthetic registration event; never grants a general registrar exception. */
    public synchronized long insertPresentedDonationMovement() {
        try {
            requireWriteTransaction(Phase.SCENARIO);
            if (relations==null || relations.registrar()==null || !presentedMovements.isEmpty())
                throw new AssertionError("One presentation movement with an explicit registrar is required");
            relations.verify(baseline,checkpoint,ledger,presentedMovements);
            long id=generatedInsert("insert into movimiento_donacion(id_donacion,id_usuario_responsable,estado_nuevo,observaciones,tipo_evento) "
                +"select id_donacion,id_usuario_registro,estado,?,? from donacion where id_donacion=?",
                new Object[]{"Private synthetic observation","DONACION_PRESENTADA",relations.donation()});
            Key key=new Key("movimiento_donacion",Long.toString(id));
            insertedOperation(key);
            RowImage inserted=allocationImages.get(key);
            if (!Long.toString(relations.donation()).equals(inserted.references().get("id_donacion"))
                    || !Long.toString(relations.registrar()).equals(inserted.references().get("id_usuario_responsable"))
                    || !"DONACION_PRESENTADA".equals(inserted.references().get("tipo_evento")))
                throw new AssertionError("Generated presentation movement does not match its donation and registrar");
            presentedMovements.add(key);
            return id;
        } catch (RuntimeException | Error failure) { gate.fail("presentation-movement",failure); throw failure; }
    }
    private long generatedInsert(String sql,Object[] args) {
        if (initialization!=null) return Objects.requireNonNull(initialization.jdbcInsert().apply(sql,args));
        var keys=new GeneratedKeyHolder();
        int affected=jdbc.update(connection -> {
            var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for (int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);
            return statement;
        },keys);
        if (affected!=1 || keys.getKey()==null) throw new AssertionError("INSERT must return exactly one actual generated ID");
        return keys.getKey().longValue();
    }
    /** No exceptions are needed by PhaseOne: it creates every parent itself.
     * Other fixtures may explicitly reuse one protected parent for one exact child FK, before setup commits.
     * No wildcard permission and no permission to mutate that parent is granted.
     */
    public synchronized void allowExistingReference(String childTable,String childKey,String column,String parentKey) {
        gate.requireSafe();
        if (phase!=Phase.PREPARATION) throw new AssertionError("Relation policy is frozen after preparation");
        if (!ledger.keys(State.ASSIGNED).contains(new Key(childTable,childKey))) throw new AssertionError("Exception requires an assigned child in the current fixture");
        Shape shape=baseline.shapes().get(childTable);
        ForeignKey fk=shape.foreignKeys().stream().filter(f -> f.column().equals(column)).findFirst().orElseThrow();
        ExistingReference allowed=new ExistingReference(childTable,childKey,column,fk.table(),parentKey);
        validateException(baseline,allowed); exceptions.add(allowed);
    }
    public synchronized void observeTransferIds(Object repository) {
        observe(repository,invocation -> {
            if (invocation.getMethod().getName().equals("dispatch") && invocation.getArguments().length>0
                    && invocation.getArguments()[0] instanceof pe.redsolidaria.modules.logistics.LogisticsDtos.Dispatch request)
            {
                Object result=invocation.proceed();
                insertedOperation(new Key("traslado_recurso",request.operacionId().toString()));
                return result;
            }
            return invocation.proceed();
        });
    }
    private void observe(Object repository,MethodInterceptor observer) {
        if (!(repository instanceof Advised proxy)) throw new AssertionError("A real observable repository proxy is required before fixture creation");
        proxy.addAdvice(0,observer); allocationObservers.computeIfAbsent(proxy,p -> new ArrayList<>()).add(observer);
    }
    static MethodInterceptor savedIdObserver(String table,Consumer<Key> assigned) {
        return invocation -> {
            if (!invocation.getMethod().getName().equals("saveAndFlush") || invocation.getArguments().length!=1) return invocation.proceed();
            Object entity=invocation.getArguments()[0]; Object saved=null;
            try { saved=invocation.proceed(); return saved; }
            finally {
                Object bean=saved==null ? entity : saved;
                var field=ReflectionUtils.findField(bean.getClass(),"id");
                if (field==null) throw new AssertionError("Cannot observe the mapped identifier of the saved test entity");
                ReflectionUtils.makeAccessible(field); Object id=ReflectionUtils.getField(field,bean);
                if (id!=null) assigned.accept(new Key(table,id.toString()));
            }
        };
    }
    private synchronized void allocatedOperation(Key key) {
        gate.requireSafe();
        if (closed || phase!=Phase.SCENARIO || baseline.tables().get(key.table()).containsKey(key.id()))
            throw new AssertionError("Unexpected operation allocation or protected row reuse");
        ledger.allocated(key,current.get());
    }
    static void insertObservationFailed(Throwable failure) {
        ControlledFixtureAudit owner=INSERT_OWNER.get();
        (owner==null ? INTEGRITY : owner.gate).fail("insert-observation",failure);
    }
    static void hibernateInserted(String table,String id) {
        ControlledFixtureAudit owner=INSERT_OWNER.get();
        if (owner==null) {
            AssertionError failure=new AssertionError("Hibernate insert without a controlled write transaction");
            INTEGRITY.fail("insert-provenance",failure); throw failure;
        }
        owner.insertedOperation(new Key(table,id));
    }
    private synchronized void insertedOperation(Key key) {
        try {
            if (registration==null ? !FixtureSchema.OPERATIONS.contains(key.table()) : !registration.permits(key.table()))
                throw new AssertionError("Unexpected inserted entity table: "+key.table());
            requireWriteTransaction(Phase.SCENARIO);
            allocatedOperation(key);
            RowImage inserted=readImage(key);
            allocationImages.put(key,inserted);
            if (registration!=null) registration.observe(key,inserted,current.get());
        } catch (RuntimeException | Error failure) { gate.fail("insert-provenance",failure); throw failure; }
    }
    private RowImage readImage(Key key) {
        if (initialization!=null) return Objects.requireNonNull(initialization.insertedImage().apply(key));
        Shape shape=baseline.shapes().get(key.table());
        return Objects.requireNonNull(jdbc.queryForObject("SELECT * FROM `"+key.table()+"` WHERE `"+shape.primaryKey().getFirst()+"`=?",(row,n) -> {
            Map<String,String> values=new TreeMap<>();
            for (int i=1;i<=row.getMetaData().getColumnCount();i++) values.put(row.getMetaData().getColumnName(i),row.getString(i));
            return image(shape.primaryKey(),values);
        },key.id()));
    }
    public synchronized void authorizeRelations(ScenarioRelations expected) {
        if (phase!=Phase.PREPARATION || relations!=null || registration!=null) throw new AssertionError("Scenario relations are already frozen");
        relations=Objects.requireNonNull(expected);
    }
    public synchronized void authorizeRegistration(RegistrationScenarioRelations expected) {
        try {
            gate.requireSafe();
            if (phase!=Phase.PREPARATION || relations!=null || registration!=null)
                throw new AssertionError("Scenario relations are already frozen");
            Objects.requireNonNull(expected).claim(); registration=expected;
        } catch (RuntimeException | Error failure) { gate.fail("registration-policy",failure); throw failure; }
    }
    public synchronized Map<String,String> requireCommittedRegistration() {
        try {
            requireReadyForScenario(); checkCurrent();
            if (registration==null) throw new AssertionError("Explicit registration policy required");
            return registration.confirmed(ledger);
        } catch (RuntimeException | Error failure) { gate.fail("registration-confirmation",failure); throw failure; }
    }
    synchronized String armRegistrationRollback(String target) {
        try {
            requireReadyForScenario();
            if (registration==null || rollbackTarget!=null || TransactionSynchronizationManager.isActualTransactionActive()
                    || !Set.of("registro_auditoria","movimiento_donacion").contains(target))
                throw new AssertionError("Exclusive registration rollback probe required outside a transaction");
            rollbackTarget=target; return correlation;
        } catch (RuntimeException | Error failure) { atomicityFailure(failure); throw failure; }
    }
    synchronized RegistrationRollbackEvidence captureRegistrationRollback(String expectedCorrelation,String target,String id) {
        try {
            requireWriteTransaction(Phase.SCENARIO);
            if (INSERT_OWNER.get()!=this || registration==null || rollbackEvidence!=null || !correlation.equals(expectedCorrelation)
                    || !Objects.equals(rollbackTarget,target) || current.get()!=rollbackTransaction)
                throw new AssertionError("Rollback fault does not match its armed scenario and transaction");
            Map<String,String> ids=registration.pendingRollback(target,current.get(),ledger);
            if (!id.equals(ids.get(target))) throw new AssertionError("Fault event PK was not observed by the controlled listener");
            for (Key key:registration.observedKeys()) {
                RowImage actual=readImage(key),initial=allocationImages.get(key);
                if (initial==null || !initial.digest().equals(actual.digest()))
                    throw new AssertionError("Registration changed before the deliberate fault");
            }
            rollbackEvidence=new RegistrationRollbackEvidence(correlation,current.get().id,target,ids);
            return rollbackEvidence;
        } catch (RuntimeException | Error failure) { atomicityFailure(failure); throw failure; }
    }
    public synchronized Map<String,String> requireRolledBackRegistration(RegistrationRollbackEvidence evidence) {
        try {
            requireReadyForScenario();
            if (evidence==null || evidence!=rollbackEvidence || registration==null || rollbackCertified)
                throw new AssertionError("Exact one-use rollback evidence required");
            ledger.requireSingleScenarioRollback(rollbackTransaction,registration.observedKeys());
            checkCurrent(); // A new read-only REQUIRES_NEW capture checks all 21 protected tables and the master.
            registration.verifyRollbackAbsence(checkpoint,evidence.ids());
            rollbackCertified=true;
            System.out.println("CONTROLLED_REGISTRATION_ROLLBACK "+JsonMapper.builder().build().writeValueAsString(Map.of(
                "correlation",correlation,"method",method,"transaction",evidence.transaction(),"failureTable",evidence.target(),
                "identities",evidence.ids(),"outcome","ROLLED_BACK","absenceVerified",true)));
            return evidence.ids();
        } catch (RuntimeException | Error failure) { atomicityFailure(failure); throw failure; }
    }
    public synchronized void requireTransactionalRegistrationTables() {
        try {
            requireReadyForScenario();
            Objects.requireNonNull(read.execute(status -> {
                List<Map<String,Object>> engines=jdbc.queryForList("SELECT TABLE_NAME, ENGINE FROM INFORMATION_SCHEMA.TABLES "
                    +"WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('donacion','detalle_donacion','movimiento_donacion','registro_auditoria')");
                if (engines.size()!=4 || engines.stream().anyMatch(row -> !"InnoDB".equalsIgnoreCase(Objects.toString(row.get("ENGINE")))))
                    throw new AssertionError("All registration tables must use InnoDB");
                return Boolean.TRUE;
            }));
        } catch (RuntimeException | Error failure) { atomicityFailure(failure); throw failure; }
    }
    void atomicityFailure(Throwable failure) { gate.fail("registration-atomicity",failure); }
    public synchronized void requireReadyForScenario() {
        try {
            gate.requireSafe(); rethrowCallbackFailure(); ledger.requireSettled();
            if (closed || phase!=Phase.SCENARIO) throw new AssertionError("Scenario is not ready");
        } catch (RuntimeException | Error failure) { gate.fail("scenario-readiness",failure); throw failure; }
    }
    public synchronized void announce() {
        try {
            if (relations==null && registration==null) throw new AssertionError("Explicit scenario relations are required");
            rethrowCallbackFailure(); ledger.requireSettled(); checkCurrent(); phase=Phase.SCENARIO;
            printManifest("preparation-confirmed");
        } catch (RuntimeException | AssertionError failure) { gate.fail("announce",failure); throw failure; }
    }
    public synchronized void finish() {
        try {
            gate.requireSafe();
            if (rollbackTarget!=null && !rollbackCertified) throw new AssertionError("Armed rollback scenario was not certified");
            rethrowCallbackFailure(); ledger.requireSettled(); checkCurrent();
            printManifest("integrity-verified");
            System.out.println("CONTROLLED_FIXTURE_INTEGRITY correlation="+correlation+" existingRowsUnchanged=true masterHealthy=true");
        } catch (RuntimeException | AssertionError failure) {
            gate.fail("finish",failure); printManifest("failed-or-indeterminate"); throw failure;
        } finally { close(); }
    }
    private void rethrowCallbackFailure() { if (callbackFailure!=null) throw callbackFailure; }
    private void checkCurrent() {
        if (initialization==null) ControlledTestMySqlCondition.verifyDataSource(jdbc.getDataSource());
        else initialization.identity().run();
        Snapshot after=capture();
        verifyProvenance(baseline,after,ledger);
        verify(baseline,after,ledger.keys(State.COMMITTED_BASE),exceptions,registration==null ? Set.of() : registration.observedKeys());
        verifyCheckpoint(baseline,checkpoint,after);
        verifyAllocations(after,ledger);
        if (relations!=null) relations.verify(baseline,after,ledger,presentedMovements);
        if (registration!=null) registration.verify(after,ledger);
        verifyOperationImages(after,allocationImages,ledger.keys(State.COMMITTED_OPERATION));
        Set<Key> newlyCommitted=new HashSet<>(ledger.keys(State.COMMITTED_BASE)); newlyCommitted.removeAll(validatedAllocations);
        verifyAllocatedImages(after,allocationImages,newlyCommitted);
        validatedAllocations.addAll(newlyCommitted);
        assertMasterHealthy();
        for (var table:additions(baseline,after).entrySet()) for (String key:table.getValue().keySet())
            if (!ledger.keys(State.COMMITTED_BASE).contains(new Key(table.getKey(),key)))
                ledger.observedCommitted(new Key(table.getKey(),key));
        checkpoint=after;
    }
    private void printManifest(String stage) {
        System.out.println("CONTROLLED_FIXTURE_MANIFEST "+JsonMapper.builder().build().writeValueAsString(Map.of(
            "correlation",correlation,"method",method,"database","red_solidaria_test","mysqlUser","rs_test@127.0.0.1",
            "stage",stage,"ledger",ledger.manifest(),"allowedExistingReferences",exceptions,
            "confirmedRecords",newRecords(baseline,checkpoint),"requiredExistingRows",requiredExisting,"historicalReceiptSeeds",historicalSeeds)));
    }
    @Override public synchronized void close() {
        if (closed) return; closed=true;
        var listeners=new ArrayList<>(manager.getTransactionExecutionListeners()); listeners.remove(listener);
        manager.setTransactionExecutionListeners(listeners); current.remove(); INSERT_OWNER.remove();
        allocationObservers.forEach((proxy,observers) -> observers.forEach(proxy::removeAdvice)); allocationObservers.clear();
    }
    public static void assertSafeToStart() {
        INTEGRITY.requireSafe();
    }
    public static <T> T initializeScenario(java.util.function.Supplier<T> initialization) {
        return INTEGRITY.initialize("scenario-initialization",initialization);
    }
    public static void blockFurtherScenarios(String stage,Throwable failure) { INTEGRITY.fail(stage,failure); }
    private void assertMasterHealthy() {
        if (initialization!=null) { initialization.master().run(); return; }
        List<Long> masters=jdbc.queryForList("SELECT id_usuario FROM usuario WHERE admin_maestro=1",Long.class);
        String state=masters.size()==1 ? jdbc.queryForObject("SELECT estado FROM usuario WHERE id_usuario=?",String.class,masters.getFirst()) : null;
        List<String> permissions=masters.size()==1 ? jdbc.queryForList("""
            SELECT p.codigo FROM usuario u JOIN rol_permiso rp ON rp.id_rol=u.id_rol
            JOIN permiso p ON p.id_permiso=rp.id_permiso WHERE u.id_usuario=?
            """,String.class,masters.getFirst()) : List.of();
        verifyMaster(masters.size(),state,permissions);
    }
    static void verifyMaster(int markedAccounts,String state,Collection<String> permissions) {
        if (markedAccounts!=1 || !"ACTIVO".equals(state)) throw new AssertionError("Requires exactly one active account marked admin_maestro=1");
        if (!permissions.contains("ACCESO_ADMINISTRAR") || !permissions.contains("DONACION_REGISTRAR"))
            throw new AssertionError("Exact master essential permission codes are required");
    }
    private Snapshot capture() {
        if (initialization!=null) return Objects.requireNonNull(initialization.capture().get());
        return Objects.requireNonNull(read.execute(status -> capture(new JdbcSource())));
    }
    static Snapshot capture(Source source) {
        Set<String> tables=source.tables();
        if (!tables.containsAll(FixtureSchema.EXPECTED.keySet())) throw new AssertionError("Missing protected tables; capture is not complete");
        Map<String,Shape> shapes=new TreeMap<>(); Map<String,Map<String,RowImage>> data=new TreeMap<>();
        for (String table:new TreeSet<>(tables)) {
            identifier(table); Shape shape=source.shape(table); validateShape(table,shape); shapes.put(table,shape);
            long expected=source.count(table);
            List<RowImage> rows=source.rows(table,shape);
            Map<String,RowImage> keyed=new TreeMap<>();
            for (RowImage row:rows) if (keyed.putIfAbsent(row.key(),row)!=null) throw new AssertionError("Duplicate captured primary key: "+table);
            if (expected!=rows.size() || expected!=keyed.size() || expected!=source.count(table))
                throw new AssertionError("Incomplete table capture: "+table);
            data.put(table,Map.copyOf(keyed));
        }
        return new Snapshot(Map.copyOf(shapes),Map.copyOf(data));
    }
    static void validateShape(String table,Shape actual) {
        if (actual.primaryKey().isEmpty()) throw new AssertionError("Protected table has no primary key: "+table);
        actual.primaryKey().forEach(ControlledFixtureAudit::identifier);
        Shape expected=FixtureSchema.EXPECTED.get(table);
        if (expected!=null && (!expected.primaryKey().equals(actual.primaryKey())
                || !new HashSet<>(expected.foreignKeys()).equals(new HashSet<>(actual.foreignKeys()))))
            throw new AssertionError("Unexpected PK/FK structure: "+table);
    }
    private final class JdbcSource implements Source {
        @Override public Set<String> tables() {
            return new HashSet<>(jdbc.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'",String.class));
        }
        @Override public Shape shape(String table) {
            List<String> pk=jdbc.queryForList("""
                SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME='PRIMARY' ORDER BY ORDINAL_POSITION
                """,String.class,table);
            List<ForeignKey> fks=jdbc.query("""
                SELECT k.COLUMN_NAME,k.REFERENCED_TABLE_SCHEMA,k.REFERENCED_TABLE_NAME,k.REFERENCED_COLUMN_NAME,c.IS_NULLABLE
                FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE k JOIN INFORMATION_SCHEMA.COLUMNS c
                ON c.TABLE_SCHEMA=k.TABLE_SCHEMA AND c.TABLE_NAME=k.TABLE_NAME AND c.COLUMN_NAME=k.COLUMN_NAME
                WHERE k.TABLE_SCHEMA=DATABASE() AND k.TABLE_NAME=? AND k.REFERENCED_TABLE_NAME IS NOT NULL
                """,(row,n) -> {
                    if (!"red_solidaria_test".equals(row.getString("REFERENCED_TABLE_SCHEMA"))) throw new AssertionError("Cross-schema fixture FK forbidden");
                    return new ForeignKey(row.getString("COLUMN_NAME"),row.getString("REFERENCED_TABLE_NAME"),row.getString("REFERENCED_COLUMN_NAME"),"YES".equals(row.getString("IS_NULLABLE")));
                },table);
            return new Shape(List.copyOf(pk),List.copyOf(fks));
        }
        @Override public long count(String table) { return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM `"+table+"`",Long.class)); }
        @Override public List<RowImage> rows(String table,Shape shape) {
            return jdbc.query("SELECT * FROM `"+table+"` ORDER BY "+String.join(",",shape.primaryKey().stream().map(k -> "`"+k+"`").toList()),(row,n) -> {
                Map<String,String> fields=new TreeMap<>();
                for (int i=1;i<=row.getMetaData().getColumnCount();i++) fields.put(row.getMetaData().getColumnName(i),row.getString(i));
                return image(shape.primaryKey(),fields);
            });
        }
    }
    static RowImage image(List<String> primaryKey,Map<String,String> values) {
        Map<String,String> fields=new TreeMap<>(),references=new TreeMap<>(); MessageDigest digest=sha256();
        for (var column:new TreeMap<>(values).entrySet()) {
            byte[] bytes=encoded(column.getValue());
            digest.update(encoded(column.getKey())); digest.update(bytes);
            fields.put(column.getKey(),HexFormat.of().formatHex(sha256().digest(bytes)));
            if (safeReference(column.getKey()) && column.getValue()!=null) references.put(column.getKey(),column.getValue());
        }
        List<String> keys=primaryKey.stream().map(values::get).toList();
        if (keys.stream().anyMatch(Objects::isNull)) throw new AssertionError("Captured primary key is incomplete");
        String key=keys.size()==1 ? keys.getFirst() : JsonMapper.builder().build().writeValueAsString(keys);
        return new RowImage(key,HexFormat.of().formatHex(digest.digest()),Map.copyOf(references),Map.copyOf(fields));
    }
    private static byte[] encoded(String value) {
        byte[] bytes=value==null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(4+bytes.length).putInt(value==null ? -1 : bytes.length).put(bytes).array();
    }
    private static boolean safeReference(String column) {
        return column.startsWith("id_") || Set.of("operacion_id","uuid_decision","operacion_recepcion","entidad","accion","tipo_evento","installed_rank").contains(column);
    }
    private static void identifier(String value) { if (!value.matches("[a-zA-Z0-9_]+")) throw new AssertionError("Unsupported SQL identifier"); }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static void verify(Snapshot before,Snapshot after,Set<Key> confirmedBase,Set<ExistingReference> exceptions) {
        verify(before,after,confirmedBase,exceptions,Set.of());
    }
    private static void verify(Snapshot before,Snapshot after,Set<Key> confirmedBase,Set<ExistingReference> exceptions,Set<Key> registrationKeys) {
        if (!before.shapes().equals(after.shapes()) || !before.tables().keySet().equals(after.tables().keySet())) throw new AssertionError("Protected schema/table coverage changed");
        before.tables().forEach((table,rows) -> rows.forEach((key,row) -> {
            RowImage current=after.tables().get(table).get(key);
            if (current==null || !row.digest().equals(current.digest())) throw new AssertionError("Existing row changed or disappeared: "+table+"/"+key);
        }));
        for (Key key:confirmedBase) {
            if (!FixtureSchema.BASE.contains(key.table()) || before.tables().get(key.table()).containsKey(key.id())) throw new AssertionError("Unsafe base registration");
            if (!after.tables().get(key.table()).containsKey(key.id())) throw new AssertionError("Confirmed base row disappeared: "+key);
        }
        exceptions.forEach(e -> validateException(before,e));
        Map<String,Map<String,RowImage>> added=additions(before,after);
        for (var table:added.entrySet()) for (RowImage row:table.getValue().values()) {
            Key key=new Key(table.getKey(),row.key());
            if (!confirmedBase.contains(key) && !FixtureSchema.OPERATIONS.contains(key.table()) && !registrationKeys.contains(key)) throw new AssertionError("Unregistered new row: "+key);
            for (ForeignKey fk:after.shapes().get(key.table()).foreignKeys()) {
                String value=row.references().get(fk.column());
                if (value==null) { if (!fk.nullable()) throw new AssertionError("Missing mandatory fixture FK: "+fk.column()); continue; }
                RowImage parent=after.tables().getOrDefault(fk.table(),Map.of()).get(value);
                if (parent==null) throw new AssertionError("Nonexistent FK target: "+key+"/"+fk.column());
                if (before.tables().get(fk.table()).containsKey(value)
                        && !exceptions.contains(new ExistingReference(key.table(),key.id(),fk.column(),fk.table(),value)))
                    throw new AssertionError("Unauthorized reference to a protected parent: "+key+"/"+fk.column());
                if (!after.shapes().get(fk.table()).primaryKey().equals(List.of(fk.parentColumn())))
                    throw new AssertionError("Unsupported referenced key; no implicit ownership allowed");
            }
            if (key.table().equals("registro_auditoria")) {
                String target=switch(row.references().getOrDefault("entidad","")) {
                    case "DONACION" -> "donacion"; case "TRASLADO" -> "traslado_recurso"; case "ENTREGA" -> "entrega"; default -> "";
                };
                if (!added.getOrDefault(target,Map.of()).containsKey(row.references().getOrDefault("id_registro","")))
                    throw new AssertionError("Audit target is outside the fixture");
            }
            if (key.table().equals("entrega") && added.getOrDefault("detalle_entrega",Map.of()).values().stream()
                    .noneMatch(detail -> key.id().equals(detail.references().get("id_entrega")))) throw new AssertionError("Orphan fixture delivery");
        }
    }
    private static void validateException(Snapshot before,ExistingReference allowed) {
        Shape shape=before.shapes().get(allowed.childTable());
        if (shape==null || shape.foreignKeys().stream().noneMatch(f -> f.column().equals(allowed.column()) && f.table().equals(allowed.parentTable()))
                || !before.tables().getOrDefault(allowed.parentTable(),Map.of()).containsKey(allowed.parentKey())
                || before.tables().get(allowed.childTable()).containsKey(allowed.childKey()))
            throw new AssertionError("Invalid explicit existing-parent exception");
    }
    static void verifyCheckpoint(Snapshot baseline,Snapshot checkpoint,Snapshot after) {
        for (var table:additions(baseline,checkpoint).entrySet()) for (RowImage previous:table.getValue().values()) {
            RowImage current=after.tables().get(table.getKey()).get(previous.key());
            if (current==null) throw new AssertionError("Previously committed fixture row disappeared: "+table.getKey()+"/"+previous.key());
            Set<String> mutable=FixtureSchema.MUTABLE.getOrDefault(table.getKey(),Set.of());
            if (!previous.fields().keySet().equals(current.fields().keySet())) throw new AssertionError("Fixture column coverage changed");
            for (String column:previous.fields().keySet()) if (!mutable.contains(column) && !previous.fields().get(column).equals(current.fields().get(column)))
                throw new AssertionError("Unauthorized fixture mutation: "+table.getKey()+"/"+previous.key()+"/"+column);
        }
    }
    static void verifyAllocations(Snapshot after,FixtureLedger ledger) {
        for (State state:List.of(State.COMMITTED_BASE,State.COMMITTED_OPERATION,State.ROLLED_BACK)) for (Key key:ledger.keys(state)) {
            boolean exists=after.tables().getOrDefault(key.table(),Map.of()).containsKey(key.id());
            if (exists!=(state!=State.ROLLED_BACK)) throw new AssertionError("Row contradicts transaction outcome: "+key+"/"+state);
        }
    }
    static void verifyProvenance(Snapshot before,Snapshot after,FixtureLedger ledger) {
        Set<Key> attributed=ledger.attributedKeys();
        additions(before,after).forEach((table,rows) -> rows.keySet().forEach(id -> {
            if (!attributed.contains(new Key(table,id))) throw new AssertionError("New row without transactional provenance: "+table+"/"+id);
        }));
    }
    static void verifyOperationImages(Snapshot after,Map<Key,RowImage> assigned,Set<Key> committed) {
        for (Key key:committed) {
            RowImage initial=assigned.get(key),actual=after.tables().getOrDefault(key.table(),Map.of()).get(key.id());
            if (initial==null || actual==null || !initial.fields().keySet().equals(actual.fields().keySet()))
                throw new AssertionError("Missing operation insert image: "+key);
            for (String column:initial.fields().keySet()) if (!FixtureSchema.MUTABLE.getOrDefault(key.table(),Set.of()).contains(column)
                    && !initial.fields().get(column).equals(actual.fields().get(column)))
                throw new AssertionError("Operation changed since observed insertion: "+key+"/"+column);
        }
    }
    static void verifyAllocatedImages(Snapshot after,Map<Key,RowImage> assigned,Set<Key> newlyCommitted) {
        for (Key key:newlyCommitted) {
            RowImage initial=assigned.get(key),current=after.tables().getOrDefault(key.table(),Map.of()).get(key.id());
            if (initial==null || current==null || !initial.digest().equals(current.digest()))
                throw new AssertionError("Base row changed between ID allocation and preparation commit: "+key);
        }
    }
    private static Map<String,Map<String,RowImage>> additions(Snapshot before,Snapshot after) {
        Map<String,Map<String,RowImage>> added=new TreeMap<>();
        after.tables().forEach((table,rows) -> {
            Map<String,RowImage> newRows=new TreeMap<>();
            rows.forEach((key,row) -> { if (!before.tables().getOrDefault(table,Map.of()).containsKey(key)) newRows.put(key,row); }); added.put(table,newRows);
        }); return added;
    }
    static Map<String,List<Map<String,String>>> newRecords(Snapshot before,Snapshot after) {
        Map<String,List<Map<String,String>>> manifest=new TreeMap<>();
        additions(before,after).forEach((table,rows) -> {
            if (!rows.isEmpty()) manifest.put(table,rows.values().stream().map(row -> {
                Map<String,String> fields=new TreeMap<>(); fields.put("primaryKey",row.key()); fields.putAll(row.references()); return fields;
            }).toList());
        }); return manifest;
    }
}
