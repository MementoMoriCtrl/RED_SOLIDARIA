package pe.redsolidaria.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import pe.redsolidaria.test.RequiresLocalRedSolidariaMySql;
import pe.redsolidaria.modules.access.persistence.PermissionRepository;
import pe.redsolidaria.modules.access.persistence.RoleRepository;
import pe.redsolidaria.modules.access.persistence.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local-bootstrap")
@Import(BootstrapRollbackMySqlTest.FailingEncoderConfiguration.class)
@RequiresLocalRedSolidariaMySql
class BootstrapRollbackMySqlTest {
    @Autowired BootstrapDatabaseGuard databaseGuard;
    @Autowired BootstrapAdminService bootstrap;
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired UserRepository users;

    @Test
    void rollsBackRolePermissionAndUserWritesWhenHashingFails() {
        databaseGuard.verify();
        Assumptions.assumeFalse(bootstrap.administratorExists(), "Ya existe un administrador válido");
        Assumptions.assumeFalse(roles.existsByCodigo(BootstrapAdminService.ROLE_CODE), "El rol reservado ya existe y requiere revisión manual");
        boolean permissionExistedBefore = permissions.findByCodigo(BootstrapAdminService.ADMIN_PERMISSION).isPresent();
        String email = "rollback-" + UUID.randomUUID() + "@example.test";
        char[] password = "ROLLBACK_TEST_password_123".toCharArray();
        try {
            assertThrows(IllegalStateException.class, () -> bootstrap.createAdministrator(
                    new BootstrapAdminInput(email, "Temporal", "Rollback", password)));
        } finally {
            java.util.Arrays.fill(password, '\0');
        }

        assertFalse(roles.existsByCodigo(BootstrapAdminService.ROLE_CODE));
        assertEquals(permissionExistedBefore,
                permissions.findByCodigo(BootstrapAdminService.ADMIN_PERMISSION).isPresent());
        assertFalse(users.existsByCorreo(email));
    }

    @TestConfiguration
    static class FailingEncoderConfiguration {
        @Bean @Primary
        PasswordEncoder failingPasswordEncoder() {
            BCryptPasswordEncoder delegate = new BCryptPasswordEncoder(4);
            return new PasswordEncoder() {
                @Override public String encode(CharSequence rawPassword) {
                    if (rawPassword.toString().startsWith("ROLLBACK_TEST_")) {
                        throw new IllegalStateException("Prueba de fallo deliberado para verificar rollback");
                    }
                    return delegate.encode(rawPassword);
                }
                @Override public boolean matches(CharSequence rawPassword, String encodedPassword) {
                    return delegate.matches(rawPassword, encodedPassword);
                }
            };
        }
    }
}
