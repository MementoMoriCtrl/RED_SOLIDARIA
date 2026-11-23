package pe.redsolidaria.modules.reports;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.redsolidaria.modules.deliveries.domain.DeliveryLine;

public interface DeliveryReportRepository extends JpaRepository<DeliveryLine, Long> {
    @Query(value = """
            select new pe.redsolidaria.modules.reports.DeliveryReportItem(l.id, l.entrega.fechaHora,
                l.necesidad.zona.emergencia.nombre, l.necesidad.zona.nombre, l.necesidad.id,
                l.necesidad.recurso.nombre, l.necesidad.recurso.unidadMedida.codigo,
                l.detalleDonacion.donacion.codigoTrazabilidad, l.cantidadAsignada, l.cantidadEntregada)
            from DeliveryLine l
            where l.cantidadEntregada > 0 and (:desde is null or l.entrega.fechaHora >= :desde)
              and (:hasta is null or l.entrega.fechaHora < :hasta)
            order by l.entrega.fechaHora desc, l.id desc
            """, countQuery = """
            select count(l) from DeliveryLine l where l.cantidadEntregada > 0
              and (:desde is null or l.entrega.fechaHora >= :desde)
              and (:hasta is null or l.entrega.fechaHora < :hasta)
            """)
    Page<DeliveryReportItem> findDeliveries(@Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta, Pageable pageable);
}
