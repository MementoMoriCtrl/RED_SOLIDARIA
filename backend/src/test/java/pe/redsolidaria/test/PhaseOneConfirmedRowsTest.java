package pe.redsolidaria.test;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import pe.redsolidaria.test.PhaseOneConfirmedRows.*;

/** Classpath resources and pure inventory parsing only: no Spring context, JDBC or target reports. */
class PhaseOneConfirmedRowsTest {
    static byte[] resource(String path) {
        try (var stream=PhaseOneConfirmedRowsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream); return stream.readAllBytes();
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    static String propertiesText() { return new String(resource("/phase-one-confirmed-rows.properties"),StandardCharsets.UTF_8); }
    static Properties properties() {
        Properties properties=new Properties();
        try { properties.load(new StringReader(propertiesText())); return properties; }
        catch (IOException impossible) { throw new UncheckedIOException(impossible); }
    }
    static Inventory parse(String text) {
        return PhaseOneConfirmedRows.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
            new ByteArrayInputStream(resource("/confirmed-integration-evidence.json")));
    }
    static Inventory parse(Properties properties) {
        StringWriter writer=new StringWriter();
        try { properties.store(writer,"isolated input mutation"); }
        catch (IOException impossible) { throw new UncheckedIOException(impossible); }
        return parse(writer.toString());
    }
    static Evidence evidence() {
        return JsonMapper.builder().build().readValue(resource("/confirmed-integration-evidence.json"),Evidence.class);
    }
    static Map<String,Set<String>> legacySnapshot() {
        Map<String,Set<String>> snapshot=new TreeMap<>();
        evidence().legacyPhaseOneSnapshot().forEach((table,ids) -> snapshot.put(table,Set.copyOf(ids)));
        return Map.copyOf(snapshot);
    }
    @Test void all381LiteralIdentitiesHaveTheExpectedOriginAndCommittedStateCounts() {
        List<Entry> entries=PhaseOneConfirmedRows.entries(); assertEquals(381,entries.size());
        assertEquals(191,entries.stream().filter(e -> e.suite()==Origin.PHASE_ONE).count());
        assertEquals(190,entries.stream().filter(e -> e.suite()==Origin.MODEL_B).count());
        assertEquals(254,entries.stream().filter(e -> e.state()==State.COMMITTED_BASE).count());
        assertEquals(127,entries.stream().filter(e -> e.state()==State.COMMITTED_OPERATION).count());
        assertEquals(381,entries.stream().map(e -> List.of(e.table(),e.pk())).distinct().count());
        assertEquals(381,PhaseOneConfirmedRows.loadAllConfirmed().values().stream().mapToInt(Set::size).sum());
        assertEquals(18,entries.stream().map(Entry::correlation).distinct().count());
    }
    @Test void loadExactlyPreservesThePrevious191RowMapAndCannotRequireModelBToSeedItsOwnPrerequisites() {
        assertEquals(legacySnapshot(),PhaseOneConfirmedRows.load());
        assertEquals(191,PhaseOneConfirmedRows.load().values().stream().mapToInt(Set::size).sum());
        Set<List<String>> model=new HashSet<>();
        PhaseOneConfirmedRows.entries().stream().filter(e -> e.suite()==Origin.MODEL_B).forEach(e -> model.add(List.of(e.table(),e.pk())));
        PhaseOneConfirmedRows.load().forEach((table,ids) -> ids.forEach(id -> assertFalse(model.contains(List.of(table,id)))));
    }
    @Test void everyEntryMatchesItsOriginalCaseCorrelationTransactionOutcomeAndCapturedIdentity() {
        Map<List<String>,Entry> parsed=new HashMap<>();
        PhaseOneConfirmedRows.entries().forEach(e -> assertNull(parsed.put(List.of(e.table(),e.pk()),e)));
        int verified=0; Set<List<String>> excluded=new HashSet<>();
        for (var source:evidence().sources().entrySet()) {
            assertEquals(9,source.getValue().scenarios().size());
            for (Manifest manifest:source.getValue().scenarios()) {
                assertTrue(manifest.passed()); assertEquals("integrity-verified",manifest.stage());
                assertEquals(manifest.correlation()+"/"+manifest.method(),manifest.ledgerScenario());
                Set<List<String>> committed=new HashSet<>();
                for (EvidenceRow row:manifest.rows()) {
                    var transaction=manifest.transactions().stream().filter(t -> t.id().equals(row.transaction())).findFirst().orElseThrow();
                    var key=List.of(row.table(),row.pk());
                    if (row.state().equals("ROLLED_BACK")) {
                        assertEquals("ROLLED_BACK",transaction.outcome()); assertFalse(parsed.containsKey(key)); excluded.add(key); continue;
                    }
                    assertEquals("COMMITTED",transaction.outcome());
                    State state=State.valueOf(row.state());
                    assertEquals(state==State.COMMITTED_BASE ? "PREPARATION" : "SCENARIO",transaction.phase());
                    assertEquals(new Entry(row.table(),row.pk(),state,source.getKey(),manifest.caseName(),manifest.correlation(),transaction.id()),parsed.get(key));
                    assertTrue(committed.add(key)); verified++;
                }
                assertEquals(committed,manifest.confirmedKeys().stream().map(k -> List.of(k.table(),k.pk())).collect(java.util.stream.Collectors.toSet()));
            }
        }
        assertEquals(381,verified);
        assertEquals(Set.of(List.of("movimiento_donacion","16"),List.of("traslado_recurso","f8a02053-fa0d-43f2-a30f-a985d3973cf0")),excluded);
    }
    @Test void originalXmlHashesAndSanitizedEvidenceAreRetainedAndPinned() throws Exception {
        var sources=evidence().sources();
        assertEquals("CCE8A6DF298C602A07DE0DAAA165B40D4F4B94D7F71EB3844D2D2E7427AAD76E",sources.get(Origin.PHASE_ONE).xmlSha256());
        assertEquals("7DFADD695E9B17194FEAF7C6A7121F7A85493A74B8A798D1CCD491EF0B00A86C",sources.get(Origin.MODEL_B).xmlSha256());
        byte[] bytes=resource("/confirmed-integration-evidence.json");
        assertEquals(properties().getProperty("evidence.sha256"),HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        String sanitized=new String(bytes,StandardCharsets.UTF_8);
        for (String privateField:List.of("password","password_hash","system-out","system-err","java.class.path","spring.datasource","datos_nuevos"))
            assertFalse(sanitized.contains(privateField),privateField);
    }
    @ParameterizedTest @ValueSource(strings={"suite","scenario","correlation","transaction","table","pk","state"})
    void absentRowMetadataFailsClosed(String field) {
        var input=properties(); input.remove("row.0001."+field); assertThrows(AssertionError.class,() -> parse(input));
    }
    @ParameterizedTest @ValueSource(strings={"suite","scenario","correlation","transaction","table","pk","state"})
    void blankRowMetadataFailsClosed(String field) {
        var input=properties(); input.setProperty("row.0001."+field," "); assertThrows(AssertionError.class,() -> parse(input));
    }
    @ParameterizedTest @ValueSource(strings={"inventory.version","evidence.sha256","source.PHASE_ONE.suite","source.PHASE_ONE.xml.sha256",
        "source.PHASE_ONE.summary.sha256","source.MODEL_B.suite","source.MODEL_B.xml.sha256","source.MODEL_B.summary.sha256"})
    void missingSourceMetadataFailsClosed(String key) {
        var input=properties(); input.remove(key); assertThrows(AssertionError.class,() -> parse(input));
    }
    @ParameterizedTest @ValueSource(strings={"inventory.version","evidence.sha256","source.PHASE_ONE.suite","source.PHASE_ONE.xml.sha256",
        "source.PHASE_ONE.summary.sha256","source.MODEL_B.suite","source.MODEL_B.xml.sha256","source.MODEL_B.summary.sha256"})
    void alteredVersionOrSourceMetadataFailsClosed(String key) {
        var input=properties(); input.setProperty(key,"altered"); assertThrows(AssertionError.class,() -> parse(input));
    }
    @ParameterizedTest @ValueSource(strings={"ROLLED_BACK","ASSIGNED","UNKNOWN","COMMITTED","committed_base"})
    void nonCommittedAndInvalidStatesAreRejected(String state) {
        var input=properties(); input.setProperty("row.0001.state",state); assertThrows(AssertionError.class,() -> parse(input));
    }
    @ParameterizedTest @ValueSource(strings={"row.0001.pk","source.PHASE_ONE.xml.sha256","inventory.version"})
    void duplicatePropertiesAreRejectedEvenWhenTheRepeatedValueIsIdentical(String key) {
        var problem=assertThrows(AssertionError.class,() -> parse(propertiesText()+key+"="+properties().getProperty(key)+"\n"));
        assertTrue(problem.getMessage().contains("Duplicate inventory property"));
    }
    @Test void escapedDuplicatePropertiesCannotSilentlyOverwriteAnEarlierEntry() {
        var problem=assertThrows(AssertionError.class,() -> parse(propertiesText()+"row.0001.\\u0070k=999\n"));
        assertTrue(problem.getMessage().contains("Duplicate inventory property"));
    }
    @ParameterizedTest @ValueSource(strings={"0002","0015","0192"})
    void duplicateIdentitiesAreRejectedWithinAScenarioAcrossScenariosAndAcrossSuites(String label) {
        var input=properties(); input.setProperty("row."+label+".table",input.getProperty("row.0001.table"));
        input.setProperty("row."+label+".pk",input.getProperty("row.0001.pk"));
        var problem=assertThrows(AssertionError.class,() -> parse(input));
        assertTrue(problem.getMessage().contains("Duplicate confirmed identity"));
    }
    @ParameterizedTest @ValueSource(strings={"suite","scenario","correlation","transaction","pk"})
    void plausibleButIncorrectProvenanceCannotPassByPreservingTotalCounts(String field) {
        var input=properties();
        String value=switch(field) {
            case "suite" -> "MODEL_B";
            case "scenario" -> input.getProperty("row.0015.scenario");
            case "correlation" -> input.getProperty("row.0015.correlation");
            case "transaction" -> input.getProperty("row.0015.transaction");
            default -> "999999";
        };
        input.setProperty("row.0001."+field,value); assertThrows(AssertionError.class,() -> parse(input));
    }
    @Test void bothKnownRollbackKeysAreExplicitlyRejectedAsCommittedRows() {
        for (var key:List.of(List.of("movimiento_donacion","16"),List.of("traslado_recurso","f8a02053-fa0d-43f2-a30f-a985d3973cf0"))) {
            var input=properties(); input.setProperty("row.0001.table",key.getFirst()); input.setProperty("row.0001.pk",key.getLast());
            input.setProperty("row.0001.state","COMMITTED_OPERATION");
            var problem=assertThrows(AssertionError.class,() -> parse(input)); assertTrue(problem.getMessage().contains("Rolled-back identity"));
        }
    }
    @Test void missingEntriesAdditionalPropertiesAndUnknownTablesAreRejected() {
        var missing=properties();
        for (String field:List.of("suite","scenario","correlation","transaction","table","pk","state")) missing.remove("row.0381."+field);
        assertThrows(AssertionError.class,() -> parse(missing));
        var extra=properties(); extra.setProperty("row.0001.extra","unexpected"); assertThrows(AssertionError.class,() -> parse(extra));
        var table=properties(); table.setProperty("row.0001.table","foreign_table"); assertThrows(AssertionError.class,() -> parse(table));
    }
    @Test void evidenceAlterationAndAbsentStreamsCannotBeAccepted() {
        byte[] evidence=resource("/confirmed-integration-evidence.json"); evidence[0]^=1;
        assertThrows(AssertionError.class,() -> PhaseOneConfirmedRows.read(new ByteArrayInputStream(propertiesText().getBytes(StandardCharsets.UTF_8)),new ByteArrayInputStream(evidence)));
        assertThrows(AssertionError.class,() -> PhaseOneConfirmedRows.read(null,new ByteArrayInputStream(evidence)));
        assertThrows(AssertionError.class,() -> PhaseOneConfirmedRows.read(new ByteArrayInputStream(new byte[0]),null));
    }
    @Test void bothApisTheirNestedSetsAndTheProvenanceListAreImmutable() {
        for (var inventory:List.of(PhaseOneConfirmedRows.load(),PhaseOneConfirmedRows.loadAllConfirmed())) {
            assertThrows(UnsupportedOperationException.class,() -> inventory.put("rol",Set.of("999")));
            assertThrows(UnsupportedOperationException.class,() -> inventory.get("rol").clear());
        }
        assertThrows(UnsupportedOperationException.class,() -> PhaseOneConfirmedRows.entries().clear());
    }
}
