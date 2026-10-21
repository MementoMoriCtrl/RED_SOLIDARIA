package pe.redsolidaria.modules.resources.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.resources.domain.Resource;

public interface ResourceRepository extends JpaRepository<Resource, Long> {}
