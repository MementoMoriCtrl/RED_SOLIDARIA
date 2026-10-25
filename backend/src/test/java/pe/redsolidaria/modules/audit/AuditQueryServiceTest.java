package pe.redsolidaria.modules.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.audit.application.AuditQueryService;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;

class AuditQueryServiceTest {
    AuditRecordRepository records = org.mockito.Mockito.mock(AuditRecordRepository.class);
    AuditQueryService service = new AuditQueryService(records);

    @Test void normalizesFiltersAndRequestsStableDescendingPagination() {
        when(records.search(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 10), 25));
        var response = service.search(" RECEPCION_DONACION ", " DONACION ", " 22 ", " Ana ",
                null, null, 2, 10);

        assertEquals(2, response.page());
        assertEquals(10, response.size());
        assertEquals(25, response.totalElements());
        assertEquals(3, response.totalPages());
        assertEquals(true, response.last());
        var sort = Sort.by(Sort.Order.desc("fechaHora"), Sort.Order.desc("id"));
        verify(records).search(eq("RECEPCION_DONACION"), eq("DONACION"), eq("22"), eq("Ana"),
                isNull(), isNull(), argThat(pageable -> pageable.getPageNumber() == 2
                        && pageable.getPageSize() == 10 && pageable.getSort().equals(sort)));
    }

    @Test void rejectsInvertedDateRangeBeforeQuery() {
        assertThrows(ResponseStatusException.class, () -> service.search(null, null, null, null,
                LocalDateTime.parse("2026-05-02T00:00:00"), LocalDateTime.parse("2026-05-01T00:00:00"), 0, 20));
        verifyNoInteractions(records);
    }

    @Test void forwardsAnIndividualActionAndDateRangeFilter() {
        LocalDateTime from = LocalDateTime.parse("2026-05-01T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-05-03T23:59:59");
        when(records.search(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.search("RECEPCION_DONACION", null, null, null, from, to, 0, 20);

        verify(records).search(eq("RECEPCION_DONACION"), isNull(), isNull(), isNull(), eq(from), eq(to), any());
    }
}
