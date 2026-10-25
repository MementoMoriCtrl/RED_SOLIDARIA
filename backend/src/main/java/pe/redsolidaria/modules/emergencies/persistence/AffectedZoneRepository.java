package pe.redsolidaria.modules.emergencies.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.emergencies.domain.AffectedZone;

public interface AffectedZoneRepository extends JpaRepository<AffectedZone, Long> {
    List<AffectedZone> findAllByEmergencia_IdOrderByIdAsc(Long emergencyId);
    Optional<AffectedZone> findByIdAndEmergencia_Id(Long id, Long emergencyId);
}
