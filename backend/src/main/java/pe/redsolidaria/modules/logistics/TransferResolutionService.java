package pe.redsolidaria.modules.logistics;

import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TransferResolutionService {
    private final LogisticsRepository logistics;
    private final TransferResolutionRepository resolutions;
    private final UserRepository users;
    private final AuditRecordRepository audit;
    private final JsonMapper json;
    public TransferResolutionService(LogisticsRepository logistics, TransferResolutionRepository resolutions, UserRepository users,
                                     AuditRecordRepository audit, JsonMapper json) {
        this.logistics=logistics; this.resolutions=resolutions; this.users=users; this.audit=audit; this.json=json;
    }
    @Transactional(readOnly=true)
    public java.util.List<ResolutionDtos.Resolution> history(int page) { return logistics.resolutions(page); }
    @Transactional
    public ResolutionDtos.Resolution resolve(String id, ResolutionDtos.Request request, String email) {
        // Lock the transfer itself before checking its state and its unique final resolution.
        var transfer=logistics.transferForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var actor=users.findByCorreo(email.trim().toLowerCase(Locale.ROOT)).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!"RECIBIDO_CON_DIFERENCIA".equals(transfer.estado()) || transfer.cantidadRecibida()==null
                || transfer.cantidadEnviada().compareTo(transfer.cantidadRecibida())<=0)
            throw conflict("El traslado no tiene una diferencia recibida pendiente de resolución");
        String reason=request.motivo().trim(); String evidence=clean(request.evidencia());
        if (reason.isEmpty()) throw conflict("El motivo es obligatorio");
        var existing=resolutions.findByTrasladoId(id);
        if (existing.isPresent()) {
            var previous=existing.get().dto();
            if (previous.operacionId().equals(request.operacionId().toString()) && previous.actorId().equals(actor.getId())
                    && previous.tipo().equals(request.tipo()) && previous.motivo().equals(reason)
                    && clean(previous.evidencia()).equals(evidence)) return previous;
            throw conflict("El traslado ya tiene una resolución final");
        }
        if (resolutions.existsById(request.operacionId().toString())) throw conflict("UUID de resolución ya utilizado");
        var entity=new TransferResolution(request.operacionId().toString(),id,actor.getId(),LocalDateTime.now(),request.tipo(),reason,
                evidence.isEmpty() ? null : evidence,transfer.cantidadEnviada(),transfer.cantidadRecibida());
        var result=entity.dto();
        // INSERT is intentional: an assigned JPA id must never cause merge to update a prior resolution.
        try { logistics.insertResolution(result); }
        catch (org.springframework.dao.DataIntegrityViolationException e) { throw conflict("UUID o resolución ya existente"); }
        audit.saveAndFlush(new AuditRecord(actor,"TRASLADO",id,"RESOLUCION_DIFERENCIA",null,json.writeValueAsString(result)));
        return result;
    }
    private static String clean(String text) { return text==null ? "" : text.trim(); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
