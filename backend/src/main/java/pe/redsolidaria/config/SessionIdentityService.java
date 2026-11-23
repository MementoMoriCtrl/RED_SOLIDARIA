package pe.redsolidaria.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import pe.redsolidaria.modules.access.persistence.UserRepository;

/** A keyed, non-reversible revision marker; never retain a password hash in a session. */
public final class SessionIdentityService implements UserDetailsService {
    private final UserRepository users;
    private final byte[] key = new byte[32];

    public SessionIdentityService(UserRepository users) {
        this.users = users;
        new SecureRandom().nextBytes(key);
    }

    @Override public UserDetails loadUserByUsername(String email) {
        var account = users.findByCorreo(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales no válidas"));
        var permissions = account.permisosEfectivos();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            // Persisted identities always include their database IDs. The labelled fallback
            // supports transient test entities without ever colliding with a persisted ID.
            String userId = account.getId() == null ? "transient:" + account.getCorreo() : "id:" + account.getId();
            String roleId = account.getRol().getId() == null ? "transient:" + account.getRol().getCodigo() : "id:" + account.getRol().getId();
            for (String value : java.util.stream.Stream.concat(java.util.stream.Stream.of(
                    userId, account.getCorreo(), account.getEstado(), roleId,
                    account.getPasswordHash()), permissions.stream().sorted()).toList()) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                mac.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                mac.update(bytes);
            }
            return new SessionUser(account.getCorreo(), account.getPasswordHash(),
                    "ACTIVO".equalsIgnoreCase(account.getEstado()), permissions, mac.doFinal());
        } catch (java.security.GeneralSecurityException failure) {
            throw new IllegalStateException("No se pudo comprobar la identidad de sesión", failure);
        }
    }

    public static final class SessionUser extends User {
        private final byte[] revision;
        SessionUser(String username, String password, boolean active, java.util.Set<String> permissions, byte[] revision) {
            super(username, password, active, true, true, true,
                    permissions.stream().map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList());
            this.revision = revision.clone();
        }
        boolean sameRevision(SessionUser other) { return java.security.MessageDigest.isEqual(revision, other.revision); }
    }
}
