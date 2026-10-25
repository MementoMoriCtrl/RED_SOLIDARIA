package pe.redsolidaria.modules.deliveries.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.redsolidaria.modules.deliveries.domain.Delivery;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {}
