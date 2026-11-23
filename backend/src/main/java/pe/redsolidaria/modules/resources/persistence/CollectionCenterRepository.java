package pe.redsolidaria.modules.resources.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.resources.domain.CollectionCenter;

public interface CollectionCenterRepository extends JpaRepository<CollectionCenter, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from CollectionCenter c where c.id = :id")
    java.util.Optional<CollectionCenter> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    org.springframework.data.domain.Page<CollectionCenter> findByNombreContainingIgnoreCaseOrderByNombreAscIdAsc(
            String nombre, org.springframework.data.domain.Pageable pageable);
}
