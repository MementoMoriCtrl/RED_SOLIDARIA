package pe.redsolidaria.modules.access.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.access.domain.Permission;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCodigo(String codigo);
    boolean existsByCodigo(String codigo);
    List<Permission> findAllByOrderByIdAsc();
}
