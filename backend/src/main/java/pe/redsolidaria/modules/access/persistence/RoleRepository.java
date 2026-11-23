package pe.redsolidaria.modules.access.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import pe.redsolidaria.modules.access.domain.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCodigo(String codigo);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.id = :id")
    Optional<Role> findByIdForUpdate(@Param("id") Long id);
    boolean existsByCodigo(String codigo);
    List<Role> findAllByOrderByIdAsc();
}
