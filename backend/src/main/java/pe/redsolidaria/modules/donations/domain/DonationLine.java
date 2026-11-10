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
    @Column(name="cantidad_examinada",nullable=false,precision=14,scale=3) private BigDecimal examinada=BigDecimal.ZERO;
    @Column(name="cantidad_no_aceptada",nullable=false,precision=14,scale=3) private BigDecimal noAceptada=BigDecimal.ZERO;
    @Column(name="cantidad_excedente_pendiente",nullable=false,precision=14,scale=3) private BigDecimal excedentePendiente=BigDecimal.ZERO;
    @Column(name="cantidad_excedente_aprobada",nullable=false,precision=14,scale=3) private BigDecimal excedenteAprobado=BigDecimal.ZERO;
    @Column(name="verificacion_cerrada",nullable=false) private boolean cerrada;
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
    public BigDecimal getExaminada() { return examinada; }
    public BigDecimal getNoAceptada() { return noAceptada; }
    public BigDecimal getExcedentePendiente() { return excedentePendiente; }
    public BigDecimal getExcedenteAprobado() { return excedenteAprobado; }
    public boolean isCerrada() { return cerrada; }
    public BigDecimal pendienteDeclarado() { return cantidadDonada.subtract(cantidadRecibidaAcopio.subtract(excedenteAprobado)).max(BigDecimal.ZERO); }
    public void verificar(BigDecimal examinada,BigDecimal aceptada,BigDecimal noAceptada,BigDecimal excedente,boolean cerrar) {
        if (cerrada || cantidadRecibidaAcopio.compareTo(cantidadDonada)>=0) throw new IllegalStateException("Verificación cerrada");
        if (aceptada.signum()>0) recibir(aceptada);
        this.examinada=this.examinada.add(examinada); this.noAceptada=this.noAceptada.add(noAceptada);
        this.excedentePendiente=this.excedentePendiente.add(excedente);
        this.cerrada=cerrar || pendienteDeclarado().signum()==0;
    }
    public void decidirExcedente(BigDecimal cantidad,boolean aprobar) {
        if (cantidad.signum()<=0 || cantidad.compareTo(excedentePendiente)>0) throw new IllegalArgumentException("Excedente no pendiente");
        excedentePendiente=excedentePendiente.subtract(cantidad);
        if (aprobar) { excedenteAprobado=excedenteAprobado.add(cantidad); cantidadRecibidaAcopio=cantidadRecibidaAcopio.add(cantidad); }
    }
    public void recibir(BigDecimal cantidad) {
        if (cantidad == null || cantidad.signum() <= 0 || cantidad.scale() > 3
                || cantidadRecibidaAcopio.add(cantidad).compareTo(cantidadDonada) > 0) {
            throw new IllegalArgumentException("La cantidad recibida no es válida para el detalle");
        }
        cantidadRecibidaAcopio = cantidadRecibidaAcopio.add(cantidad).setScale(3);
    }
}
