package pe.redsolidaria.test;

import java.util.*;
import pe.redsolidaria.test.ControlledFixtureAudit.*;
import pe.redsolidaria.test.FixtureLedger.*;

/** Exact authorized entities for one scenario, frozen before its first operation. */
public record ScenarioRelations(long donation,long actor,long line,long resource,long unit,long collection,
        long distribution,Set<Long> needs,Map<Long,Long> additionalLines,Long registrar) {
    public ScenarioRelations(long donation,long actor,long line,long resource,long unit,long collection,long distribution,Set<Long> needs) {
        this(donation,actor,line,resource,unit,collection,distribution,needs,Map.of(),null);
    }
    public ScenarioRelations {
        needs=Set.copyOf(needs); additionalLines=Map.copyOf(additionalLines);
        if (needs.isEmpty()) throw new AssertionError("No authorized needs");
        if (additionalLines.containsKey(line) || additionalLines.containsValue(resource)
                || new HashSet<>(additionalLines.values()).size()!=additionalLines.size())
            throw new AssertionError("Additional resource/detail pairs must be explicit and distinct");
    }
    boolean permitsResource(long id) { return resource==id || additionalLines.containsValue(id); }
    void verify(Snapshot baseline,Snapshot after,FixtureLedger ledger) {
        verify(baseline,after,ledger,Set.of());
    }
    void verify(Snapshot baseline,Snapshot after,FixtureLedger ledger,Set<Key> presentedMovements) {
        Map<String,Long> parents=Map.of("donacion",donation,"usuario",actor,"detalle_donacion",line,"recurso",resource,
            "unidad_medida",unit,"centro_acopio",collection,"centro_distribucion",distribution);
        parents.forEach((table,id) -> requireBase(ledger,table,id));
        needs.forEach(id -> requireBase(ledger,"necesidad",id));
        if (registrar!=null) {
            requireBase(ledger,"usuario",registrar);
            equal(after.tables().get("donacion").get(Long.toString(donation)),"id_usuario_registro",registrar);
        }
        RowImage donated=after.tables().get("detalle_donacion").get(Long.toString(line));
        equal(donated,"id_donacion",donation); equal(donated,"id_recurso",resource);
        equal(after.tables().get("recurso").get(Long.toString(resource)),"id_unidad_medida",unit);
        equal(after.tables().get("donacion").get(Long.toString(donation)),"id_centro_acopio",collection);
        additionalLines.forEach((detail,otherResource) -> {
            requireBase(ledger,"detalle_donacion",detail); requireBase(ledger,"recurso",otherResource);
            RowImage added=after.tables().get("detalle_donacion").get(detail.toString());
            equal(added,"id_donacion",donation); equal(added,"id_recurso",otherResource);
            equal(after.tables().get("recurso").get(otherResource.toString()),"id_unidad_medida",unit);
        });
        for (long need:needs) equal(after.tables().get("necesidad").get(Long.toString(need)),"id_recurso",resource);
        for (String table:FixtureSchema.OPERATIONS) for (RowImage row:after.tables().get(table).values()) {
            if (baseline.tables().get(table).containsKey(row.key())) continue;
            switch (table) {
                case "recepcion_operacion" -> { equal(row,"id_donacion",donation); optionalActor(row,"id_usuario_decision"); }
                case "movimiento_donacion" -> {
                    equal(row,"id_donacion",donation);
                    if (presentedMovements.contains(new Key(table,row.key()))) {
                        if (registrar==null || !"DONACION_PRESENTADA".equals(row.references().get("tipo_evento")))
                            throw new AssertionError("Only observed presentation movements may use the registrar");
                        owned(ledger,table,row.key());
                        equal(row,"id_usuario_responsable",registrar);
                    } else equal(row,"id_usuario_responsable",actor);
                }
                case "traslado_recurso" -> {
                    equal(row,"id_detalle_donacion",line); equal(row,"id_centro_acopio",collection);
                    equal(row,"id_centro_distribucion",distribution); equal(row,"id_unidad_medida",unit);
                    equal(row,"id_usuario_salida",actor); optionalActor(row,"id_usuario_recepcion");
                }
                case "entrega" -> equal(row,"id_usuario_responsable",actor);
                case "detalle_entrega" -> {
                    equal(row,"id_detalle_donacion",line);
                    if (needs.stream().noneMatch(n -> n.toString().equals(row.references().get("id_necesidad"))))
                        throw new AssertionError("Delivery refers to an unauthorized need");
                    owned(ledger,"entrega",row.references().get("id_entrega"));
                }
                case "registro_auditoria" -> {
                    equal(row,"id_usuario",actor);
                    String target=row.references().get("id_registro");
                    switch (row.references().getOrDefault("entidad","")) {
                        case "DONACION" -> equal(row,"id_registro",donation);
                        case "TRASLADO" -> owned(ledger,"traslado_recurso",target);
                        case "ENTREGA" -> owned(ledger,"entrega",target);
                        default -> throw new AssertionError("Unauthorized scenario audit entity");
                    }
                }
                default -> throw new AssertionError("Unsupported operation table");
            }
        }
    }
    private void optionalActor(RowImage row,String column) { if (row.references().containsKey(column)) equal(row,column,actor); }
    private static void equal(RowImage row,String column,long expected) {
        if (row==null || !Long.toString(expected).equals(row.references().get(column)))
            throw new AssertionError("Scenario relation mismatch: "+column);
    }
    private static void requireBase(FixtureLedger ledger,String table,long id) {
        if (!ledger.keys(State.COMMITTED_BASE).contains(new Key(table,Long.toString(id))))
            throw new AssertionError("Scenario parent is not a committed base fixture: "+table);
    }
    private static void owned(FixtureLedger ledger,String table,String id) {
        if (id==null || !ledger.attributedKeys().contains(new Key(table,id))) throw new AssertionError("Unattributed operation target");
    }
}
