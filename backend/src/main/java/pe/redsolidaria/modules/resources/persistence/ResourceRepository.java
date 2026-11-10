package pe.redsolidaria.modules.resources.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import pe.redsolidaria.modules.resources.domain.Resource;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
    @org.springframework.data.jpa.repository.Query(value = "SELECT (EXISTS(SELECT 1 FROM detalle_donacion WHERE id_recurso=:id) OR EXISTS(SELECT 1 FROM necesidad WHERE id_recurso=:id))", nativeQuery = true)
    long historyCount(@org.springframework.data.repository.query.Param("id") long id);
    @EntityGraph(attributePaths = "unidadMedida")
    List<Resource> findAllByOrderByNombreAsc();
}
