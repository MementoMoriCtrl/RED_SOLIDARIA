package pe.redsolidaria.bootstrap;

import java.nio.CharBuffer;
import java.util.Arrays;
import org.hibernate.LazyInitializationException;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Profile("local-bootstrap")
public class AdminAuthenticationDiagnostic {
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapDatabaseGuard databaseGuard;

    public AdminAuthenticationDiagnostic(UserDetailsService userDetailsService,
                                         PasswordEncoder passwordEncoder,
                                         BootstrapDatabaseGuard databaseGuard) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.databaseGuard = databaseGuard;
    }

    public Result inspect(String email, char[] password) {
        try {
            databaseGuard.verify();
        } catch (RuntimeException rejectedTarget) {
            return new Result(Status.DESTINATION_REJECTED, false, false, false, false);
        }
        try {
            UserDetails user = userDetailsService.loadUserByUsername(email);
            boolean hasAdminPermission = user.getAuthorities().stream()
                    .anyMatch(authority -> "ACCESO_ADMINISTRAR".equals(authority.getAuthority()));
            boolean passwordMatches = passwordEncoder.matches(CharBuffer.wrap(password), user.getPassword());
            return new Result(Status.COMPLETE, true, user.isEnabled(), hasAdminPermission, passwordMatches);
        } catch (UsernameNotFoundException missingUser) {
            return new Result(Status.ACCOUNT_NOT_FOUND, false, false, false, false);
        } catch (LazyInitializationException detachedRelation) {
            return new Result(Status.LAZY_RELATION_UNAVAILABLE, false, false, false, false);
        } catch (RuntimeException lookupFailure) {
            return new Result(Status.USER_LOOKUP_FAILED, false, false, false, false);
        }
    }

    public static void clear(char[] value) {
        if (value != null) Arrays.fill(value, '\0');
    }

    public enum Status {
        COMPLETE, ACCOUNT_NOT_FOUND, LAZY_RELATION_UNAVAILABLE, DESTINATION_REJECTED, USER_LOOKUP_FAILED
    }

    public record Result(Status status, boolean accountFound, boolean enabled,
                         boolean hasAdminPermission, boolean passwordMatches) {}
}
