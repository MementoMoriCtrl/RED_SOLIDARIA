package pe.redsolidaria.test;

import java.util.*;

/** Names, keys and nullability from the unchanged V1-V5 files; Flyway's own table is also protected. */
final class FixtureSchema {
    record ForeignKey(String column,String table,String parentColumn,boolean nullable) {}
    record Shape(List<String> primaryKey,List<ForeignKey> foreignKeys) {
        Shape {
            primaryKey=List.copyOf(primaryKey);
            foreignKeys=foreignKeys.stream().sorted(Comparator.comparing(ForeignKey::column)
                .thenComparing(ForeignKey::table).thenComparing(ForeignKey::parentColumn)).toList();
        }
    }
    static ForeignKey fk(String column,String table,String key) { return new ForeignKey(column,table,key,false); }
    static ForeignKey optional(String column,String table,String key) { return new ForeignKey(column,table,key,true); }
    static Shape table(String key,ForeignKey... keys) { return new Shape(List.of(key),List.of(keys)); }
    static final Map<String,Shape> EXPECTED=Map.ofEntries(
        Map.entry("rol",table("id_rol")), Map.entry("permiso",table("id_permiso")),
        Map.entry("rol_permiso",new Shape(List.of("id_rol","id_permiso"),List.of(fk("id_rol","rol","id_rol"),fk("id_permiso","permiso","id_permiso")))),
        Map.entry("usuario",table("id_usuario",fk("id_rol","rol","id_rol"))),
        Map.entry("emergencia",table("id_emergencia")),
        Map.entry("zona_afectada",table("id_zona_afectada",fk("id_emergencia","emergencia","id_emergencia"))),
        Map.entry("centro_acopio",table("id_centro_acopio")), Map.entry("centro_distribucion",table("id_centro_distribucion")),
        Map.entry("unidad_medida",table("id_unidad_medida")),
        Map.entry("recurso",table("id_recurso",fk("id_unidad_medida","unidad_medida","id_unidad_medida"))),
        Map.entry("donante",table("id_donante")),
        Map.entry("donacion",table("id_donacion",fk("id_donante","donante","id_donante"),optional("id_emergencia","emergencia","id_emergencia"),
            optional("id_centro_acopio","centro_acopio","id_centro_acopio"),fk("id_usuario_registro","usuario","id_usuario"))),
        Map.entry("detalle_donacion",table("id_detalle_donacion",fk("id_donacion","donacion","id_donacion"),fk("id_recurso","recurso","id_recurso"))),
        Map.entry("necesidad",table("id_necesidad",fk("id_zona_afectada","zona_afectada","id_zona_afectada"),fk("id_recurso","recurso","id_recurso"))),
        Map.entry("entrega",table("id_entrega",fk("id_usuario_responsable","usuario","id_usuario"))),
        Map.entry("detalle_entrega",table("id_detalle_entrega",fk("id_entrega","entrega","id_entrega"),
            fk("id_detalle_donacion","detalle_donacion","id_detalle_donacion"),fk("id_necesidad","necesidad","id_necesidad"))),
        Map.entry("movimiento_donacion",table("id_movimiento",fk("id_donacion","donacion","id_donacion"),fk("id_usuario_responsable","usuario","id_usuario"))),
        Map.entry("registro_auditoria",table("id_registro_auditoria",fk("id_usuario","usuario","id_usuario"))),
        Map.entry("recepcion_operacion",table("id_recepcion_operacion",fk("id_donacion","donacion","id_donacion"),optional("id_usuario_decision","usuario","id_usuario"))),
        Map.entry("traslado_recurso",table("operacion_id",fk("id_detalle_donacion","detalle_donacion","id_detalle_donacion"),
            fk("id_centro_acopio","centro_acopio","id_centro_acopio"),fk("id_centro_distribucion","centro_distribucion","id_centro_distribucion"),
            fk("id_unidad_medida","unidad_medida","id_unidad_medida"),fk("id_usuario_salida","usuario","id_usuario"),optional("id_usuario_recepcion","usuario","id_usuario"))),
        Map.entry("flyway_schema_history",table("installed_rank")));
    static final Set<String> BASE=Set.of("rol","usuario","donante","unidad_medida","recurso","emergencia","zona_afectada",
        "centro_acopio","centro_distribucion","donacion","detalle_donacion","necesidad");
    static final Set<String> OPERATIONS=Set.of("recepcion_operacion","movimiento_donacion","traslado_recurso","entrega","detalle_entrega","registro_auditoria");
    // Only columns actually changed by the existing nine scenarios. All FKs of base rows stay immutable.
    static final Map<String,Set<String>> MUTABLE=Map.of(
        "donacion",Set.of("estado","fecha_ingreso"),
        "detalle_donacion",Set.of("cantidad_recibida_acopio","cantidad_examinada","cantidad_no_aceptada","cantidad_excedente_pendiente","cantidad_excedente_aprobada","verificacion_cerrada"),
        "necesidad",Set.of("cantidad_requerida"),
        "recepcion_operacion",Set.of("decision","id_usuario_decision","fecha_decision","observaciones_decision","uuid_decision","huella_decision","respuesta_decision"),
        "traslado_recurso",Set.of("cantidad_recibida","id_usuario_recepcion","fecha_recepcion","estado","observaciones_recepcion","operacion_recepcion","huella_recepcion"));
    private FixtureSchema() {}
}
