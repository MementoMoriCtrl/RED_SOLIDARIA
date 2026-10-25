package pe.redsolidaria.modules.resources.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.resources.domain.CollectionCenter;

public interface CollectionCenterRepository extends JpaRepository<CollectionCenter, Long> {
    org.springframework.data.domain.Page<CollectionCenter> findByNombreContainingIgnoreCaseOrderByNombreAscIdAsc(
            String nombre, org.springframework.data.domain.Pageable pageable);
}
