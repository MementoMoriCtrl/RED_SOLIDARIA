package pe.redsolidaria.modules.donations.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import pe.redsolidaria.modules.donations.api.dto.CustodyDtos.*;
import pe.redsolidaria.modules.donations.domain.Donation;
import pe.redsolidaria.modules.donations.domain.DonationMovement;
import pe.redsolidaria.modules.donations.persistence.DonationCustodyRepository;
import pe.redsolidaria.modules.donations.persistence.DonationCustodyRepository.Row;

@Service
public class DonationCustodyService {
    private final DonationCustodyRepository repository;
    public DonationCustodyService(DonationCustodyRepository repository) { this.repository=repository; }
    public AdministrativeCustody read(Donation donation, List<DonationMovement> history) {
        // Preserve distinct delivery details within the same operation; joined copies of one source row count once.
        Map<List<Object>,Row> unique=new LinkedHashMap<>();
        for (var row:repository.events(donation.getId()))
            unique.putIfAbsent(List.of(row.lineId(),row.sourceKey(),row.event().evento()),row);
        var rows=unique.values().stream()
            .sorted(Comparator.comparing((Row r) -> r.event().fechaHora(),Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(r -> eventOrder(r.event().evento())).thenComparing(Row::operation)).toList();
        var resources=new ArrayList<Resource>();
        var privateEvents=new ArrayList<PrivateEvent>();
        var rawBalances=new ArrayList<RawBalance>();
        for (var line : donation.getDetalles()) {
            var publicEvents=new ArrayList<Event>();
            var events=rows.stream().filter(r -> r.lineId()==line.getId()).toList();
            var resource=line.getRecurso();
            var unit=resource.getUnidadMedida().getCodigo();
            var departures=events.stream().filter(r -> r.event().evento().equals("SALIDA_TRASLADO")).toList();
            var arrivals=events.stream().filter(r -> r.event().evento().equals("RECEPCION_DISTRIBUCION")).toList();
            var positions=new ArrayList<Position>();
            var received=zero(line.getCantidadRecibidaAcopio());
            var sent=departures.stream().map(r -> r.event().cantidadEnviada()).reduce(BigDecimal.ZERO,BigDecimal::add);
            var historicalAssigned=events.stream().filter(r -> !r.distribution() && r.event().evento().equals("ASIGNACION_RECURSOS"))
                .map(r -> r.event().cantidadRecibida()).reduce(BigDecimal.ZERO,BigDecimal::add);
            Place collection=events.stream().filter(r -> r.event().evento().equals("RECEPCION_ACOPIO") && r.event().destino()!=null)
                .map(r -> r.event().destino()).findFirst().orElse(departures.isEmpty() ? null : departures.getFirst().event().origen());
            var rawCollection=received.subtract(sent).subtract(historicalAssigned);
            var remaining=rawCollection.max(BigDecimal.ZERO);
            if (remaining.signum()>0) positions.add(new Position(collection==null ? "CUSTODIA_NO_DETERMINADA" : "EN_ACOPIO",collection,null,remaining));
            for (var departure : departures) {
                boolean arrived=arrivals.stream().anyMatch(r -> r.operation().equals(departure.operation()));
                if (!arrived) positions.add(new Position("EN_TRANSITO",departure.event().destino(),null,departure.event().cantidadEnviada()));
            }
            // The current business model guarantees one distribution destination per line.
            var distributionReceived=arrivals.stream().map(r -> zero(r.event().cantidadRecibida())).reduce(BigDecimal.ZERO,BigDecimal::add);
            var distributed=events.stream().filter(r -> r.distribution() && r.event().evento().equals("ENTREGA_POBLACION"))
                .map(r -> r.event().cantidadRecibida()).reduce(BigDecimal.ZERO,BigDecimal::add);
            var rawDistribution=distributionReceived.subtract(distributed);
            var distributionRemaining=rawDistribution.max(BigDecimal.ZERO);
            var distributionCenters=arrivals.stream().map(r -> r.event().destino()).distinct().toList();
            if (distributionRemaining.signum()>0) positions.add(new Position("EN_DISTRIBUCION",
                distributionCenters.size()==1 ? distributionCenters.getFirst() : null,null,distributionRemaining));
            for (var row : events) {
                var event=row.event();
                if (event.evento().equals("ENTREGA_POBLACION")) positions.add(new Position("ENTREGADA",null,event.zona(),event.cantidadRecibida()));
                // Assignments are reservations; they never move physical custody.
                Place origin=event.origen();
                if (row.distribution() && (event.evento().equals("ASIGNACION_RECURSOS") || event.evento().equals("ENTREGA_POBLACION"))) {
                    var destinations=arrivals.stream().filter(a -> a.event().fechaHora()!=null && event.fechaHora()!=null
                        && !a.event().fechaHora().isAfter(event.fechaHora())).map(a -> a.event().destino()).distinct().toList();
                    if (destinations.size()==1) origin=destinations.getFirst();
                }
                var enriched=new Event(event.evento(),event.fechaHora(),event.estado(),origin,event.destino(),event.zona(),event.cantidadEnviada(),event.cantidadRecibida());
                publicEvents.add(enriched);
                privateEvents.add(new PrivateEvent(resource.getNombre(),unit,enriched,row.operation(),row.actorId(),row.actor(),row.notes()));
            }
            if (historicalAssigned.signum()>0) {
                var historicalDelivered=events.stream().filter(r -> !r.distribution() && r.event().evento().equals("ENTREGA_POBLACION"))
                    .map(r -> r.event().cantidadRecibida()).reduce(BigDecimal.ZERO,BigDecimal::add);
                var rawHistorical=historicalAssigned.subtract(historicalDelivered);
                rawBalances.add(new RawBalance(resource.getNombre(),unit,"RESERVA_HISTORICA_PENDIENTE",null,rawHistorical));
                var unknown=rawHistorical.max(BigDecimal.ZERO);
                if (unknown.signum()>0) positions.add(new Position("RESERVA_HISTORICA_SIN_CUSTODIA_CONFIRMADA",null,null,unknown));
            }
            Place last=null; LocalDateTime confirmed=null;
            for (var row : events) {
                var e=row.event();
                Place point=e.evento().equals("SALIDA_TRASLADO") ? e.origen()
                    : (e.evento().startsWith("RECEPCION_") && zero(e.cantidadRecibida()).signum()>0 ? e.destino() : null);
                if (e.evento().equals("ENTREGA_POBLACION") && e.zona()!=null && zero(e.cantidadRecibida()).signum()>0)
                    point=new Place("ZONA",e.zona(),null);
                if (point!=null && e.fechaHora()!=null) { last=point; confirmed=e.fechaHora(); }
            }
            LocalDateTime updated=events.stream().map(r -> r.event().fechaHora()).filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(donation.getFechaRegistro());
            // Combine repeated deliveries to the same public zone without turning multiple records into multiple locations.
            Map<List<Object>,Position> grouped=new LinkedHashMap<>();
            for (var position:positions) {
                var key=java.util.Arrays.<Object>asList(position.estado(),position.centro(),position.zona());
                var previous=grouped.get(key);
                grouped.put(key,previous==null ? position : new Position(position.estado(),position.centro(),position.zona(),
                    previous.cantidad().add(position.cantidad())));
            }
            positions=new ArrayList<>(grouped.values());
            Map<String,Allocation> allocations=new LinkedHashMap<>();
            for (var row:events) {
                var e=row.event();
                if (e.zona()==null || (!e.evento().equals("ASIGNACION_RECURSOS") && !e.evento().equals("ENTREGA_POBLACION"))) continue;
                var previous=allocations.getOrDefault(e.zona(),new Allocation(e.zona(),BigDecimal.ZERO,BigDecimal.ZERO));
                allocations.put(e.zona(),new Allocation(e.zona(),previous.asignada().add(e.evento().equals("ASIGNACION_RECURSOS") ? zero(e.cantidadRecibida()) : BigDecimal.ZERO),
                    previous.entregada().add(e.evento().equals("ENTREGA_POBLACION") ? zero(e.cantidadRecibida()) : BigDecimal.ZERO)));
            }
            var assigned=events.stream().filter(r -> r.event().evento().equals("ASIGNACION_RECURSOS"))
                .map(r -> zero(r.event().cantidadRecibida())).reduce(BigDecimal.ZERO,BigDecimal::add);
            var delivered=events.stream().filter(r -> r.event().evento().equals("ENTREGA_POBLACION"))
                .map(r -> zero(r.event().cantidadRecibida())).reduce(BigDecimal.ZERO,BigDecimal::add);
            var distributionAssigned=events.stream().filter(r -> r.distribution() && r.event().evento().equals("ASIGNACION_RECURSOS"))
                .map(r -> zero(r.event().cantidadRecibida())).reduce(BigDecimal.ZERO,BigDecimal::add);
            String collectionName=collection == null ? null : collection.nombre();
            String distributionName=distributionCenters.size()==1 && distributionCenters.getFirst()!=null ? distributionCenters.getFirst().nombre() : null;
            rawBalances.add(new RawBalance(resource.getNombre(),unit,"DISPONIBLE_ACOPIO",collectionName,rawCollection));
            rawBalances.add(new RawBalance(resource.getNombre(),unit,"CUSTODIA_DISTRIBUCION",distributionName,rawDistribution));
            rawBalances.add(new RawBalance(resource.getNombre(),unit,"DISPONIBLE_DISTRIBUCION",distributionName,distributionReceived.subtract(distributionAssigned)));
            var transit=positions.stream().filter(p -> p.estado().equals("EN_TRANSITO")).map(Position::cantidad).reduce(BigDecimal.ZERO,BigDecimal::add);
            String status=positions.size()>1 ? (delivered.signum()>0 && transit.signum()==0 ? "ENTREGA_PARCIAL" : "CUSTODIA_DISTRIBUIDA") : positions.isEmpty()
                ? (received.signum()>0 ? "SIN_CUSTODIA_CONFIRMADA" : "PENDIENTE_DE_VERIFICACION") : positions.getFirst().estado();
            if (!positions.isEmpty() && positions.stream().allMatch(p -> p.estado().equals("ENTREGADA"))) status="ENTREGADA";
            if (status.equals("EN_DISTRIBUCION") && assigned.compareTo(delivered)>0) status="ASIGNADA";
            resources.add(new Resource(resource.getNombre(),unit,status,last,confirmed,updated,List.copyOf(positions),publicEvents,
                "Custodia física y cantidades reservadas se muestran por separado. No hay seguimiento GPS. Las diferencias de recepción no tienen custodia confirmada; la historia incompleta no se reconstruye.",
                new Quantities(zero(line.getCantidadDonada()),received,transit,distributionRemaining,
                    distributionReceived.subtract(distributionAssigned).max(BigDecimal.ZERO),assigned,delivered),List.copyOf(allocations.values())));
        }
        String location=history.stream().filter(m -> "DONACION_PRESENTADA".equals(m.getTipoEvento())
            || (m.getEstadoAnterior()==null && "REGISTRADA".equals(m.getEstadoNuevo())))
            .map(DonationMovement::getUbicacion).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        var latest=java.util.stream.Stream.concat(history.stream().map(DonationMovement::getFechaHora),
            rows.stream().map(r -> r.event().fechaHora())).filter(java.util.Objects::nonNull)
            .max(Comparator.naturalOrder()).orElse(donation.getFechaRegistro());
        boolean collectionConfirmed=rows.stream().anyMatch(r ->
            (r.event().evento().equals("RECEPCION_ACOPIO") && r.event().destino()!=null && zero(r.event().cantidadRecibida()).signum()>0)
            || (r.event().evento().equals("SALIDA_TRASLADO") && r.event().origen()!=null && zero(r.event().cantidadEnviada()).signum()>0));
        var publicData=new PublicCustody("Punto de presentación registrado. Dirección exacta protegida por privacidad",
            donation.getEmergencia()==null ? null : donation.getEmergencia().getNombre(),resources,latest,collectionConfirmed);
        privateEvents.sort(Comparator.comparing(e -> e.movimiento().fechaHora(),Comparator.nullsLast(Comparator.naturalOrder())));
        return new AdministrativeCustody(location,publicData,privateEvents,rawBalances);
    }
    private static BigDecimal zero(BigDecimal value) { return value==null ? BigDecimal.ZERO : value; }
    private static int eventOrder(String type) {
        return switch(type) {
            case "RECEPCION_ACOPIO" -> 0;
            case "SALIDA_TRASLADO" -> 1;
            case "RECEPCION_DISTRIBUCION" -> 2;
            case "ASIGNACION_RECURSOS" -> 3;
            case "ENTREGA_POBLACION" -> 4;
            default -> 5;
        };
    }
}
