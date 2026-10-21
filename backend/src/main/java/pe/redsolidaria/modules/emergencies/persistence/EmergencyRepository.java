package pe.redsolidaria.modules.emergencies.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.emergencies.domain.Emergency;

public interface EmergencyRepository extends JpaRepository<Emergency, Long> {}
