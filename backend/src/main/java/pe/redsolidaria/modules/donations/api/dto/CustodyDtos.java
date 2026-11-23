package pe.redsolidaria.modules.donations.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Explicit public allowlist. Never add free-text movement locations, notes or people here. */
public final class CustodyDtos {
    private CustodyDtos() {}
    public record Place(String tipo, String nombre, String direccionInstitucional) {}
    public record Event(String evento, LocalDateTime fechaHora, String estado, Place origen, Place destino,
                        String zona, BigDecimal cantidadEnviada, BigDecimal cantidadRecibida) {}
    public record Position(String estado, Place centro, String zona, BigDecimal cantidad) {}
    public record Quantities(BigDecimal declarada, BigDecimal recibidaConfirmada, BigDecimal enTransito,
                             BigDecimal enDistribucion, BigDecimal disponibleDistribucion,
                             BigDecimal asignada, BigDecimal entregada) {}
    public record Allocation(String zona, BigDecimal asignada, BigDecimal entregada) {}
    public record Resource(String recurso, String unidad, String estadoLogistico, Place ultimaCustodiaConfirmada,
                           LocalDateTime ultimaConfirmacion, LocalDateTime ultimaActualizacion,
                           List<Position> posiciones, List<Event> movimientos, String aclaracion,
                           Quantities cantidades, List<Allocation> asignaciones) {}
    public record PublicCustody(String presentacion, String emergencia, List<Resource> recursos, LocalDateTime ultimaActualizacion,
                                boolean recepcionAcopioConfirmada) {
        public PublicCustody(String presentacion,String emergencia,List<Resource> recursos) {
            this(presentacion,emergencia,recursos,null,false);
        }
    }
    public record PrivateEvent(String recurso, String unidad, Event movimiento, String operacion,
                               Long responsableId, String responsableNombre, String observaciones) {}
    public record RawBalance(String recurso, String unidad, String tipo, String centro, BigDecimal saldoBruto) {}
    public record AdministrativeCustody(String lugarPresentacion, PublicCustody situacion,
                                       List<PrivateEvent> operaciones, List<RawBalance> saldosBrutos) {}
}
