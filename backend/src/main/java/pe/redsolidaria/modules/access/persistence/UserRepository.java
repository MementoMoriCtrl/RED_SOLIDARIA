package pe.redsolidaria.modules.access.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import pe.redsolidaria.modules.access.domain.UserAccount;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
    // Common lock for direct account changes and changes to the role currently used by the master.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.adminMaestro = true")
    Optional<UserAccount> findMarkedMasterForUpdate();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findByIdForUpdate(@Param("id") Long id);
    @EntityGraph(attributePaths = {"rol", "rol.asignaciones", "rol.asignaciones.permiso"})
    Optional<UserAccount> findByCorreo(String correo);
    boolean existsByCorreo(String correo);
    boolean existsByCorreoAndIdNot(String correo, Long id);
    List<UserAccount> findAllByOrderByIdAsc();

    @Query("""
            select case when count(u) > 0 then true else false end
            from UserAccount u
            join u.rol r
            join r.asignaciones rp
            join rp.permiso p
            where upper(u.estado) = upper(:estado) and p.codigo = :permiso
            """)
    boolean existsAdministratorWithPermission(@Param("estado") String estado, @Param("permiso") String permiso);
}
