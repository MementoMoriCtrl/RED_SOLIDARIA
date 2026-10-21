package pe.redsolidaria.modules.access;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import pe.redsolidaria.config.SecurityConfiguration;
import pe.redsolidaria.modules.access.domain.Permission;
import pe.redsolidaria.modules.access.domain.Role;
import pe.redsolidaria.modules.access.domain.UserAccount;
import pe.redsolidaria.modules.access.persistence.UserRepository;

class AuthenticationProviderTest {
    @Test
    void normalizesEmailIndependentlyOfDefaultLocale() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var users = mock(UserRepository.class);
            var encoder = new BCryptPasswordEncoder(4);
            var account = new UserAccount(new Role("ADMIN", "Administrador", null), "Admin", "Local",
                    "i@example.test", encoder.encode("clave-de-prueba-solamente"), "ACTIVO");
            when(users.findByCorreo("i@example.test")).thenReturn(Optional.of(account));

            var configuration = new SecurityConfiguration();
            var provider = configuration.authenticationProvider(configuration.userDetailsService(users), encoder);
            Authentication result = provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    "I@example.test", "clave-de-prueba-solamente"));

            assertTrue(result.isAuthenticated());
            verify(users).findByCorreo("i@example.test");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void acceptsValidPasswordAndLoadsPermissionAuthority() {
        var users = mock(UserRepository.class);
        var encoder = new BCryptPasswordEncoder(4);
        var role = new Role("ADMIN", "Administrador", null);
        role.asignar(new Permission("ACCESO_ADMINISTRAR", "Administración de acceso"));
        var account = new UserAccount(role, "Admin", "Local", "admin@example.test",
                encoder.encode("clave-de-prueba-solamente"), "ACTIVO");
        when(users.findByCorreo("admin@example.test")).thenReturn(Optional.of(account));

        var configuration = new SecurityConfiguration();
        var userDetails = configuration.userDetailsService(users);
        var provider = configuration.authenticationProvider(userDetails, encoder);
        Authentication result = provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                "admin@example.test", "clave-de-prueba-solamente"));

        assertTrue(result.isAuthenticated());
        assertTrue(result.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ACCESO_ADMINISTRAR")));
    }

    @Test
    void rejectsInvalidPassword() {
        var users = mock(UserRepository.class);
        var encoder = new BCryptPasswordEncoder(4);
        var account = new UserAccount(new Role("OPERADOR", "Operador", null), "Operador", "Local",
                "operador@example.test", encoder.encode("clave-correcta"), "ACTIVO");
        when(users.findByCorreo("operador@example.test")).thenReturn(Optional.of(account));
        var configuration = new SecurityConfiguration();
        var provider = configuration.authenticationProvider(configuration.userDetailsService(users), encoder);

        assertThrows(BadCredentialsException.class, () -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("operador@example.test", "incorrecta")));
    }
}
