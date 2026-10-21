package pe.redsolidaria.modules.donations.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.donations.domain.Donation;

public interface DonationRepository extends JpaRepository<Donation, Long> {
    boolean existsByCodigoTrazabilidad(String codigoTrazabilidad);

    @EntityGraph(attributePaths = {
            "donante", "emergencia", "centroAcopio", "detalles",
            "detalles.recurso", "detalles.recurso.unidadMedida"
    })
    Optional<Donation> findByCodigoTrazabilidad(String codigoTrazabilidad);
}
