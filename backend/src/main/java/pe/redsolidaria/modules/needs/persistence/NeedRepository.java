package pe.redsolidaria.modules.needs.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.redsolidaria.modules.needs.domain.Need;

public interface NeedRepository extends JpaRepository<Need, Long> {
    Page<Need> findAllByZona_IdOrderByIdDesc(Long zoneId, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Need n where n.id = :id")
    Optional<Need> findByIdForUpdate(@Param("id") Long id);
}
