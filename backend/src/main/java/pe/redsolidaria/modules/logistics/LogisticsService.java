package pe.redsolidaria.modules.logistics;

import static pe.redsolidaria.modules.logistics.LogisticsDtos.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import pe.redsolidaria.modules.donations.domain.DonationMovement;
import pe.redsolidaria.modules.donations.persistence.*;
import pe.redsolidaria.modules.deliveries.persistence.DeliveryLineRepository;
import pe.redsolidaria.modules.needs.application.NeedManagementService;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly=true)
public class LogisticsService {
    private final LogisticsRepository repository;
    private final DonationRepository donations;
    private final DonationLineRepository lines;
    private final DeliveryLineRepository deliveries;
    private final UserRepository users;
    private final AuditRecordRepository audit;
    private final DonationMovementRepository movements;
    private final EntityManager em;
    private final JsonMapper json;
    public LogisticsService(LogisticsRepository repository, DonationRepository donations, DonationLineRepository lines,
            DeliveryLineRepository deliveries, UserRepository users, AuditRecordRepository audit,
            DonationMovementRepository movements, EntityManager em, JsonMapper json) {
        this.repository=repository; this.donations=donations; this.lines=lines; this.deliveries=deliveries;
        this.users=users; this.audit=audit; this.movements=movements; this.em=em; this.json=json;
    }
    public List<Center> centers() { return repository.centers(); }
    public List<Stock> stock(int page) { return repository.stock(page); }
    public List<Transfer> history(int page) { return repository.history(page); }
    @Transactional
    public void saveCenter(Long id, SaveCenter request, String email) {
        var actor=actor(email);
        boolean creating = id==null;
        if (creating) id=repository.createCenter(request);
        else if (repository.updateCenter(id,request)==0) throw missing();
        audit.saveAndFlush(new AuditRecord(actor,"CENTRO_DISTRIBUCION",id.toString(),
            creating ? "CREACION_CATALOGO" : "ACTUALIZACION_CATALOGO",null,json.writeValueAsString(request)));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Transfer dispatch(Dispatch request, String email) {
        var line=lines.findById(request.detalleDonacionId()).orElseThrow(LogisticsService::missing);
        var donation=donations.findByIdForUpdate(line.getDonacion().getId()).orElseThrow(LogisticsService::missing);
        em.refresh(line);
        var actor=actor(email);
        var fingerprint=hash(json.writeValueAsString(List.of(actor.getId(),request.detalleDonacionId(),
            request.centroDistribucionId(),request.cantidad().stripTrailingZeros().toPlainString(),clean(request.observaciones()))));
        var previous=repository.transfer(request.operacionId().toString());
        if (previous.isPresent()) {
            if (!previous.get().huellaSalida().equals(fingerprint)) throw conflict("UUID usado con otro contenido o responsable");
            return previous.get();
        }
        if (donation.getCentroAcopio()==null) throw conflict("La donación no tiene centro de acopio identificado; requiere conciliación");
        if (!NeedManagementService.USABLE_STATES.contains(donation.getEstado())) throw conflict("El estado de la donación no permite traslados");
        if (repository.centers().stream().noneMatch(c -> c.id()==request.centroDistribucionId())) throw missing();
        if (repository.hasOtherDestination(line.getId(), request.centroDistribucionId()))
            throw conflict("Este lote ya tiene otro destino de distribución. No se permite mezclar sus reservas entre instalaciones");
        var available=line.getCantidadRecibidaAcopio().subtract(repository.sent(line.getId()))
            .subtract(deliveries.historicalAssigned(line.getId()));
        if (request.cantidad().signum()<=0 || request.cantidad().compareTo(available)>0)
            throw conflict("La cantidad supera las existencias libres del centro de acopio");
        try {
            repository.dispatch(request,donation.getCentroAcopio().getId(),line.getRecurso().getUnidadMedida().getId(),actor.getId(),fingerprint);
        } catch (org.springframework.dao.DataIntegrityViolationException collision) {
            throw conflict("El UUID o los datos del traslado entran en conflicto con una operación existente");
        }
        var result=repository.transfer(request.operacionId().toString()).orElseThrow();
        audit(actor,result,"SALIDA_TRASLADO");
        movements.saveAndFlush(new DonationMovement(donation,actor,donation.getEstado(),donation.getEstado(),
            donation.getCentroAcopio().getNombre(),"Salida de traslado " + result.operacionId() + ": " + request.cantidad().toPlainString()).conTipo("SALIDA_TRASLADO"));
        return result;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Transfer receive(String id, Receive request, String email) {
        var initial=repository.transfer(id).orElseThrow(LogisticsService::missing);
        var line=lines.findById(initial.detalleDonacionId()).orElseThrow(LogisticsService::missing);
        var donation=donations.findByIdForUpdate(line.getDonacion().getId()).orElseThrow(LogisticsService::missing);
        var transfer=repository.transfer(id).orElseThrow(LogisticsService::missing);
        var actor=actor(email);
        var fingerprint=hash(json.writeValueAsString(List.of(actor.getId(),request.cantidad().stripTrailingZeros().toPlainString(),clean(request.observaciones()))));
        if (!"EN_TRANSITO".equals(transfer.estado())) {
            if (!request.operacionId().toString().equals(transfer.operacionRecepcion()) || !fingerprint.equals(transfer.huellaRecepcion()))
                throw conflict("El traslado ya fue recibido con otra operación o contenido");
            return transfer;
        }
        if (request.cantidad().signum()<0 || request.cantidad().compareTo(transfer.cantidadEnviada())>0)
            throw conflict("La cantidad recibida debe estar entre cero y la enviada");
        boolean difference=request.cantidad().compareTo(transfer.cantidadEnviada())!=0;
        if (difference && clean(request.observaciones()).isEmpty()) throw conflict("Documenta la diferencia en observaciones");
        repository.receive(id,request,actor.getId(),fingerprint,difference ? "RECIBIDO_CON_DIFERENCIA" : "RECIBIDO");
        var result=repository.transfer(id).orElseThrow();
        audit(actor,result,"RECEPCION_DISTRIBUCION");
        movements.saveAndFlush(new DonationMovement(donation,actor,donation.getEstado(),donation.getEstado(),
            result.destino(),"Recepción de traslado " + id + ": " + request.cantidad().toPlainString()).conTipo("RECEPCION_DISTRIBUCION"));
        return result;
    }
    private void audit(UserAccount actor, Transfer transfer, String action) {
        audit.saveAndFlush(new AuditRecord(actor,"TRASLADO",transfer.operacionId(),action,null,json.writeValueAsString(transfer)));
    }
    private UserAccount actor(String email) {
        return users.findByCorreo(email.trim().toLowerCase(Locale.ROOT)).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
    private static String clean(String text) { return text==null ? "" : text.trim(); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"No se encontró el registro logístico"); }
    private static ResponseStatusException conflict(String text) { return new ResponseStatusException(HttpStatus.CONFLICT,text); }
}
