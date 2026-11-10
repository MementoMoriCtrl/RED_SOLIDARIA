package pe.redsolidaria.modules.logistics;

import static pe.redsolidaria.modules.logistics.LogisticsDtos.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LogisticsRepository {
    private final JdbcClient jdbc;
    public LogisticsRepository(JdbcClient jdbc) { this.jdbc = jdbc; }
    public boolean hasOtherDestination(long source, long destination) {
        return jdbc.sql("SELECT COUNT(*) FROM traslado_recurso WHERE id_detalle_donacion=:source AND id_centro_distribucion<>:destination")
            .param("source",source).param("destination",destination).query(Long.class).single() > 0;
    }
    public BigDecimal received(long source) {
        return jdbc.sql("SELECT COALESCE(SUM(cantidad_recibida),0) FROM traslado_recurso WHERE id_detalle_donacion=:id")
                .param("id", source).query(BigDecimal.class).single();
    }
    public BigDecimal sent(long source) {
        return jdbc.sql("SELECT COALESCE(SUM(cantidad_enviada),0) FROM traslado_recurso WHERE id_detalle_donacion=:id")
                .param("id", source).query(BigDecimal.class).single();
    }
    private static final String TRANSFER = """
        SELECT t.operacion_id operacionId, t.id_detalle_donacion detalleDonacionId,
        t.id_centro_acopio centroAcopioId, t.id_centro_distribucion centroDistribucionId,
        a.nombre origen, c.nombre destino, r.nombre recurso, u.codigo unidad,
        t.cantidad_enviada cantidadEnviada, t.cantidad_recibida cantidadRecibida, t.estado,
        t.fecha_salida fechaSalida, t.fecha_recepcion fechaRecepcion,
        t.id_usuario_salida responsableSalida, t.id_usuario_recepcion responsableRecepcion,
        t.observaciones, t.observaciones_recepcion observacionesRecepcion,
        t.operacion_recepcion operacionRecepcion, t.huella_salida huellaSalida, t.huella_recepcion huellaRecepcion
        FROM traslado_recurso t JOIN centro_acopio a ON a.id_centro_acopio=t.id_centro_acopio
        JOIN centro_distribucion c ON c.id_centro_distribucion=t.id_centro_distribucion
        JOIN detalle_donacion l ON l.id_detalle_donacion=t.id_detalle_donacion
        JOIN recurso r ON r.id_recurso=l.id_recurso JOIN unidad_medida u ON u.id_unidad_medida=t.id_unidad_medida
        """;
    public Optional<Transfer> transfer(String id) {
        return jdbc.sql(TRANSFER + " WHERE t.operacion_id=:id").param("id",id).query(Transfer.class).optional();
    }
    public List<Transfer> history(int page) {
        return jdbc.sql(TRANSFER + " ORDER BY t.fecha_salida DESC,t.operacion_id LIMIT 20 OFFSET :offset")
            .param("offset",page * 20L).query(Transfer.class).list();
    }
    public List<Center> centers() {
        return jdbc.sql("SELECT id_centro_distribucion id,nombre,ubicacion,descripcion FROM centro_distribucion ORDER BY id_centro_distribucion")
            .query(Center.class).list();
    }
    public long createCenter(SaveCenter request) {
        var keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.sql("INSERT INTO centro_distribucion(nombre,ubicacion,descripcion) VALUES (:name,:location,:description)")
            .param("name",request.nombre().trim()).param("location",request.ubicacion()).param("description",request.descripcion()).update(keys);
        return keys.getKey().longValue();
    }
    public int updateCenter(long id, SaveCenter request) {
        return jdbc.sql("UPDATE centro_distribucion SET nombre=:name,ubicacion=:location,descripcion=:description WHERE id_centro_distribucion=:id")
            .param("id",id).param("name",request.nombre().trim()).param("location",request.ubicacion()).param("description",request.descripcion()).update();
    }
    public void dispatch(Dispatch request, long origin, long unit, long actor, String fingerprint) {
        jdbc.sql("""
            INSERT INTO traslado_recurso(operacion_id,id_detalle_donacion,id_centro_acopio,id_centro_distribucion,
            id_unidad_medida,cantidad_enviada,id_usuario_salida,estado,observaciones,huella_salida)
            VALUES (:id,:source,:origin,:destination,:unit,:quantity,:actor,'EN_TRANSITO',:notes,:fingerprint)
            """).param("id",request.operacionId().toString()).param("source",request.detalleDonacionId())
            .param("origin",origin).param("destination",request.centroDistribucionId()).param("unit",unit)
            .param("quantity",request.cantidad()).param("actor",actor).param("notes",request.observaciones())
            .param("fingerprint",fingerprint).update();
    }
    public void receive(String id, Receive request, long actor, String fingerprint, String state) {
        jdbc.sql("""
            UPDATE traslado_recurso SET cantidad_recibida=:quantity, id_usuario_recepcion=:actor,
            fecha_recepcion=CURRENT_TIMESTAMP(6), estado=:state, observaciones_recepcion=:notes,
            operacion_recepcion=:operation,huella_recepcion=:fingerprint WHERE operacion_id=:id
            """).param("id",id).param("quantity",request.cantidad()).param("actor",actor).param("state",state)
            .param("notes",request.observaciones()).param("operation",request.operacionId().toString()).param("fingerprint",fingerprint).update();
    }
    public List<Stock> stock(int page) {
        return jdbc.sql("""
            SELECT l.id_detalle_donacion detalleDonacionId,d.codigo_trazabilidad codigoTrazabilidad,
            d.id_centro_acopio centroAcopioId,c.nombre centroAcopio,r.nombre recurso,u.codigo unidad,
            l.cantidad_recibida_acopio recibidaAcopio,COALESCE(t.enviada,0) trasladada,
            COALESCE(t.recibida,0) recibidaDistribucion,COALESCE(e.asignada,0) asignada,
            GREATEST(0,l.cantidad_recibida_acopio-COALESCE(t.enviada,0)-COALESCE(e.historica,0)) disponibleAcopio,
            GREATEST(0,COALESCE(t.recibida,0)-COALESCE(e.nueva,0)) disponibleDistribucion,
            COALESCE(t.transito,0) enTransito,COALESCE(t.diferencia,0) diferencia,
            COALESCE(e.historica,0) reservaHistoricaSinDistribucion
            FROM detalle_donacion l JOIN donacion d ON d.id_donacion=l.id_donacion
            JOIN recurso r ON r.id_recurso=l.id_recurso JOIN unidad_medida u ON u.id_unidad_medida=r.id_unidad_medida
            LEFT JOIN centro_acopio c ON c.id_centro_acopio=d.id_centro_acopio
            LEFT JOIN (SELECT id_detalle_donacion,SUM(cantidad_enviada) enviada,SUM(cantidad_recibida) recibida,
              SUM(CASE WHEN estado='EN_TRANSITO' THEN cantidad_enviada ELSE 0 END) transito,
              SUM(CASE WHEN estado<>'EN_TRANSITO' THEN cantidad_enviada-cantidad_recibida ELSE 0 END) diferencia
              FROM traslado_recurso GROUP BY id_detalle_donacion) t ON t.id_detalle_donacion=l.id_detalle_donacion
            LEFT JOIN (SELECT id_detalle_donacion,SUM(cantidad_asignada) asignada,
              SUM(CASE WHEN origen_distribucion=0 THEN cantidad_asignada ELSE 0 END) historica,
              SUM(CASE WHEN origen_distribucion=1 THEN cantidad_asignada ELSE 0 END) nueva
              FROM detalle_entrega GROUP BY id_detalle_donacion) e ON e.id_detalle_donacion=l.id_detalle_donacion
            ORDER BY l.id_detalle_donacion DESC LIMIT 20 OFFSET :offset
            """).param("offset",page * 20L).query(Stock.class).list();
    }
}
