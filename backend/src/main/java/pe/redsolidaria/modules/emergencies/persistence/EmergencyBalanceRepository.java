package pe.redsolidaria.modules.emergencies.persistence;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pe.redsolidaria.modules.emergencies.api.dto.EmergencyBalanceDtos.ResourceBalance;

@Repository
public class EmergencyBalanceRepository {
    private final JdbcClient jdbc;
    public EmergencyBalanceRepository(JdbcClient jdbc) { this.jdbc = jdbc; }
    public List<ResourceBalance> balance(long emergencyId) {
        // Aggregate each relation before joining: no receipt is multiplied by needs or delivery events.
        return jdbc.sql("""
                WITH requirements AS (
                    SELECT n.id_recurso, SUM(n.cantidad_requerida) requerida
                    FROM necesidad n JOIN zona_afectada z ON z.id_zona_afectada = n.id_zona_afectada
                    WHERE z.id_emergencia = :id GROUP BY n.id_recurso
                ), reservations AS (
                    SELECT id_detalle_donacion, SUM(CASE WHEN origen_distribucion=1 THEN cantidad_asignada ELSE 0 END) asignada
                    FROM detalle_entrega GROUP BY id_detalle_donacion
                ), transfers AS (
                    SELECT id_detalle_donacion,SUM(cantidad_enviada) trasladada,
                        COALESCE(SUM(cantidad_recibida),0) recibidaDistribucion,
                        SUM(CASE WHEN estado='EN_TRANSITO' THEN cantidad_enviada ELSE 0 END) enTransito,
                        SUM(CASE WHEN estado<>'EN_TRANSITO' THEN cantidad_enviada-cantidad_recibida ELSE 0 END) diferencia
                    FROM traslado_recurso GROUP BY id_detalle_donacion
                ), receipts AS (
                    SELECT l.id_recurso, SUM(l.cantidad_recibida_acopio) recibida,
                        SUM(COALESCE(t.trasladada,0)) trasladada, SUM(COALESCE(t.recibidaDistribucion,0)) recibidaDistribucion,
                        SUM(COALESCE(t.enTransito,0)) enTransito,SUM(COALESCE(t.diferencia,0)) diferencia,
                        SUM(CASE WHEN d.estado IN ('REGISTRADA','RECIBIDA','ALMACENADA','CLASIFICADA')
                            THEN GREATEST(0, COALESCE(t.recibidaDistribucion,0) - COALESCE(a.asignada,0)) ELSE 0 END) disponible
                    FROM detalle_donacion l JOIN donacion d ON d.id_donacion = l.id_donacion
                    LEFT JOIN transfers t ON t.id_detalle_donacion = l.id_detalle_donacion
                    LEFT JOIN reservations a ON a.id_detalle_donacion = l.id_detalle_donacion
                    WHERE d.id_emergencia = :id GROUP BY l.id_recurso
                ), events AS (
                    SELECT n.id_recurso, SUM(l.cantidad_asignada) asignada, SUM(l.cantidad_entregada) entregada
                    FROM detalle_entrega l JOIN necesidad n ON n.id_necesidad = l.id_necesidad
                    JOIN zona_afectada z ON z.id_zona_afectada = n.id_zona_afectada
                    WHERE z.id_emergencia = :id GROUP BY n.id_recurso
                )
                SELECT r.id_recurso, r.nombre, u.codigo, COALESCE(n.requerida,0) requerida,
                    COALESCE(d.recibida,0) recibida, COALESCE(d.trasladada,0) trasladada,
                    COALESCE(d.recibidaDistribucion,0) recibidaDistribucion,COALESCE(d.enTransito,0) enTransito,
                    COALESCE(d.diferencia,0) diferencia, COALESCE(e.asignada,0) asignada, COALESCE(e.entregada,0) entregada,
                    CASE WHEN r.estado = 'ACTIVO' THEN COALESCE(d.disponible,0) ELSE 0 END disponible
                FROM recurso r JOIN unidad_medida u ON u.id_unidad_medida = r.id_unidad_medida
                LEFT JOIN requirements n ON n.id_recurso = r.id_recurso
                LEFT JOIN receipts d ON d.id_recurso = r.id_recurso
                LEFT JOIN events e ON e.id_recurso = r.id_recurso
                WHERE n.id_recurso IS NOT NULL OR d.id_recurso IS NOT NULL
                ORDER BY r.nombre, r.id_recurso
                """).param("id", emergencyId).query((rs, row) -> {
                    var required = rs.getBigDecimal("requerida");
                    var assigned = rs.getBigDecimal("asignada");
                    var delivered = rs.getBigDecimal("entregada");
                    var available = rs.getBigDecimal("disponible");
                    var pending = required.subtract(assigned).max(BigDecimal.ZERO);
                    return new ResourceBalance(rs.getLong("id_recurso"), rs.getString("nombre"), rs.getString("codigo"),
                            required, rs.getBigDecimal("recibida"), assigned, delivered, available, pending,
                            required.subtract(delivered).max(BigDecimal.ZERO), pending.subtract(available).max(BigDecimal.ZERO),rs.getBigDecimal("trasladada"),
                            rs.getBigDecimal("recibidaDistribucion"),rs.getBigDecimal("enTransito"),rs.getBigDecimal("diferencia"));
                }).list();
    }
}
