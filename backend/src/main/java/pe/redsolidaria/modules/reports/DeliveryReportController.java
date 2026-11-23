package pe.redsolidaria.modules.reports;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reportes/entregas")
public class DeliveryReportController {
    private final DeliveryReportRepository reports;
    public DeliveryReportController(DeliveryReportRepository reports) { this.reports = reports; }

    public record DeliveryPage(List<DeliveryReportItem> content, int page, int size,
            long totalElements, int totalPages, boolean last) {}

    @GetMapping
    @Transactional(readOnly = true)
    public DeliveryPage list(@RequestParam(required = false) LocalDate fechaDesde,
            @RequestParam(required = false) LocalDate fechaHasta,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        ReportDates.validate(fechaDesde, fechaHasta);
        var result = reports.findDeliveries(fechaDesde == null ? null : fechaDesde.atStartOfDay(),
                fechaHasta == null ? null : fechaHasta.plusDays(1).atStartOfDay(), PageRequest.of(page, size));
        return new DeliveryPage(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast());
    }
}
