package pe.redsolidaria.modules.reports;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.donations.application.DonationQueryService;
import pe.redsolidaria.modules.donations.persistence.DonationRepository;
import pe.redsolidaria.modules.emergencies.application.EmergencyManagementService;
import pe.redsolidaria.modules.emergencies.persistence.EmergencyRepository;

class ReportFilterTest {
    @Test void donationRangeUsesExclusiveDayAfterEnd() {
        var repository = mock(DonationRepository.class);
        when(repository.findAdministrativeListByDate(any(), any(), any(), any())).thenReturn(List.of());
        var service = new DonationQueryService(repository, mock(), mock());
        assertTrue(service.listAdministrative(" RS ", "registrada", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31")).isEmpty());
        verify(repository).findAdministrativeListByDate("RS", "REGISTRADA", LocalDateTime.parse("2026-01-01T00:00:00"), LocalDateTime.parse("2026-02-01T00:00:00"));
        assertThrows(ResponseStatusException.class, () -> service.listAdministrative(null, null, LocalDate.parse("2026-02-01"), LocalDate.parse("2026-01-01")));
    }
    @Test void emergencyDatesAndPageArePassedToRepository() {
        var repository = mock(EmergencyRepository.class);
        when(repository.searchByDate(any(), any(), any(), any(), any())).thenReturn(Page.empty());
        var service = new EmergencyManagementService(repository, mock(), mock(), mock(), mock());
        service.list("flood", "ACTIVA", 1, 20, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"));
        verify(repository).searchByDate(eq("flood"), eq("ACTIVA"), eq(LocalDate.parse("2026-01-01")), eq(LocalDate.parse("2026-01-31")),
                argThat(page -> page.getPageNumber() == 1 && page.getPageSize() == 20));
        assertThrows(ResponseStatusException.class, () -> service.list("", "", 0, 20, LocalDate.parse("2026-02-01"), LocalDate.parse("2026-01-01")));
    }
}
