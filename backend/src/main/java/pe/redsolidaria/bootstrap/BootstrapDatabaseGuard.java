package pe.redsolidaria.bootstrap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
public class BootstrapDatabaseGuard {
    private static final Pattern LOCAL_TARGET = Pattern.compile(
            "^jdbc:mysql://(?:localhost|127\\.0\\.0\\.1|\\[::1\\])(?::([0-9]{1,5}))?/red_solidaria(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);
    private final DataSource dataSource;

    public BootstrapDatabaseGuard(DataSource dataSource) { this.dataSource = dataSource; }

    public void verify() {
        try (Connection connection = dataSource.getConnection()) {
            String jdbcUrl = connection.getMetaData().getURL();
            var matcher = jdbcUrl == null ? null : LOCAL_TARGET.matcher(jdbcUrl);
            if (matcher == null || !matcher.matches() || containsCredentialQueryParameter(jdbcUrl)) throw targetError();
            String port = matcher.group(1);
            if (port != null && (Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535)) throw targetError();
            String catalog = connection.getCatalog();
            if (catalog == null || !"red_solidaria".equals(catalog.toLowerCase(Locale.ROOT))) throw targetError();
            if (!"MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) throw targetError();
            if (!connection.isValid(3)) throw targetError();
        } catch (SQLException | NumberFormatException ex) {
            throw new IllegalStateException("No se pudo verificar con seguridad el destino MySQL local red_solidaria", ex);
        }
    }

    private static IllegalStateException targetError() {
        return new IllegalStateException("El bootstrap solo se permite contra red_solidaria en MySQL local");
    }

    private static boolean containsCredentialQueryParameter(String jdbcUrl) {
        int queryStart = jdbcUrl.indexOf('?');
        if (queryStart < 0) return false;
        for (String parameter : jdbcUrl.substring(queryStart + 1).split("&")) {
            String key = parameter.split("=", 2)[0].toLowerCase(Locale.ROOT);
            if (key.equals("user") || key.equals("password") || key.equals("pwd")) return true;
        }
        return false;
    }
}
