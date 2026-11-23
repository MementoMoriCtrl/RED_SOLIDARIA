package pe.redsolidaria.modules.reports;

import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ReportDates {
    private ReportDates() {}
    public static void validate(LocalDate from, LocalDate to) {
        if ((from != null && to != null && from.isAfter(to)) || LocalDate.MAX.equals(to))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rango de fechas no válido");
    }
}
