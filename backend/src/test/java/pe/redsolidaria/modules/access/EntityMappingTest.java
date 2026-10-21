package pe.redsolidaria.modules.access;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.Map;
import org.springframework.data.jpa.repository.EntityGraph;
import org.junit.jupiter.api.Test;
import pe.redsolidaria.modules.access.domain.Permission;
import pe.redsolidaria.modules.access.domain.Role;
import pe.redsolidaria.modules.access.domain.RolePermission;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.modules.audit.domain.AuditRecord;
import pe.redsolidaria.modules.deliveries.domain.Delivery;
import pe.redsolidaria.modules.deliveries.domain.DeliveryLine;
import pe.redsolidaria.modules.donations.domain.Donation;
import pe.redsolidaria.modules.donations.domain.DonationLine;
import pe.redsolidaria.modules.donations.domain.DonationMovement;
import pe.redsolidaria.modules.donations.domain.Donor;
import pe.redsolidaria.modules.emergencies.domain.AffectedZone;
import pe.redsolidaria.modules.emergencies.domain.Emergency;
import pe.redsolidaria.modules.needs.domain.Need;
import pe.redsolidaria.modules.resources.domain.CollectionCenter;
import pe.redsolidaria.modules.resources.domain.MeasurementUnit;
import pe.redsolidaria.modules.resources.domain.Resource;

class EntityMappingTest {
    @Test
    void userLookupFetchesRoleAssignmentsAndPermissions() throws Exception {
        EntityGraph graph = UserRepository.class.getMethod("findByCorreo", String.class)
                .getAnnotation(EntityGraph.class);

        assertNotNull(graph);
        assertArrayEquals(new String[] {"rol", "rol.asignaciones", "rol.asignaciones.permiso"},
                graph.attributePaths());
    }

    @Test
    void allV1TablesHaveAnEntityMapping() {
        Map<Class<?>, String> mappings = Map.ofEntries(
                Map.entry(Role.class, "rol"), Map.entry(Permission.class, "permiso"),
                Map.entry(RolePermission.class, "rol_permiso"), Map.entry(UserAccount.class, "usuario"),
                Map.entry(Emergency.class, "emergencia"), Map.entry(AffectedZone.class, "zona_afectada"),
                Map.entry(CollectionCenter.class, "centro_acopio"), Map.entry(MeasurementUnit.class, "unidad_medida"),
                Map.entry(Resource.class, "recurso"), Map.entry(Donor.class, "donante"),
                Map.entry(Donation.class, "donacion"), Map.entry(DonationLine.class, "detalle_donacion"),
                Map.entry(Need.class, "necesidad"), Map.entry(Delivery.class, "entrega"),
                Map.entry(DeliveryLine.class, "detalle_entrega"), Map.entry(DonationMovement.class, "movimiento_donacion"),
                Map.entry(AuditRecord.class, "registro_auditoria"));
        assertEquals(17, mappings.size());
        mappings.forEach((type, table) -> {
            assertTrue(type.isAnnotationPresent(Entity.class), type.getSimpleName());
            assertEquals(table, type.getAnnotation(Table.class).name(), type.getSimpleName());
        });
    }

    @Test
    void accessAndOperationalRelationsUseJpaAssociations() throws Exception {
        assertNotNull(UserAccount.class.getDeclaredField("rol").getAnnotation(ManyToOne.class));
        assertNotNull(Role.class.getDeclaredField("asignaciones").getAnnotation(OneToMany.class));
        assertNotNull(RolePermission.class.getDeclaredField("rol").getAnnotation(ManyToOne.class));
        assertNotNull(RolePermission.class.getDeclaredField("permiso").getAnnotation(ManyToOne.class));
        assertNotNull(Donation.class.getDeclaredField("detalles").getAnnotation(OneToMany.class));
        assertNotNull(DeliveryLine.class.getDeclaredField("detalleDonacion").getAnnotation(ManyToOne.class));
    }
}
