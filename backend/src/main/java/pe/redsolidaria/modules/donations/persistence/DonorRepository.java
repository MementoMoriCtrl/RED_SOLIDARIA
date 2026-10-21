package pe.redsolidaria.modules.donations.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.donations.domain.Donor;

public interface DonorRepository extends JpaRepository<Donor, Long> {}
