package pe.redsolidaria.modules.donations.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.emergencies.domain.Emergency;
import pe.redsolidaria.modules.resources.domain.CollectionCenter;
import java.util.Objects;

@Entity @Table(name = "donacion")
public class Donation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_donacion", columnDefinition = "BIGINT UNSIGNED") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_donante", nullable = false) private Donor donante;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_emergencia") private Emergency emergencia;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_centro_acopio") private CollectionCenter centroAcopio;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_usuario_registro", nullable = false) private UserAccount usuarioRegistro;
    @Column(name = "codigo_trazabilidad", nullable = false, unique = true, length = 100) private String codigoTrazabilidad;
    @Column(name = "fecha_registro", nullable = false, insertable = false, updatable = false) private LocalDateTime fechaRegistro;
    @Column(name = "fecha_ingreso") private LocalDateTime fechaIngreso;
    @Column(nullable = false, length = 30) private String estado;
    @Column(columnDefinition = "TEXT") private String observaciones;
    @OneToMany(mappedBy = "donacion") private List<DonationLine> detalles = new ArrayList<>();
    @OneToMany(mappedBy = "donacion") private List<DonationMovement> movimientos = new ArrayList<>();
    protected Donation() {}

    public Donation(Donor donante, Emergency emergencia, CollectionCenter centroAcopio,
                    UserAccount usuarioRegistro, String codigoTrazabilidad,
                    LocalDateTime fechaIngreso, String estado, String observaciones) {
        this.donante = Objects.requireNonNull(donante);
        this.emergencia = emergencia;
        this.centroAcopio = centroAcopio;
        this.usuarioRegistro = Objects.requireNonNull(usuarioRegistro);
        this.codigoTrazabilidad = Objects.requireNonNull(codigoTrazabilidad);
        this.fechaIngreso = fechaIngreso;
        this.estado = Objects.requireNonNull(estado);
        this.observaciones = observaciones;
    }

    public Long getId() { return id; }
    public Donor getDonante() { return donante; }
    public Emergency getEmergencia() { return emergencia; }
    public CollectionCenter getCentroAcopio() { return centroAcopio; }
    public UserAccount getUsuarioRegistro() { return usuarioRegistro; }
    public String getCodigoTrazabilidad() { return codigoTrazabilidad; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public LocalDateTime getFechaIngreso() { return fechaIngreso; }
    public String getEstado() { return estado; }
    public String getObservaciones() { return observaciones; }
    public List<DonationLine> getDetalles() { return detalles; }
    public List<DonationMovement> getMovimientos() { return movimientos; }
    public void marcarRecibida() {
        if (!"REGISTRADA".equals(estado)) throw new IllegalStateException("Estado incompatible con recepción");
        estado = "RECIBIDA";
    }
}
