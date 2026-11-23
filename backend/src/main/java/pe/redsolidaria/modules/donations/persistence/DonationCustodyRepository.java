package pe.redsolidaria.modules.donations.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pe.redsolidaria.modules.donations.api.dto.CustodyDtos.*;

/** Read-only projection of existing records; no stock or business-state updates. */
@Repository
public class DonationCustodyRepository {
    public record Row(long lineId, Event event, String operation, Long actorId, String actor,
                      String notes, boolean distribution, String sourceKey) {
        public Row(long lineId,Event event,String operation,Long actorId,String actor,String notes,boolean distribution) {
            this(lineId,event,operation,actorId,actor,notes,distribution,operation);
        }
    }
    private final JdbcClient jdbc;
    public DonationCustodyRepository(JdbcClient jdbc) { this.jdbc = jdbc; }
    public List<Row> events(long donationId) {
        List<Row> result = new ArrayList<>();
        result.addAll(jdbc.sql("""
            SELECT t.*, a.nombre origen_nombre,a.ubicacion origen_direccion,
              c.nombre destino_nombre,c.ubicacion destino_direccion,
              CONCAT(s.nombre,' ',s.apellido) salida_nombre,CONCAT(u.nombre,' ',u.apellido) recepcion_nombre
            FROM traslado_recurso t JOIN detalle_donacion l ON l.id_detalle_donacion=t.id_detalle_donacion
            JOIN centro_acopio a ON a.id_centro_acopio=t.id_centro_acopio
            JOIN centro_distribucion c ON c.id_centro_distribucion=t.id_centro_distribucion
            JOIN usuario s ON s.id_usuario=t.id_usuario_salida
            LEFT JOIN usuario u ON u.id_usuario=t.id_usuario_recepcion
            WHERE l.id_donacion=:id
            """).param("id", donationId).query((rs, n) -> {
                var origin = new Place("ACOPIO",rs.getString("origen_nombre"),rs.getString("origen_direccion"));
                var destination = new Place("DISTRIBUCION",rs.getString("destino_nombre"),rs.getString("destino_direccion"));
                var departure = new Row(rs.getLong("id_detalle_donacion"),
                    new Event("SALIDA_TRASLADO",rs.getObject("fecha_salida",LocalDateTime.class),"EN_TRANSITO",origin,destination,null,
                        rs.getBigDecimal("cantidad_enviada"),null),rs.getString("operacion_id"),rs.getLong("id_usuario_salida"),
                    rs.getString("salida_nombre"),rs.getString("observaciones"),true);
                List<Row> rows = new ArrayList<>(); rows.add(departure);
                if (rs.getObject("fecha_recepcion") != null && !"EN_TRANSITO".equals(rs.getString("estado"))) {
                    rows.add(new Row(rs.getLong("id_detalle_donacion"),new Event("RECEPCION_DISTRIBUCION",
                        rs.getObject("fecha_recepcion",LocalDateTime.class),rs.getString("estado"),origin,destination,null,
                        rs.getBigDecimal("cantidad_enviada"),rs.getBigDecimal("cantidad_recibida")),rs.getString("operacion_id"),
                        rs.getObject("id_usuario_recepcion",Long.class),rs.getString("recepcion_nombre"),rs.getString("observaciones_recepcion"),true));
                }
                return rows;
            }).list().stream().flatMap(List::stream).toList());
        // Extract only known fields from stored receipt JSON, never expose the JSON itself.
        result.addAll(jdbc.sql("""
            SELECT l.id_detalle_donacion, p.operacion_id,
              COALESCE(CAST(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(p.respuesta,'$.fechaHora')),'T',' ') AS DATETIME(6)),p.creado_en) fecha_confirmacion,
              j.cantidad,JSON_UNQUOTE(JSON_EXTRACT(p.respuesta,'$.centroAcopioId')) centro_id,
              c.nombre centro_nombre,c.ubicacion centro_direccion,u.id_usuario,
              CONCAT(u.nombre,' ',u.apellido) responsable,
              JSON_UNQUOTE(JSON_EXTRACT(p.respuesta,'$.observaciones')) notas
            FROM (
              SELECT id_donacion,operacion_id,creado_en,respuesta FROM recepcion_operacion WHERE id_donacion=:id
              UNION ALL
              SELECT id_donacion,uuid_decision,fecha_decision,respuesta_decision FROM recepcion_operacion
              WHERE id_donacion=:id AND decision='APROBADO' AND respuesta_decision IS NOT NULL
            ) p
            JOIN JSON_TABLE(p.respuesta,'$.detalles[*]' COLUMNS (
              recurso BIGINT PATH '$.recursoId', cantidad DECIMAL(14,3) PATH '$.cantidadRecibidaOperacion')) j ON TRUE
            JOIN detalle_donacion l ON l.id_donacion=p.id_donacion AND l.id_recurso=j.recurso
            LEFT JOIN registro_auditoria audit ON audit.entidad='DONACION' AND audit.accion='RECEPCION_DONACION'
              AND audit.id_registro=CAST(p.id_donacion AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci
              AND audit.fecha_hora=CAST(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(p.respuesta,'$.fechaHora')),'T',' ') AS DATETIME(6))
            LEFT JOIN centro_acopio c ON c.id_centro_acopio=COALESCE(
              NULLIF(JSON_UNQUOTE(JSON_EXTRACT(p.respuesta,'$.centroAcopioId')),'null'),
              NULLIF(JSON_UNQUOTE(JSON_EXTRACT(audit.datos_nuevos,'$.centroAcopioId')),'null'))
            LEFT JOIN usuario u ON u.id_usuario=JSON_UNQUOTE(JSON_EXTRACT(p.respuesta,'$.responsableId'))
            WHERE p.id_donacion=:id AND j.cantidad>0
            """).param("id",donationId).query((rs,n) -> new Row(rs.getLong("id_detalle_donacion"),
                new Event("RECEPCION_ACOPIO",rs.getObject("fecha_confirmacion",LocalDateTime.class),"RECIBIDA",null,
                    rs.getString("centro_nombre")==null ? null : new Place("ACOPIO",rs.getString("centro_nombre"),rs.getString("centro_direccion")),
                    null,null,rs.getBigDecimal("cantidad")),rs.getString("operacion_id"),rs.getObject("id_usuario",Long.class),
                rs.getString("responsable"),rs.getString("notas"),false)).list());
        result.addAll(jdbc.sql("""
            SELECT l.id_detalle_donacion,d.id_detalle_entrega,e.id_entrega,e.fecha_hora,e.observaciones,e.id_usuario_responsable,
              CONCAT(u.nombre,' ',u.apellido) responsable,z.nombre zona,d.cantidad_asignada,d.cantidad_entregada,d.origen_distribucion
            FROM detalle_entrega d JOIN entrega e ON e.id_entrega=d.id_entrega
            JOIN detalle_donacion l ON l.id_detalle_donacion=d.id_detalle_donacion
            JOIN necesidad n ON n.id_necesidad=d.id_necesidad JOIN zona_afectada z ON z.id_zona_afectada=n.id_zona_afectada
            JOIN usuario u ON u.id_usuario=e.id_usuario_responsable WHERE l.id_donacion=:id
            """).param("id",donationId).query((rs,n) -> {
                List<Row> rows=new ArrayList<>();
                for (String type : List.of("ASIGNACION_RECURSOS","ENTREGA_POBLACION")) {
                    BigDecimal quantity=rs.getBigDecimal(type.equals("ASIGNACION_RECURSOS") ? "cantidad_asignada" : "cantidad_entregada");
                    if (quantity.signum()>0) rows.add(new Row(rs.getLong("id_detalle_donacion"),
                        new Event(type,rs.getObject("fecha_hora",LocalDateTime.class),type.equals("ASIGNACION_RECURSOS") ? "ASIGNADA" : "ENTREGADA",
                            null,null,rs.getString("zona"),null,quantity),rs.getString("id_entrega"),rs.getLong("id_usuario_responsable"),
                        rs.getString("responsable"),rs.getString("observaciones"),rs.getBoolean("origen_distribucion"),rs.getString("id_detalle_entrega")));
                }
                return rows;
            }).list().stream().flatMap(List::stream).toList());
        return result;
    }
}
