package pe.redsolidaria.modules.deliveries.persistence;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.redsolidaria.modules.deliveries.domain.DeliveryLine;

public interface DeliveryLineRepository extends JpaRepository<DeliveryLine, Long> {
    @Query("select coalesce(sum(l.cantidadAsignada), 0) from DeliveryLine l where l.detalleDonacion.id = :id and l.origenDistribucion = false")
    BigDecimal historicalAssigned(@Param("id") Long id);
    @Query("select coalesce(sum(l.cantidadAsignada), 0) from DeliveryLine l where l.detalleDonacion.id = :id and l.origenDistribucion = true")
    BigDecimal distributionAssigned(@Param("id") Long id);
    @Query("select coalesce(sum(l.cantidadEntregada), 0) from DeliveryLine l where l.detalleDonacion.id = :id and l.origenDistribucion = true")
    BigDecimal distributionDelivered(@Param("id") Long id);
    @Query("select coalesce(sum(l.cantidadAsignada), 0) from DeliveryLine l where l.detalleDonacion.id = :id")
    BigDecimal assignedToDonation(@Param("id") Long id);
    @Query("select coalesce(sum(l.cantidadAsignada), 0) from DeliveryLine l where l.necesidad.id = :id")
    BigDecimal assignedToNeed(@Param("id") Long id);
    @Query("select coalesce(sum(l.cantidadEntregada), 0) from DeliveryLine l where l.necesidad.id = :id")
    BigDecimal deliveredToNeed(@Param("id") Long id);
    @Query("select l.detalleDonacion.id from DeliveryLine l where l.necesidad.id = :id group by l.detalleDonacion.id having sum(l.cantidadAsignada) > sum(l.cantidadEntregada)")
    List<Long> sourcesWithPendingReservations(@Param("id") Long id);
    @Query("select coalesce(sum(l.cantidadAsignada), 0) from DeliveryLine l where l.necesidad.id = :needId and l.detalleDonacion.id = :lineId")
    BigDecimal assignedToPair(@Param("needId") Long needId, @Param("lineId") Long lineId);
    @Query("select coalesce(sum(l.cantidadEntregada), 0) from DeliveryLine l where l.necesidad.id = :needId and l.detalleDonacion.id = :lineId")
    BigDecimal deliveredToPair(@Param("needId") Long needId, @Param("lineId") Long lineId);
    @Query("select coalesce(sum(l.cantidadAsignada), 0) from DeliveryLine l where l.necesidad.id = :needId and l.detalleDonacion.id = :lineId and l.origenDistribucion = true")
    BigDecimal distributionAssignedToPair(@Param("needId") Long needId, @Param("lineId") Long lineId);
    @Query("select coalesce(sum(l.cantidadEntregada), 0) from DeliveryLine l where l.necesidad.id = :needId and l.detalleDonacion.id = :lineId and l.origenDistribucion = true")
    BigDecimal distributionDeliveredToPair(@Param("needId") Long needId, @Param("lineId") Long lineId);
}
