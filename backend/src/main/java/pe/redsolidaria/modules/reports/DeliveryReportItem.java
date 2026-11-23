package pe.redsolidaria.modules.reports;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Each row is a recorded event increment, not an accumulated balance. */
public record DeliveryReportItem(Long id, LocalDateTime fecha, String emergencia, String zona,
        Long necesidadId, String recurso, String unidad, String codigoDonacion,
        BigDecimal cantidadAsignada, BigDecimal cantidadEntregada) {}
