package pe.redsolidaria.modules.logistics;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferResolutionRepository extends JpaRepository<TransferResolution,String> {
    Optional<TransferResolution> findByTrasladoId(String trasladoId);
}
