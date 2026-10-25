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
                    SELECT id_detalle_donacion, SUM(cantidad_asignada) asignada
                    FROM detalle_entrega GROUP BY id_detalle_donacion
                ), receipts AS (
                    SELECT l.id_recurso, SUM(l.cantidad_recibida_acopio) recibida,
                        SUM(CASE WHEN d.estado IN ('REGISTRADA','RECIBIDA','ALMACENADA','CLASIFICADA')
                            THEN GREATEST(0, l.cantidad_recibida_acopio - COALESCE(a.asignada,0)) ELSE 0 END) disponible
                    FROM detalle_donacion l JOIN donacion d ON d.id_donacion = l.id_donacion
                    LEFT JOIN reservations a ON a.id_detalle_donacion = l.id_detalle_donacion
                    WHERE d.id_emergencia = :id GROUP BY l.id_recurso
                ), events AS (
                    SELECT n.id_recurso, SUM(l.cantidad_asignada) asignada, SUM(l.cantidad_entregada) entregada
                    FROM detalle_entrega l JOIN necesidad n ON n.id_necesidad = l.id_necesidad
                    JOIN zona_afectada z ON z.id_zona_afectada = n.id_zona_afectada
                    WHERE z.id_emergencia = :id GROUP BY n.id_recurso
                )
                SELECT r.id_recurso, r.nombre, u.codigo, COALESCE(n.requerida,0) requerida,
                    COALESCE(d.recibida,0) recibida, COALESCE(e.asignada,0) asignada, COALESCE(e.entregada,0) entregada,
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
                            required.subtract(delivered).max(BigDecimal.ZERO), pending.subtract(available).max(BigDecimal.ZERO));
                }).list();
    }
}
