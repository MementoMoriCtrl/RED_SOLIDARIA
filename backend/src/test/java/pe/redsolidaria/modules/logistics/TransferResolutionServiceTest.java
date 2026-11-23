package pe.redsolidaria.modules.logistics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import tools.jackson.databind.json.JsonMapper;

class TransferResolutionServiceTest {
    final LogisticsRepository logistics=mock(LogisticsRepository.class);
    final TransferResolutionRepository resolutions=mock(TransferResolutionRepository.class);
    final UserRepository users=mock(UserRepository.class);
    final AuditRecordRepository audit=mock(AuditRecordRepository.class);
    final UserAccount actor=mock(UserAccount.class);
    final TransferResolutionService service=new TransferResolutionService(logistics,resolutions,users,audit,JsonMapper.builder().build());
    final UUID operation=UUID.randomUUID();
    ResolutionDtos.Request request() { return new ResolutionDtos.Request(operation,"PERDIDA"," Acta "," evidencia "); }
    LogisticsDtos.Transfer transfer(String state) { return new LogisticsDtos.Transfer("traslado",10,1,2,"A","D","Arroz","KG",
        new BigDecimal("80"),new BigDecimal("75"),state,null,null,1L,2L,null,null,null,"hash",null); }
    void setup(String state) {
        when(logistics.transferForUpdate("traslado")).thenReturn(Optional.of(transfer(state)));
        when(users.findByCorreo("admin@test")).thenReturn(Optional.of(actor)); when(actor.getId()).thenReturn(9L);
    }
    @Test void recordsSnapshotAndAuditWithoutChangingTransferOrStock() {
        setup("RECIBIDO_CON_DIFERENCIA");
        var result=service.resolve("traslado",request(),"ADMIN@test");
        assertEquals(new BigDecimal("5"),result.diferencia()); assertEquals("Acta",result.motivo()); assertEquals(9L,result.actorId());
        var order=inOrder(logistics,resolutions,audit); order.verify(logistics).transferForUpdate("traslado");
        order.verify(resolutions).findByTrasladoId("traslado"); order.verify(logistics).insertResolution(any()); order.verify(audit).saveAndFlush(any());
        verifyNoMoreInteractions(logistics); verify(audit,times(1)).saveAndFlush(any());
    }
    @Test void replayIsIdempotentAndDifferentRequestsCannotOverwrite() {
        setup("RECIBIDO_CON_DIFERENCIA");
        var old=new TransferResolution(operation.toString(),"traslado",9L,java.time.LocalDateTime.now(),"PERDIDA","Acta","evidencia",new BigDecimal("80"),new BigDecimal("75"));
        when(resolutions.findByTrasladoId("traslado")).thenReturn(Optional.of(old));
        assertEquals(operation.toString(),service.resolve("traslado",request(),"admin@test").operacionId());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.resolve("traslado",new ResolutionDtos.Request(operation,"OTRO","Acta","evidencia"),"admin@test")).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->service.resolve("traslado",new ResolutionDtos.Request(UUID.randomUUID(),"PERDIDA","Acta","evidencia"),"admin@test"));
        verify(logistics,never()).insertResolution(any()); verifyNoInteractions(audit);
    }
    @Test void rejectsWrongStateAndReusedUuid() {
        setup("EN_TRANSITO"); assertThrows(ResponseStatusException.class,()->service.resolve("traslado",request(),"admin@test"));
        setup("RECIBIDO_CON_DIFERENCIA"); when(resolutions.existsById(operation.toString())).thenReturn(true);
        assertThrows(ResponseStatusException.class,()->service.resolve("traslado",request(),"admin@test"));
        verify(logistics,never()).insertResolution(any()); verifyNoInteractions(audit);
    }
    @Test void simultaneousReuseOfUuidAcrossDifferentTransfersCannotOverwrite() throws Exception {
        when(logistics.transferForUpdate(anyString())).thenReturn(Optional.of(transfer("RECIBIDO_CON_DIFERENCIA")));
        when(users.findByCorreo("admin@test")).thenReturn(Optional.of(actor)); when(actor.getId()).thenReturn(9L);
        var barrier=new CyclicBarrier(2);
        var inserted=new ConcurrentHashMap<String,ResolutionDtos.Resolution>();
        when(resolutions.existsById(operation.toString())).thenAnswer(call -> { barrier.await(5,TimeUnit.SECONDS); return false; });
        doAnswer(call -> {
            ResolutionDtos.Resolution row=call.getArgument(0);
            if (inserted.putIfAbsent(row.operacionId(),row)!=null)
                throw new org.springframework.dao.DuplicateKeyException("duplicate operation");
            return null;
        }).when(logistics).insertResolution(any());
        var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> first=() -> status("traslado-a");
            Callable<Integer> second=() -> status("traslado-b");
            var a=pool.submit(first); var b=pool.submit(second);
            assertEquals(Set.of(200,409),Set.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)));
            assertEquals(1,inserted.size());
            assertTrue(Set.of("traslado-a","traslado-b").contains(inserted.get(operation.toString()).trasladoId()));
            verify(logistics,times(2)).insertResolution(any()); verify(audit,times(1)).saveAndFlush(any());
            verify(resolutions,never()).saveAndFlush(any());
        } finally { pool.shutdownNow(); }
    }
    private int status(String id) {
        try { service.resolve(id,request(),"admin@test"); return 200; }
        catch (ResponseStatusException e) { return e.getStatusCode().value(); }
    }
}
