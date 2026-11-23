package pe.redsolidaria.modules.donations.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.donations.domain.Donor;

public interface DonorRepository extends JpaRepository<Donor, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from Donor d where d.id = :id")
    java.util.Optional<Donor> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    org.springframework.data.domain.Page<Donor> findByNombreContainingIgnoreCaseOrderByNombreAscIdAsc(
            String nombre, org.springframework.data.domain.Pageable pageable);
}
