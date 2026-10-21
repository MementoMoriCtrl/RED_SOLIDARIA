package pe.redsolidaria.modules.access.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.access.domain.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCodigo(String codigo);
    boolean existsByCodigo(String codigo);
    List<Role> findAllByOrderByIdAsc();
}
