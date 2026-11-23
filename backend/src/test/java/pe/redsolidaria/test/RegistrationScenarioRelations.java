package pe.redsolidaria.test;

import java.math.BigDecimal;
import java.util.*;
import pe.redsolidaria.test.ControlledFixtureAudit.*;
import pe.redsolidaria.test.FixtureLedger.*;

/** One registration, one resource, one transaction. Never changes the default scenario policy. */
public final class RegistrationScenarioRelations {
    public static final BigDecimal QUANTITY=new BigDecimal("2.500");
    public static final String LOCATION="Controlled collection";
    public static final String OBSERVATIONS="Controlled registration observation";
    private static final Set<String> TABLES=Set.of("donacion","detalle_donacion","movimiento_donacion","registro_auditoria");
    private final long role,actor,donor,collection,unit,resource;
    private final Map<String,Key> observed=new LinkedHashMap<>();
    private final Map<Key,RowImage> images=new HashMap<>();
    private String transaction;
    private boolean claimed;

    public RegistrationScenarioRelations(long role,long actor,long donor,long collection,long unit,long resource) {
        this.role=role; this.actor=actor; this.donor=donor; this.collection=collection; this.unit=unit; this.resource=resource;
        if (List.of(role,actor,donor,collection,unit,resource).stream().anyMatch(id -> id<=0))
            throw new AssertionError("Registration requires generated fixture identities");
    }
    void claim() {
        if (claimed) throw new AssertionError("Registration policy cannot be shared between scenarios");
        claimed=true;
    }
    boolean permits(String table) { return TABLES.contains(table); }
    Set<Key> observedKeys() { return Set.copyOf(images.keySet()); }
    void observe(Key key,RowImage image,Transaction tx) {
        if (!permits(key.table()) || observed.containsKey(key.table()) || !key.id().equals(image.key()))
            throw new AssertionError("Unexpected or duplicate registration insert");
        if (tx==null || tx.phase!=Phase.SCENARIO || tx.outcome!=Outcome.ACTIVE
                || (transaction!=null && !transaction.equals(tx.id)))
            throw new AssertionError("All registration inserts require the same active scenario transaction");
        if (!key.table().equals("donacion") && !observed.containsKey("donacion"))
            throw new AssertionError("Registration donation must be observed before its children");
        validate(key,image);
        transaction=tx.id; observed.put(key.table(),key); images.put(key,image);
    }
    private void validate(Key key,RowImage row) {
        switch (key.table()) {
            case "donacion" -> {
                equal(row,"id_donante",donor); equal(row,"id_usuario_registro",actor); equal(row,"id_centro_acopio",collection);
                field(row,"id_emergencia",null); field(row,"estado","REGISTRADA"); field(row,"fecha_ingreso",null);
                field(row,"observaciones",OBSERVATIONS); nonNull(row,"fecha_registro"); nonNull(row,"codigo_trazabilidad");
            }
            case "detalle_donacion" -> {
                donation(row,"id_donacion"); equal(row,"id_recurso",resource); field(row,"cantidad_donada","2.500");
                for (String column:List.of("cantidad_recibida_acopio","cantidad_examinada","cantidad_no_aceptada",
                        "cantidad_excedente_pendiente","cantidad_excedente_aprobada")) field(row,column,"0.000");
                field(row,"verificacion_cerrada","0");
            }
            case "movimiento_donacion" -> {
                donation(row,"id_donacion"); equal(row,"id_usuario_responsable",actor);
                field(row,"tipo_evento","DONACION_PRESENTADA"); field(row,"estado_anterior",null);
                field(row,"estado_nuevo","REGISTRADA"); field(row,"ubicacion",LOCATION);
                field(row,"observaciones",OBSERVATIONS); nonNull(row,"fecha_hora");
            }
            case "registro_auditoria" -> {
                donation(row,"id_registro"); equal(row,"id_usuario",actor); field(row,"entidad","DONACION");
                field(row,"accion","REGISTRO_DONACION"); field(row,"datos_anteriores",null);
                nonNull(row,"datos_nuevos"); nonNull(row,"fecha_hora");
            }
            default -> throw new AssertionError("Unsupported registration table");
        }
    }
    private void donation(RowImage row,String column) {
        if (!observed.get("donacion").id().equals(row.references().get(column)))
            throw new AssertionError("Registration child belongs to another donation");
    }
    void verify(Snapshot after,FixtureLedger ledger) {
        Set<Key> parents=Set.of(key("rol",role),key("usuario",actor),key("donante",donor),
            key("centro_acopio",collection),key("unidad_medida",unit),key("recurso",resource));
        if (!parents.equals(ledger.keys(State.COMMITTED_BASE)))
            throw new AssertionError("Registration requires exactly six committed base fixtures");
        RowImage user=after.tables().get("usuario").get(Long.toString(actor));
        equal(user,"id_rol",role); field(user,"estado","ACTIVO"); field(user,"admin_maestro",null);
        RowImage item=after.tables().get("recurso").get(Long.toString(resource));
        equal(item,"id_unidad_medida",unit); field(item,"estado","ACTIVO");
        Set<Key> committed=ledger.keys(State.COMMITTED_OPERATION);
        if (!committed.isEmpty() && (observed.size()!=4 || !committed.equals(observedKeys())))
            throw new AssertionError("Registration commit must contain all four observed operations");
        for (Key key:committed) {
            RowImage actual=after.tables().get(key.table()).get(key.id());
            if (actual==null || !images.get(key).digest().equals(actual.digest()))
                throw new AssertionError("Registration row changed after its observed INSERT");
            validate(key,actual);
        }
    }
    Map<String,String> confirmed(FixtureLedger ledger) {
        if (observed.size()!=4 || !ledger.keys(State.COMMITTED_OPERATION).equals(observedKeys()))
            throw new AssertionError("Registration has not committed all four observed identities");
        Map<String,String> ids=new TreeMap<>(); observed.forEach((table,key) -> ids.put(table,key.id()));
        return Map.copyOf(ids);
    }
    Map<String,String> pendingRollback(String target,Transaction tx,FixtureLedger ledger) {
        Set<String> expected=target.equals("registro_auditoria") ? TABLES : Set.of("donacion","detalle_donacion","movimiento_donacion");
        if (!observed.keySet().equals(expected) || tx==null || tx.outcome!=Outcome.ACTIVE
                || !tx.id.equals(transaction) || !ledger.keys(State.ASSIGNED).equals(observedKeys())
                || !ledger.keys(State.COMMITTED_OPERATION).isEmpty())
            throw new AssertionError("Rollback fault requires the exact pending observed registration");
        Map<String,String> ids=new TreeMap<>();
        observed.forEach((table,key) -> { validate(key,images.get(key)); ids.put(table,key.id()); });
        return Map.copyOf(ids);
    }
    void verifyRollbackAbsence(Snapshot after,Map<String,String> ids) {
        ids.forEach((table,id) -> {
            if (after.tables().get(table).containsKey(id)) throw new AssertionError("Residual rolled-back primary key: "+table+"/"+id);
        });
        String donation=ids.get("donacion");
        if (after.tables().get("donacion").values().stream().anyMatch(row -> Long.toString(donor).equals(row.references().get("id_donante"))))
            throw new AssertionError("Residual donation for the synthetic donor");
        for (String table:List.of("detalle_donacion","movimiento_donacion"))
            if (after.tables().get(table).values().stream().anyMatch(row -> donation.equals(row.references().get("id_donacion"))))
                throw new AssertionError("Residual registration child: "+table);
        if (after.tables().get("registro_auditoria").values().stream().anyMatch(row ->
                ("DONACION".equals(row.references().get("entidad")) && donation.equals(row.references().get("id_registro")))
                || Long.toString(actor).equals(row.references().get("id_usuario"))))
            throw new AssertionError("Residual registration audit");
    }
    private static Key key(String table,long id) { return new Key(table,Long.toString(id)); }
    private static void equal(RowImage row,String column,long id) {
        if (row==null || !Long.toString(id).equals(row.references().get(column)))
            throw new AssertionError("Registration fixture relationship mismatch: "+column);
    }
    private static String hash(String column,String value) {
        Map<String,String> fields=new HashMap<>(); fields.put("test_pk","x"); fields.put(column,value);
        return ControlledFixtureAudit.image(List.of("test_pk"),fields).fields().get(column);
    }
    private static void field(RowImage row,String column,String value) {
        if (row==null || !hash(column,value).equals(row.fields().get(column)))
            throw new AssertionError("Registration field mismatch: "+column);
    }
    private static void nonNull(RowImage row,String column) {
        if (!row.fields().containsKey(column) || hash(column,null).equals(row.fields().get(column)))
            throw new AssertionError("Missing generated registration field: "+column);
    }
}
