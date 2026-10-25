package pe.redsolidaria.modules.deliveries.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import pe.redsolidaria.modules.donations.domain.DonationLine;
import pe.redsolidaria.modules.needs.domain.Need;

@Entity @Table(name = "detalle_entrega")
public class DeliveryLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_detalle_entrega", columnDefinition = "BIGINT UNSIGNED") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_entrega", nullable = false) private Delivery entrega;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_detalle_donacion", nullable = false) private DonationLine detalleDonacion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_necesidad", nullable = false) private Need necesidad;
    @Column(name = "cantidad_asignada", nullable = false, precision = 14, scale = 3) private BigDecimal cantidadAsignada;
    @Column(name = "cantidad_entregada", nullable = false, precision = 14, scale = 3) private BigDecimal cantidadEntregada;
    protected DeliveryLine() {}
    public DeliveryLine(Delivery entrega, DonationLine donacion, Need necesidad,
            BigDecimal asignada, BigDecimal entregada) {
        this.entrega = entrega; this.detalleDonacion = donacion; this.necesidad = necesidad;
        this.cantidadAsignada = asignada; this.cantidadEntregada = entregada;
    }
    public BigDecimal getCantidadAsignada() { return cantidadAsignada; }
    public BigDecimal getCantidadEntregada() { return cantidadEntregada; }
}
