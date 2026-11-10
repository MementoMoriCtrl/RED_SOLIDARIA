package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import pe.redsolidaria.modules.donations.api.dto.DonationDtos.CreateDonationRequest;
import pe.redsolidaria.modules.donations.api.dto.DonationDtos.DonationLineRequest;
import pe.redsolidaria.modules.donations.application.DonationRegistrationService;
import pe.redsolidaria.modules.donations.persistence.DonationMovementRepository;
import pe.redsolidaria.test.StrictTestMySqlSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local-bootstrap")
@Import(DonationRegistrationRollbackMySqlTest.FailingMovementRepositoryConfiguration.class)
class DonationRegistrationRollbackMySqlTest extends StrictTestMySqlSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired DonationRegistrationService service;
    @Autowired DonationMovementRepository movements;

    @Test
    void rollsBackDonationAndDetailsWhenInitialMovementCannotBeStored() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String roleCode = "IT_DONATION_" + suffix.substring(0, 30);
        String email = "it-" + suffix + "@example.test";
        long roleId = insert("INSERT INTO rol (codigo,nombre) VALUES (?,?)", roleCode, "Prueba rollback");
        long userId = insert("INSERT INTO usuario (id_rol,nombre,apellido,correo,password_hash,estado) VALUES (?,?,?,?,?,?)",
                roleId, "Prueba", "Rollback", email, "not-used-by-test", "ACTIVO");
        long donorId = insert("INSERT INTO donante (nombre) VALUES (?)", "Donante " + suffix);
        long centerId = insert("INSERT INTO centro_acopio (nombre) VALUES (?)", "Centro " + suffix);
        long unitId = insert("INSERT INTO unidad_medida (codigo,nombre) VALUES (?,?)", "U" + suffix.substring(0, 28), "Unidad");
        long resourceId = insert("INSERT INTO recurso (id_unidad_medida,nombre,categoria,estado) VALUES (?,?,?,?)",
                unitId, "Recurso " + suffix, "Prueba", "ACTIVO");
        System.out.printf("REGISTRATION_ROLLBACK_FIXTURE role=%d user=%d donor=%d center=%d unit=%d resource=%d%n",
                roleId, userId, donorId, centerId, unitId, resourceId);

        when(movements.saveAndFlush(any())).thenThrow(new IllegalStateException("Fallo deliberado al persistir movimiento inicial"));
        CreateDonationRequest request = new CreateDonationRequest(donorId, null, centerId, null, null,
                java.util.List.of(new DonationLineRequest(resourceId, new java.math.BigDecimal("1.000"))));
        assertThrows(IllegalStateException.class, () -> service.register(request, email));

        // Verify no donation or detail was left for the isolated fixture donor.
        assertEquals(0, count("SELECT COUNT(*) FROM donacion WHERE id_donante = ?", donorId));
        assertEquals(0, count("SELECT COUNT(*) FROM detalle_donacion dd JOIN donacion d ON d.id_donacion=dd.id_donacion WHERE d.id_donante = ?", donorId));
    }

    private long insert(String sql, Object... values) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private int count(String sql, Object value) { return jdbc.queryForObject(sql, Integer.class, value); }

    @TestConfiguration
    static class FailingMovementRepositoryConfiguration {
        @Bean @Primary DonationMovementRepository failingMovementRepository() {
            return org.mockito.Mockito.mock(DonationMovementRepository.class);
        }
    }
}
