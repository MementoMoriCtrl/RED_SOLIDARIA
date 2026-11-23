package pe.redsolidaria.modules.resources.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.resources.domain.MeasurementUnit;

public interface MeasurementUnitRepository extends JpaRepository<MeasurementUnit, Long> {
    List<MeasurementUnit> findAllByOrderByNombreAsc();
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from MeasurementUnit u where u.id = :id")
    java.util.Optional<MeasurementUnit> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
