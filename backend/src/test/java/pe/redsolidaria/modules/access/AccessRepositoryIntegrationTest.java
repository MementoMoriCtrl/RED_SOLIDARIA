package pe.redsolidaria.modules.access;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.Persistence;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import pe.redsolidaria.config.SecurityConfiguration;
import pe.redsolidaria.modules.access.domain.Permission;
import pe.redsolidaria.modules.access.domain.Role;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.PermissionRepository;
import pe.redsolidaria.modules.access.persistence.RoleRepository;
import pe.redsolidaria.modules.access.persistence.UserRepository;
import pe.redsolidaria.test.RequiresLocalRedSolidariaMySql;

@SpringBootTest
@Transactional
@RequiresLocalRedSolidariaMySql
class AccessRepositoryIntegrationTest {
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void canReadAccessRepositoriesWithoutChangingExistingRows() {
        assertDoesNotThrow(() -> {
            users.findAll();
            roles.findAll();
            permissions.findAll();
        });
    }

    @Test
    void persistsUserAndBcryptHashInMySqlThenRollsBack() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String rawPassword = suffix + "-SoloPrueba";
        String email = "it-" + suffix + "@example.test";
        Permission permission = permissions.saveAndFlush(new Permission("IT_CONSULTA_" + suffix,
                "Permiso temporal de prueba"));
        Role role = new Role("IT_ROL_" + suffix, "Rol temporal de prueba", null);
        role.asignar(permission);
        role = roles.saveAndFlush(role);
        UserAccount saved = users.saveAndFlush(new UserAccount(role, "Prueba", "Temporal",
                email, passwordEncoder.encode(rawPassword), "ACTIVO"));

        assertTrue(saved.getId() != null);
        UserAccount reloaded = users.findByCorreo(email).orElseThrow();
        assertTrue(Persistence.getPersistenceUtil().isLoaded(reloaded, "rol"));
        assertTrue(Persistence.getPersistenceUtil().isLoaded(reloaded.getRol(), "asignaciones"));
        assertTrue(reloaded.getRol().getAsignaciones().stream()
                .allMatch(assignment -> Persistence.getPersistenceUtil().isLoaded(assignment, "permiso")));
        assertTrue(passwordEncoder.matches(rawPassword, reloaded.getPasswordHash()));
        assertTrue(!rawPassword.equals(reloaded.getPasswordHash()));

        SecurityConfiguration security = new SecurityConfiguration();
        AuthenticationProvider provider = security.authenticationProvider(security.userDetailsService(users), passwordEncoder);
        var authenticated = provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword));
        assertTrue(authenticated.isAuthenticated());
        assertTrue(authenticated.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("IT_CONSULTA_" + suffix)));
        org.junit.jupiter.api.Assertions.assertThrows(BadCredentialsException.class, () -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, "incorrecta")));
    }
}
