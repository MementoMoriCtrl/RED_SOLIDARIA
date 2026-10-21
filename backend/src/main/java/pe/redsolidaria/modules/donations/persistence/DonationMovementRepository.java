package pe.redsolidaria.modules.donations.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.donations.domain.DonationMovement;

public interface DonationMovementRepository extends JpaRepository<DonationMovement, Long> {
    @EntityGraph(attributePaths = "responsable")
    List<DonationMovement> findAllByDonacion_IdOrderByFechaHoraAscIdAsc(Long donationId);
}
