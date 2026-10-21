package pe.redsolidaria.modules.donations.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import pe.redsolidaria.modules.access.domain.UserAccount;
import java.util.Objects;

@Entity @Table(name = "movimiento_donacion")
public class DonationMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_movimiento", columnDefinition = "BIGINT UNSIGNED") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_donacion", nullable = false) private Donation donacion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_usuario_responsable", nullable = false) private UserAccount responsable;
    @Column(name = "estado_anterior", length = 30) private String estadoAnterior;
    @Column(name = "estado_nuevo", length = 30) private String estadoNuevo;
    @Column(name = "fecha_hora", nullable = false, insertable = false, updatable = false) private LocalDateTime fechaHora;
    @Column(length = 200) private String ubicacion;
    @Column(columnDefinition = "TEXT") private String observaciones;
    protected DonationMovement() {}

    public DonationMovement(Donation donacion, UserAccount responsable, String estadoAnterior,
                            String estadoNuevo, String ubicacion, String observaciones) {
        this.donacion = Objects.requireNonNull(donacion);
        this.responsable = Objects.requireNonNull(responsable);
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = Objects.requireNonNull(estadoNuevo);
        this.ubicacion = ubicacion;
        this.observaciones = observaciones;
    }

    public Long getId() { return id; }
    public Donation getDonacion() { return donacion; }
    public UserAccount getResponsable() { return responsable; }
    public String getEstadoAnterior() { return estadoAnterior; }
    public String getEstadoNuevo() { return estadoNuevo; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public String getUbicacion() { return ubicacion; }
    public String getObservaciones() { return observaciones; }
}
