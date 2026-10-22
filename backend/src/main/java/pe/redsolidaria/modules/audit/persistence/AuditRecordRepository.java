package pe.redsolidaria.modules.audit.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.audit.domain.AuditRecord;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {}
