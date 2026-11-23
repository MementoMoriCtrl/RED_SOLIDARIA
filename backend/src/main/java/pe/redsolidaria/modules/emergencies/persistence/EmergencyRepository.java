package pe.redsolidaria.modules.emergencies.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.redsolidaria.modules.emergencies.domain.Emergency;

public interface EmergencyRepository extends JpaRepository<Emergency, Long> {
    @Query(value = """
            select e from Emergency e
            where (:estado is null or upper(e.estado) = upper(:estado))
              and (:texto is null or lower(e.nombre) like lower(concat('%', :texto, '%'))
                   or lower(e.tipo) like lower(concat('%', :texto, '%')))
            """,
            countQuery = """
            select count(e) from Emergency e
            where (:estado is null or upper(e.estado) = upper(:estado))
              and (:texto is null or lower(e.nombre) like lower(concat('%', :texto, '%'))
                   or lower(e.tipo) like lower(concat('%', :texto, '%')))
            """)
    Page<Emergency> search(@Param("texto") String texto, @Param("estado") String estado, Pageable pageable);

    @Query("""
            select e from Emergency e where (:estado is null or upper(e.estado) = upper(:estado))
              and (:texto is null or lower(e.nombre) like lower(concat('%', :texto, '%'))
                   or lower(e.tipo) like lower(concat('%', :texto, '%')))
              and (:desde is null or e.fechaInicio >= :desde)
              and (:hasta is null or e.fechaInicio <= :hasta)
            """)
    Page<Emergency> searchByDate(@Param("texto") String texto, @Param("estado") String estado,
            @Param("desde") java.time.LocalDate desde, @Param("hasta") java.time.LocalDate hasta, Pageable pageable);
}
