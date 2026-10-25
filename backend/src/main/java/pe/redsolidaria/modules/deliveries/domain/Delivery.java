package pe.redsolidaria.modules.deliveries.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import pe.redsolidaria.modules.access.domain.UserAccount;

@Entity @Table(name = "entrega")
public class Delivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_entrega", columnDefinition = "BIGINT UNSIGNED") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_usuario_responsable", nullable = false) private UserAccount responsable;
    @Column(name = "fecha_hora", nullable = false, insertable = false, updatable = false) private LocalDateTime fechaHora;
    @Column(columnDefinition = "TEXT") private String observaciones;
    @OneToMany(mappedBy = "entrega") private List<DeliveryLine> detalles = new ArrayList<>();
    protected Delivery() {}
    public Delivery(UserAccount responsable) { this.responsable = responsable; }
    public Long getId() { return id; }
}
