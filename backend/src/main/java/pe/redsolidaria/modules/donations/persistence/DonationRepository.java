package pe.redsolidaria.modules.donations.persistence;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import pe.redsolidaria.modules.donations.domain.Donation;

public interface DonationRepository extends JpaRepository<Donation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Donation d where d.id = :id")
    Optional<Donation> findByIdForUpdate(@Param("id") Long id);
    boolean existsByCodigoTrazabilidad(String codigoTrazabilidad);

    @EntityGraph(attributePaths = {
            "donante", "emergencia", "centroAcopio", "detalles",
            "detalles.recurso", "detalles.recurso.unidadMedida"
    })
    Optional<Donation> findByCodigoTrazabilidad(String codigoTrazabilidad);

    @EntityGraph(attributePaths = {"donante", "emergencia", "centroAcopio"})
    @Query("""
            select d from Donation d
            where (:codigo is null or lower(d.codigoTrazabilidad) like lower(concat('%', :codigo, '%')))
              and (:estado is null or upper(d.estado) = upper(:estado))
            order by d.fechaRegistro desc, d.id desc
            """)
    List<Donation> findAdministrativeList(@Param("codigo") String codigo, @Param("estado") String estado);

    @EntityGraph(attributePaths = {
            "donante", "emergencia", "centroAcopio", "detalles",
            "detalles.recurso", "detalles.recurso.unidadMedida"
    })
    @Query("select d from Donation d where d.id = :id")
    Optional<Donation> findAdministrativeById(@Param("id") Long id);
}
