package pe.redsolidaria.bootstrap;

import java.io.Console;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-bootstrap")
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class LocalAdminBootstrapCommand {
    private final BootstrapDatabaseGuard databaseGuard;
    private final BootstrapAdminService bootstrapService;

    public LocalAdminBootstrapCommand(BootstrapDatabaseGuard databaseGuard, BootstrapAdminService bootstrapService) {
        this.databaseGuard = databaseGuard; this.bootstrapService = bootstrapService;
    }

    public BootstrapAdminResult execute() {
        databaseGuard.verify();
        if (bootstrapService.administratorExists()) throw new IllegalStateException("Ya existe un administrador activo; bootstrap cancelado");
        Console console = System.console();
        if (console == null) throw new IllegalStateException("El bootstrap requiere una consola interactiva para ocultar la contraseña");

        String correo = console.readLine("Usuario (correo): ");
        String nombre = console.readLine("Nombre: ");
        String apellido = console.readLine("Apellido: ");
        char[] password = console.readPassword("Contraseña (12-128 caracteres; máximo 72 bytes UTF-8): ");
        char[] confirmation = console.readPassword("Confirma la contraseña: ");
        try {
            if (password == null || confirmation == null) throw new IllegalStateException("La consola cerró la lectura de contraseña; no se guardó ningún dato");
            if (!Arrays.equals(password, confirmation)) throw new IllegalArgumentException("Las contraseñas no coinciden");
            return bootstrapService.createAdministrator(new BootstrapAdminInput(correo, nombre, apellido, password));
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
}
