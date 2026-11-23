package pe.redsolidaria.test;

import java.util.*;

/** Pure state machine. A generated ID remains an attempt until the transaction reports its outcome. */
final class FixtureLedger {
    enum Phase { PREPARATION, SCENARIO }
    enum State { ASSIGNED, COMMITTED_BASE, COMMITTED_OPERATION, ROLLED_BACK }
    enum Outcome { ACTIVE, COMMITTED, ROLLED_BACK, UNKNOWN }
    record Key(String table,String id) {}
    record Entry(Key key,String transaction,State state) {}
    private final String scenario;
    FixtureLedger() { this("isolated-unit-test"); }
    FixtureLedger(String scenario) { this.scenario=Objects.requireNonNull(scenario); }
    static final class Transaction {
        final String id=UUID.randomUUID().toString(),name;
        final Phase phase;
        Outcome outcome=Outcome.ACTIVE;
        Transaction(String name,Phase phase) { this.name=name==null || name.isBlank() ? "unnamed-test-transaction" : name; this.phase=phase; }
    }
    private final Map<String,Transaction> transactions=new LinkedHashMap<>();
    private final Map<Key,Entry> entries=new LinkedHashMap<>();
    private final Set<Key> observedDurable=new HashSet<>();
    synchronized Transaction begin(String name,Phase phase) {
        var transaction=new Transaction(name,phase); transactions.put(transaction.id,transaction); return transaction;
    }
    synchronized void allocated(Key key,Transaction transaction) {
        if (transaction==null || transaction.outcome!=Outcome.ACTIVE || transactions.get(transaction.id)!=transaction)
            throw new AssertionError("Generated ID without a tracked active transaction");
        if (entries.putIfAbsent(key,new Entry(key,transaction.id,State.ASSIGNED))!=null)
            throw new AssertionError("Fixture ID allocated more than once: "+key);
    }
    synchronized void complete(Transaction transaction,Outcome outcome) {
        if (transaction.outcome!=Outcome.ACTIVE || outcome==Outcome.ACTIVE) throw new AssertionError("Invalid transaction transition");
        transaction.outcome=outcome;
        if (outcome==Outcome.UNKNOWN) throw new AssertionError("Transaction outcome unknown; integrity cannot be certified");
        if (outcome==Outcome.ROLLED_BACK && entries.values().stream().anyMatch(e -> e.transaction().equals(transaction.id) && observedDurable.contains(e.key()))) {
            transaction.outcome=Outcome.UNKNOWN;
            throw new AssertionError("Rollback contradicts a row already visible in a committed snapshot");
        }
        entries.replaceAll((key,entry) -> entry.transaction().equals(transaction.id)
            ? new Entry(key,entry.transaction(),outcome==Outcome.ROLLED_BACK ? State.ROLLED_BACK
                : transaction.phase==Phase.PREPARATION ? State.COMMITTED_BASE : State.COMMITTED_OPERATION) : entry);
    }
    synchronized void observedCommitted(Key key) {
        Entry entry=entries.get(key);
        if (entry==null) throw new AssertionError("Committed row has no transactional provenance: "+key);
        if (entry.state()==State.ROLLED_BACK)
            throw new AssertionError("Observed row conflicts with its recorded transaction outcome: "+key);
        // A sibling commit can be visible before its serialized afterCommit callback acquires the audit lock.
        // Preserve ASSIGNED until that callback runs, but record the independent committed-read evidence.
        observedDurable.add(key);
    }
    synchronized Set<Key> keys(State state) {
        Set<Key> keys=new HashSet<>(); entries.forEach((key,entry) -> { if (entry.state()==state) keys.add(key); }); return Set.copyOf(keys);
    }
    synchronized Set<Key> attributedKeys() { return Set.copyOf(entries.keySet()); }
    synchronized void requireSettled() {
        if (transactions.values().stream().anyMatch(t -> t.outcome==Outcome.ACTIVE || t.outcome==Outcome.UNKNOWN))
            throw new AssertionError("Active or uncertain transactions remain");
    }
    synchronized void requireSingleScenarioRollback(Transaction expected,Set<Key> expectedKeys) {
        requireSettled();
        if (expected==null || transactions.get(expected.id)!=expected || expected.phase!=Phase.SCENARIO
                || expected.outcome!=Outcome.ROLLED_BACK
                || transactions.values().stream().filter(tx -> tx.phase==Phase.SCENARIO).count()!=1
                || !keys(State.ROLLED_BACK).equals(expectedKeys) || !keys(State.COMMITTED_OPERATION).isEmpty()
                || !keys(State.ASSIGNED).isEmpty())
            throw new AssertionError("Exact successful rollback callback required for the only scenario transaction");
    }
    synchronized Map<String,Object> manifest() {
        return Map.of("scenario",scenario,"rows",List.copyOf(entries.values()),"observedCommitted",Set.copyOf(observedDurable),"transactions",transactions.values().stream().map(t ->
            Map.of("id",t.id,"name",t.name,"phase",t.phase,"outcome",t.outcome)).toList());
    }
}
