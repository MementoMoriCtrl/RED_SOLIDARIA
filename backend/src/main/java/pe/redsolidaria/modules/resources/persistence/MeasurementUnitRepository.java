package pe.redsolidaria.modules.resources.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.resources.domain.MeasurementUnit;

public interface MeasurementUnitRepository extends JpaRepository<MeasurementUnit, Long> {
    List<MeasurementUnit> findAllByOrderByNombreAsc();
}
