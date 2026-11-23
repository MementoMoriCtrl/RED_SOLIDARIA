package pe.redsolidaria.test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/** Frozen identities and provenance, not historical checksums of database column values. No database access. */
public final class PhaseOneConfirmedRows {
    private PhaseOneConfirmedRows() {}
    public enum Origin { PHASE_ONE, MODEL_B }
    public enum State { COMMITTED_BASE, COMMITTED_OPERATION }
    public record Entry(String table,String pk,State state,Origin suite,String scenario,String correlation,String transaction) {
        public Entry {
            require(FixtureSchema.EXPECTED.containsKey(table),"Unknown inventory table");
            text(pk,"pk"); text(scenario,"scenario"); uuid(correlation,"correlation"); uuid(transaction,"transaction");
            require(state!=null && suite!=null,"Missing state or suite");
            require((state==State.COMMITTED_BASE ? FixtureSchema.BASE : FixtureSchema.OPERATIONS).contains(table),
                "Table does not match committed state");
        }
    }
    private record Key(String table,String pk) {}
    private record Source(String suite,String xmlSha256,String summarySha256) {}
    private static final String EVIDENCE_SHA256="2E68D8FB5EA7AB70F3F3E966D8485A75D06A8CDB6E38DC331586F543FD3656C0";
    private static final Map<Origin,Source> SOURCES=Map.of(
        Origin.PHASE_ONE,new Source("pe.redsolidaria.modules.donations.PhaseOneControlledMySqlIntegrationTest",
            "CCE8A6DF298C602A07DE0DAAA165B40D4F4B94D7F71EB3844D2D2E7427AAD76E",
            "5B469A8762EE507C41BA2D49786F51E34822C1C65A974FF0805AF4E7F20D4491"),
        Origin.MODEL_B,new Source("pe.redsolidaria.modules.donations.ModelBComplementaryMySqlIntegrationTest",
            "7DFADD695E9B17194FEAF7C6A7121F7A85493A74B8A798D1CCD491EF0B00A86C",
            "F2EB09697056BF1C793763560AD9BA8A233DF65AC691826F0A6BFCAF0D110E9A"));
    private static final Set<Key> EXPECTED_ROLLBACKS=Set.of(new Key("movimiento_donacion","16"),
        new Key("traslado_recurso","f8a02053-fa0d-43f2-a30f-a985d3973cf0"));
    private static final Set<String> FIELDS=Set.of("suite","scenario","correlation","transaction","table","pk","state");
    private static final Pattern ROW_KEY=Pattern.compile("row\\.([0-9]{4})\\.(suite|scenario|correlation|transaction|table|pk|state)");

    /** Backward-compatible prerequisite for Model B: its own rows must never be required before it runs. */
    public static Map<String,Set<String>> load() { return Loaded.VALUE.phaseOne(); }
    public static Map<String,Set<String>> loadAllConfirmed() { return Loaded.VALUE.all(); }
    public static List<Entry> entries() { return Loaded.VALUE.entries(); }
    private static final class Loaded { private static final Inventory VALUE=readResources(); }
    record Inventory(List<Entry> entries,Map<String,Set<String>> phaseOne,Map<String,Set<String>> all) {}

    private static Inventory readResources() {
        try (var properties=resource("/phase-one-confirmed-rows.properties");
                var evidence=resource("/confirmed-integration-evidence.json")) {
            return read(properties,evidence);
        } catch (IOException failure) { throw new AssertionError("Cannot read confirmed identity inventory",failure); }
    }
    private static InputStream resource(String name) {
        var stream=PhaseOneConfirmedRows.class.getResourceAsStream(name);
        require(stream!=null,"Missing confirmed inventory resource: "+name); return stream;
    }
    /** Stream boundary for isolated malformed-input tests; never reads target or connects to a database. */
    static Inventory read(InputStream propertyStream,InputStream evidenceStream) {
        try {
            require(propertyStream!=null && evidenceStream!=null,"Missing inventory input");
            Properties properties=new UniqueProperties();
            properties.load(new InputStreamReader(propertyStream,StandardCharsets.UTF_8));
            require("2".equals(required(properties,"inventory.version")),"Unsupported inventory version");
            byte[] evidenceBytes=evidenceStream.readAllBytes();
            require(EVIDENCE_SHA256.equals(required(properties,"evidence.sha256"))
                && EVIDENCE_SHA256.equals(sha256(evidenceBytes)),"Frozen evidence checksum mismatch");
            Evidence evidence=JsonMapper.builder().build().readValue(evidenceBytes,Evidence.class);
            Map<Key,Entry> expected=verifyEvidence(evidence);
            Set<String> headers=new HashSet<>(Set.of("inventory.version","evidence.sha256"));
            SOURCES.forEach((origin,source) -> {
                String prefix="source."+origin+".";
                for (var metadata:Map.of("suite",source.suite(),"xml.sha256",source.xmlSha256(),"summary.sha256",source.summarySha256()).entrySet()) {
                    String key=prefix+metadata.getKey(); headers.add(key);
                    require(metadata.getValue().equals(required(properties,key)),"Source provenance mismatch: "+key);
                }
            });
            Map<String,Map<String,String>> rows=new TreeMap<>();
            for (String key:properties.stringPropertyNames()) {
                if (headers.contains(key)) continue;
                var match=ROW_KEY.matcher(key);
                require(match.matches(),"Unknown inventory property: "+key);
                int ordinal=Integer.parseInt(match.group(1));
                require(ordinal>=1 && ordinal<=381,"Invalid inventory row label");
                rows.computeIfAbsent(match.group(1),ignored -> new HashMap<>()).put(match.group(2),required(properties,key));
            }
            require(rows.size()==381,"Expected all 381 inventory entries");
            List<Entry> entries=new ArrayList<>(); Set<Key> seen=new HashSet<>();
            for (Map<String,String> fields:rows.values()) {
                require(fields.keySet().equals(FIELDS),"Incomplete inventory row metadata");
                Entry entry=new Entry(fields.get("table"),fields.get("pk"),State.valueOf(fields.get("state")),
                    Origin.valueOf(fields.get("suite")),fields.get("scenario"),fields.get("correlation"),fields.get("transaction"));
                Key key=new Key(entry.table(),entry.pk());
                require(!EXPECTED_ROLLBACKS.contains(key),"Rolled-back identity cannot be protected as committed");
                require(seen.add(key),"Duplicate confirmed identity: "+key);
                require(entry.equals(expected.get(key)),"Identity provenance differs from original final manifest: "+key);
                entries.add(entry);
            }
            require(seen.equals(expected.keySet()),"Inventory differs from the original confirmed identity set");
            verifyCounts(entries);
            Map<String,Set<String>> phaseOne=group(entries.stream().filter(e -> e.suite()==Origin.PHASE_ONE).toList());
            require(phaseOne.equals(legacySnapshot(evidence.legacyPhaseOneSnapshot())),"load() no longer matches its original 191-row result");
            return new Inventory(List.copyOf(entries),phaseOne,group(entries));
        } catch (IOException | IllegalArgumentException failure) {
            throw new AssertionError("Cannot parse confirmed inventory or provenance",failure);
        }
    }
    private static final class UniqueProperties extends Properties {
        @Override public synchronized Object put(Object key,Object value) {
            require(!containsKey(key),"Duplicate inventory property: "+key);
            return super.put(key,value);
        }
    }
    private static Map<Key,Entry> verifyEvidence(Evidence evidence) {
        require(evidence!=null && evidence.schemaVersion()==2 && evidence.sources()!=null
            && evidence.sources().keySet().equals(SOURCES.keySet()),"Incomplete original source evidence");
        Map<Key,Entry> entries=new HashMap<>(); Set<Key> rollbacks=new HashSet<>();
        Set<String> correlations=new HashSet<>(),transactionIds=new HashSet<>();
        for (var sourceItem:evidence.sources().entrySet()) {
            Origin origin=sourceItem.getKey(); EvidenceSource source=sourceItem.getValue(); Source expected=SOURCES.get(origin);
            require(source!=null && expected.suite().equals(source.suite()) && expected.xmlSha256().equals(source.xmlSha256())
                && expected.summarySha256().equals(source.summarySha256()),"Original report provenance mismatch");
            require(("TEST-"+expected.suite()+".xml").equals(source.xmlFile())
                && (expected.suite()+".txt").equals(source.summaryFile()),"Wrong original report identity");
            require(source.tests()==9 && source.failures()==0 && source.errors()==0 && source.skipped()==0
                && source.scenarios()!=null && source.scenarios().size()==9,"Original suite was not completely successful");
            Set<String> cases=new HashSet<>();
            for (Manifest manifest:source.scenarios()) {
                text(manifest.caseName(),"original scenario"); uuid(manifest.correlation(),"original correlation");
                require(cases.add(manifest.caseName()) && correlations.add(manifest.correlation()),"Duplicate original scenario or correlation");
                String simpleName=expected.suite().substring(expected.suite().lastIndexOf('.')+1);
                require(manifest.passed() && "integrity-verified".equals(manifest.stage())
                    && (manifest.caseName().equals(manifest.method()) || (simpleName+"."+manifest.caseName()).equals(manifest.method()))
                    && (manifest.correlation()+"/"+manifest.method()).equals(manifest.ledgerScenario()),"Original scenario provenance mismatch");
                require(manifest.transactions()!=null && manifest.rows()!=null && manifest.confirmedKeys()!=null,"Incomplete original manifest");
                Map<String,Transaction> transactions=new HashMap<>();
                for (Transaction transaction:manifest.transactions()) {
                    uuid(transaction.id(),"original transaction"); text(transaction.name(),"original transaction name");
                    require(Set.of("PREPARATION","SCENARIO").contains(transaction.phase())
                        && Set.of("COMMITTED","ROLLED_BACK").contains(transaction.outcome()),"Unsettled original transaction");
                    require(transactionIds.add(transaction.id()) && transactions.putIfAbsent(transaction.id(),transaction)==null,
                        "Duplicate original transaction");
                }
                Set<Key> scenarioKeys=new HashSet<>(),committedKeys=new HashSet<>();
                for (EvidenceRow row:manifest.rows()) {
                    require(FixtureSchema.EXPECTED.containsKey(row.table()),"Unknown original table"); text(row.pk(),"original PK");
                    Key key=new Key(row.table(),row.pk()); require(scenarioKeys.add(key),"Duplicate original row");
                    Transaction transaction=transactions.get(row.transaction()); require(transaction!=null,"Original row has no transaction");
                    if ("ROLLED_BACK".equals(row.state())) {
                        require("ROLLED_BACK".equals(transaction.outcome()) && rollbacks.add(key),"Invalid original rollback evidence");
                        continue;
                    }
                    State state=State.valueOf(row.state());
                    require("COMMITTED".equals(transaction.outcome())
                        && (state==State.COMMITTED_BASE ? "PREPARATION" : "SCENARIO").equals(transaction.phase()),"Row/transaction outcome mismatch");
                    Entry entry=new Entry(row.table(),row.pk(),state,origin,manifest.caseName(),manifest.correlation(),row.transaction());
                    require(entries.putIfAbsent(key,entry)==null,"Duplicate confirmed identity across original scenarios or suites");
                    committedKeys.add(key);
                }
                Set<Key> captured=new HashSet<>();
                for (EvidenceKey key:manifest.confirmedKeys()) require(captured.add(new Key(key.table(),key.pk())),"Duplicate original captured identity");
                require(captured.equals(committedKeys),"Original ledger and captured identities disagree");
            }
        }
        require(rollbacks.equals(EXPECTED_ROLLBACKS) && Collections.disjoint(rollbacks,entries.keySet()),"Original rollback exclusions disagree");
        verifyCounts(new ArrayList<>(entries.values())); return Map.copyOf(entries);
    }
    private static void verifyCounts(List<Entry> entries) {
        require(entries.size()==381,"Expected 381 confirmed identities");
        require(count(entries,Origin.PHASE_ONE,State.COMMITTED_BASE)==126
            && count(entries,Origin.PHASE_ONE,State.COMMITTED_OPERATION)==65
            && count(entries,Origin.MODEL_B,State.COMMITTED_BASE)==128
            && count(entries,Origin.MODEL_B,State.COMMITTED_OPERATION)==62,"Confirmed origin/state totals disagree");
    }
    private static long count(List<Entry> entries,Origin suite,State state) {
        return entries.stream().filter(e -> e.suite()==suite && e.state()==state).count();
    }
    private static Map<String,Set<String>> legacySnapshot(Map<String,List<String>> snapshot) {
        require(snapshot!=null,"Missing original load() snapshot");
        Map<String,Set<String>> result=new TreeMap<>();
        snapshot.forEach((table,ids) -> {
            require(FixtureSchema.EXPECTED.containsKey(table) && ids!=null,"Invalid legacy inventory table");
            Set<String> unique=new HashSet<>(); ids.forEach(id -> { text(id,"legacy PK"); require(unique.add(id),"Duplicate legacy identity"); });
            result.put(table,Set.copyOf(unique));
        });
        require(result.values().stream().mapToInt(Set::size).sum()==191,"Incomplete legacy 191-row snapshot");
        return Map.copyOf(result);
    }
    private static Map<String,Set<String>> group(List<Entry> entries) {
        Map<String,Set<String>> mutable=new TreeMap<>(),frozen=new TreeMap<>();
        entries.forEach(entry -> mutable.computeIfAbsent(entry.table(),ignored -> new TreeSet<>()).add(entry.pk()));
        mutable.forEach((table,ids) -> frozen.put(table,Set.copyOf(ids))); return Map.copyOf(frozen);
    }
    private static String required(Properties properties,String key) { String value=properties.getProperty(key); text(value,key); return value; }
    private static void text(String value,String field) { require(value!=null && !value.isBlank() && value.equals(value.trim()),"Missing or invalid "+field); }
    private static void uuid(String value,String field) { text(value,field); require(UUID.fromString(value).toString().equals(value),"Invalid "+field); }
    private static void require(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    // Sanitized projections of the original reports. No JVM properties, credentials or business column values.
    record Evidence(int schemaVersion,Map<Origin,EvidenceSource> sources,Map<String,List<String>> legacyPhaseOneSnapshot) {}
    record EvidenceSource(String suite,String xmlFile,String xmlSha256,String summaryFile,String summarySha256,
        int tests,int failures,int errors,int skipped,List<Manifest> scenarios) {}
    record Manifest(String caseName,String method,String correlation,String ledgerScenario,String stage,boolean passed,
        List<Transaction> transactions,List<EvidenceRow> rows,List<EvidenceKey> confirmedKeys) {}
    record Transaction(String id,String name,String phase,String outcome) {}
    record EvidenceRow(String table,String pk,String state,String transaction) {}
    record EvidenceKey(String table,String pk) {}
}
