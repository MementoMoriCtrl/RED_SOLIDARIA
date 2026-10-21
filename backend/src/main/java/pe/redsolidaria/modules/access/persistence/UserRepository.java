package pe.redsolidaria.modules.access.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.redsolidaria.modules.access.domain.UserAccount;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
    @EntityGraph(attributePaths = {"rol", "rol.asignaciones", "rol.asignaciones.permiso"})
    Optional<UserAccount> findByCorreo(String correo);
    boolean existsByCorreo(String correo);
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
