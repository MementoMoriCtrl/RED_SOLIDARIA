package pe.redsolidaria.modules.donations.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.donations.domain.DonationLine;

public interface DonationLineRepository extends JpaRepository<DonationLine, Long> {}
