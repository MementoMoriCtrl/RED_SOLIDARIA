package pe.redsolidaria.modules.donations.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import pe.redsolidaria.modules.resources.domain.Resource;
import pe.redsolidaria.modules.deliveries.domain.DeliveryLine;
import java.util.Objects;

@Entity @Table(name = "detalle_donacion", uniqueConstraints = @UniqueConstraint(name = "uk_detalle_donacion_recurso", columnNames = {"id_donacion", "id_recurso"}))
public class DonationLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_detalle_donacion", columnDefinition = "BIGINT UNSIGNED") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_donacion", nullable = false) private Donation donacion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_recurso", nullable = false) private Resource recurso;
    @Column(name = "cantidad_donada", nullable = false, precision = 14, scale = 3) private BigDecimal cantidadDonada;
    @Column(name = "cantidad_recibida_acopio", nullable = false, precision = 14, scale = 3) private BigDecimal cantidadRecibidaAcopio;
    @OneToMany(mappedBy = "detalleDonacion") private List<DeliveryLine> entregas = new ArrayList<>();
    protected DonationLine() {}

    public DonationLine(Donation donacion, Resource recurso, BigDecimal cantidadDonada) {
        this.donacion = Objects.requireNonNull(donacion);
        this.recurso = Objects.requireNonNull(recurso);
        this.cantidadDonada = Objects.requireNonNull(cantidadDonada);
        this.cantidadRecibidaAcopio = BigDecimal.ZERO.setScale(3);
    }

    public Long getId() { return id; }
    public Donation getDonacion() { return donacion; }
    public Resource getRecurso() { return recurso; }
    public BigDecimal getCantidadDonada() { return cantidadDonada; }
    public BigDecimal getCantidadRecibidaAcopio() { return cantidadRecibidaAcopio; }
    public void recibir(BigDecimal cantidad) {
        if (cantidad == null || cantidad.signum() <= 0 || cantidad.scale() > 3
                || cantidadRecibidaAcopio.add(cantidad).compareTo(cantidadDonada) > 0) {
            throw new IllegalArgumentException("La cantidad recibida no es válida para el detalle");
        }
        cantidadRecibidaAcopio = cantidadRecibidaAcopio.add(cantidad).setScale(3);
    }
}
