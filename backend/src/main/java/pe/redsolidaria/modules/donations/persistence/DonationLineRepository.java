package pe.redsolidaria.modules.donations.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.donations.domain.DonationLine;

public interface DonationLineRepository extends JpaRepository<DonationLine, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select l from DonationLine l join fetch l.donacion d
            where d.emergencia.id = :emergencyId and l.recurso.id = :resourceId
              and (l.cantidadRecibidaAcopio > 0 or exists (
                  select x.id from DeliveryLine x where x.detalleDonacion = l))
            order by l.id
            """)
    org.springframework.data.domain.Page<DonationLine> findSources(
            @org.springframework.data.repository.query.Param("emergencyId") Long emergencyId,
            @org.springframework.data.repository.query.Param("resourceId") Long resourceId,
            org.springframework.data.domain.Pageable pageable);
}
