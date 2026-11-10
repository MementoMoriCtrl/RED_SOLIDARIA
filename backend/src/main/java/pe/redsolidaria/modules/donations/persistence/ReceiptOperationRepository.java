package pe.redsolidaria.modules.donations.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.donations.domain.ReceiptOperation;

public interface ReceiptOperationRepository extends JpaRepository<ReceiptOperation, Long> {
    org.springframework.data.domain.Page<ReceiptOperation> findAllByDonacion_IdOrderByIdDesc(Long donationId, org.springframework.data.domain.Pageable pageable);
    Optional<ReceiptOperation> findByDonacion_IdAndOperacionId(Long donationId, String operationId);
}
