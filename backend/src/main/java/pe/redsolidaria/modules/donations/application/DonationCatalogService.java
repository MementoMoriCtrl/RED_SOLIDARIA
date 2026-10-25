package pe.redsolidaria.modules.donations.application;

import static pe.redsolidaria.modules.donations.api.dto.DonationCatalogDtos.*;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.redsolidaria.modules.donations.domain.Donor;
import pe.redsolidaria.modules.donations.persistence.DonorRepository;
import pe.redsolidaria.modules.resources.domain.CollectionCenter;
import pe.redsolidaria.modules.resources.persistence.CollectionCenterRepository;
import pe.redsolidaria.modules.emergencies.persistence.EmergencyRepository;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.audit.persistence.AuditRecordRepository;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
public class DonationCatalogService {
    private final DonorRepository donors;
    private final CollectionCenterRepository centers;
    private final EmergencyRepository emergencies;
    private final UserRepository users;
    private final AuditRecordRepository audit;
    private final JsonMapper json;
    private final pe.redsolidaria.modules.resources.persistence.MeasurementUnitRepository units;
    public DonationCatalogService(DonorRepository donors, CollectionCenterRepository centers, EmergencyRepository emergencies,
            UserRepository users, AuditRecordRepository audit, JsonMapper json,
            pe.redsolidaria.modules.resources.persistence.MeasurementUnitRepository units) {
        this.donors = donors; this.centers = centers; this.emergencies = emergencies;
        this.users = users; this.audit = audit; this.json = json;
        this.units = units;
    }
    @Transactional
    public pe.redsolidaria.modules.resources.api.dto.ResourceDtos.MeasurementUnitResponse createUnit(
            String email, pe.redsolidaria.modules.resources.api.dto.ResourceDtos.CreateMeasurementUnitRequest request) {
        var actor = users.findByCorreo(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        pe.redsolidaria.modules.resources.domain.MeasurementUnit unit;
        try {
            unit = units.saveAndFlush(new pe.redsolidaria.modules.resources.domain.MeasurementUnit(
                    request.codigo().toUpperCase(Locale.ROOT), request.nombre().trim(), request.simbolo()));
        } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El código de unidad ya existe o no es válido");
        }
        audit.saveAndFlush(new AuditRecord(actor, "UNIDAD_MEDIDA", unit.getId().toString(), "CREACION_CATALOGO", null,
                json.writeValueAsString(Map.of("codigo", unit.getCodigo(), "nombre", unit.getNombre()))));
        return new pe.redsolidaria.modules.resources.api.dto.ResourceDtos.MeasurementUnitResponse(
                unit.getId(), unit.getCodigo(), unit.getNombre(), unit.getSimbolo());
    }
    public CatalogPage list(String kind, String text, int page) {
        var pageable = PageRequest.of(page, 20);
        Page<CatalogItem> result = switch (kind) {
            case "donantes" -> donors.findByNombreContainingIgnoreCaseOrderByNombreAscIdAsc(text.trim(), pageable)
                    .map(donor -> new CatalogItem(donor.getId(), donor.getNombre(), null));
            case "centros" -> centers.findByNombreContainingIgnoreCaseOrderByNombreAscIdAsc(text.trim(), pageable)
                    .map(center -> new CatalogItem(center.getId(), center.getNombre(), center.getUbicacion()));
            case "emergencias" -> emergencies.search(text.isBlank() ? null : text.trim(), null,
                    PageRequest.of(page, 20, Sort.by("nombre", "id")))
                    .map(emergency -> new CatalogItem(emergency.getId(), emergency.getNombre(), null));
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
        return new CatalogPage(result.getContent(), result.getNumber(), result.getTotalPages());
    }
    @Transactional
    public CatalogItem create(String kind, String email, CreateCatalogRequest request) {
        if (request.descripcion() != null && request.descripcion().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65535)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La descripción supera el límite permitido");
        var actor = users.findByCorreo(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        CatalogItem item = switch (kind) {
            case "donantes" -> {
                var saved = donors.saveAndFlush(new Donor(request.nombre().trim()));
                yield new CatalogItem(saved.getId(), saved.getNombre(), null);
            }
            case "centros" -> {
                var saved = centers.saveAndFlush(new CollectionCenter(request.nombre().trim(), request.ubicacion(), request.descripcion()));
                yield new CatalogItem(saved.getId(), saved.getNombre(), saved.getUbicacion());
            }
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
        audit.saveAndFlush(new AuditRecord(actor, kind.equals("donantes") ? "DONANTE" : "CENTRO_ACOPIO", item.id().toString(),
                "CREACION_CATALOGO", null, json.writeValueAsString(Map.of("id", item.id(), "nombre", item.nombre()))));
        return item;
    }
}
