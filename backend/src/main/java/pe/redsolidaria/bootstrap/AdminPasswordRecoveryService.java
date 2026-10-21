package pe.redsolidaria.bootstrap;

import java.nio.CharBuffer;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataAccessException;
import pe.redsolidaria.modules.access.application.PasswordPolicy;

@Service
@Profile("local-bootstrap")
public class AdminPasswordRecoveryService {
    private static final String TARGET_SQL = """
            SELECT u.id_usuario, u.estado, r.codigo AS rol_codigo,
                   EXISTS (
                       SELECT 1
                       FROM rol_permiso rp
                       JOIN permiso p ON p.id_permiso = rp.id_permiso
                       WHERE rp.id_rol = r.id_rol
                         AND p.codigo = 'ACCESO_ADMINISTRAR'
                   ) AS tiene_acceso_administrar
            FROM usuario u
            JOIN rol r ON r.id_rol = u.id_rol
            WHERE u.correo = ?
            """;

    private static final String UPDATE_SQL = """
            UPDATE usuario u
            JOIN rol r ON r.id_rol = u.id_rol
            SET u.password_hash = ?, u.actualizado_en = u.actualizado_en
            WHERE u.id_usuario = ?
              AND u.correo = ?
              AND UPPER(u.estado) = 'ACTIVO'
              AND r.codigo = 'ADMINISTRADOR'
              AND EXISTS (
                  SELECT 1
                  FROM rol_permiso rp
                  JOIN permiso p ON p.id_permiso = rp.id_permiso
                  WHERE rp.id_rol = r.id_rol
                    AND p.codigo = 'ACCESO_ADMINISTRAR'
              )
            """;

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapDatabaseGuard databaseGuard;

    public AdminPasswordRecoveryService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                                       BootstrapDatabaseGuard databaseGuard) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.databaseGuard = databaseGuard;
    }

    @Transactional(readOnly = true)
    public void verifyTarget(String correo) {
        verifyDatabase();
        findTarget(normalizeEmail(correo));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void resetPassword(String correo, char[] password) {
        verifyDatabase();
        String normalizedEmail = normalizeEmail(correo);
        Target target = findTarget(normalizedEmail);
        PasswordPolicy.validate(CharBuffer.wrap(password));

        String encoded = passwordEncoder.encode(CharBuffer.wrap(password));
        try {
            int updated = jdbc.update(UPDATE_SQL, encoded, target.id(), normalizedEmail);
            if (updated != 1) {
                throw new RecoveryException("La cuenta dejó de cumplir las condiciones; no se actualizó la contraseña.");
            }
        } catch (DataAccessException failure) {
            throw new RecoveryException("No se pudo actualizar la cuenta; la transacción fue cancelada.");
        }
    }

    private Target findTarget(String correo) {
        List<Target> matches;
        try {
            matches = jdbc.query(TARGET_SQL, (rs, row) -> new Target(
                    rs.getLong("id_usuario"),
                    rs.getString("estado"),
                    rs.getString("rol_codigo"),
                    rs.getBoolean("tiene_acceso_administrar")), correo);
        } catch (DataAccessException failure) {
            throw new RecoveryException("No se pudo comprobar la cuenta; no se modificaron datos.");
        }

        if (matches.size() != 1) {
            throw new RecoveryException("No se encontró una cuenta única para ese correo; no se modificaron datos.");
        }
        Target target = matches.get(0);
        if (!"ACTIVO".equalsIgnoreCase(target.estado())
                || !"ADMINISTRADOR".equals(target.rolCodigo())
                || !target.hasAdminAccess()) {
            throw new RecoveryException("La cuenta no cumple los requisitos de administrador; no se modificaron datos.");
        }
        return target;
    }

    private static String normalizeEmail(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new RecoveryException("El correo es obligatorio.");
        }
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    private void verifyDatabase() {
        try {
            databaseGuard.verify();
        } catch (IllegalStateException failure) {
            throw new RecoveryException("Destino rechazado: se requiere MySQL local y el catálogo red_solidaria.");
        }
    }

    private record Target(long id, String estado, String rolCodigo, boolean hasAdminAccess) {}

    public static final class RecoveryException extends RuntimeException {
        public RecoveryException(String message) { super(message); }
    }
}
