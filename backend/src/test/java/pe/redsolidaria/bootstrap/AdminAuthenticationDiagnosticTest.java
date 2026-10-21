package pe.redsolidaria.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminAuthenticationDiagnosticTest {
    @Test
    void reportsDetachedJpaRelationsAsIncompleteInsteadOfAborting() {
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        BootstrapDatabaseGuard databaseGuard = mock(BootstrapDatabaseGuard.class);
        when(userDetailsService.loadUserByUsername("admin@example.test"))
                .thenThrow(new LazyInitializationException("sensitive ORM detail"));

        AdminAuthenticationDiagnostic diagnostic = new AdminAuthenticationDiagnostic(
                userDetailsService, passwordEncoder, databaseGuard);
        AdminAuthenticationDiagnostic.Result result = diagnostic.inspect(
                "admin@example.test", "test-only-password".toCharArray());

        assertEquals(AdminAuthenticationDiagnostic.Status.LAZY_RELATION_UNAVAILABLE, result.status());
        verify(databaseGuard).verify();
    }
}
