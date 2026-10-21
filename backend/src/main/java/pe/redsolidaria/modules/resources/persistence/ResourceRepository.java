package pe.redsolidaria.modules.resources.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import pe.redsolidaria.modules.resources.domain.Resource;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
    @EntityGraph(attributePaths = "unidadMedida")
    List<Resource> findAllByOrderByNombreAsc();
}
