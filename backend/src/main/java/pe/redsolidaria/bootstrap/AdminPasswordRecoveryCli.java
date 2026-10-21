package pe.redsolidaria.bootstrap;

import java.io.Console;
import java.util.Arrays;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import pe.redsolidaria.RedSolidariaApplication;

public final class AdminPasswordRecoveryCli {
    private static final String CONFIRMATION_FLAG = "--confirm-password-recovery";
    private static final String PROFILE = "local-bootstrap";

    private AdminPasswordRecoveryCli() {}

    public static void main(String[] args) {
        Console console = System.console();
        if (args == null || args.length != 1 || !CONFIRMATION_FLAG.equals(args[0])) {
            System.err.println("Se requiere la opción explícita --confirm-password-recovery.");
            System.exit(2);
            return;
        }
        if (console == null) {
            System.err.println("La recuperación requiere una consola interactiva; no se modificaron datos.");
            System.exit(2);
            return;
        }

        int exitCode = 1;
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(RedSolidariaApplication.class)
                .profiles(PROFILE)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.main.web-application-type=none",
                        "--spring.main.banner-mode=off",
                        "--spring.main.log-startup-info=false",
                        "--spring.flyway.enabled=false",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--app.bootstrap.enabled=false",
                        "--logging.level.org.hibernate.orm.connections.pooling=ERROR")) {
            try {
                execute(console, context.getBean(AdminPasswordRecoveryService.class));
                exitCode = 0;
            } catch (RuntimeException failure) {
                console.printf("Recuperación abortada. No se muestran datos sensibles.%n");
                if (failure instanceof AdminPasswordRecoveryService.RecoveryException
                        || failure instanceof IllegalArgumentException) {
                    console.printf("%s%n", failure.getMessage());
                }
            }
        } catch (RuntimeException startupFailure) {
            System.err.println("No se pudo iniciar la herramienta de recuperación; no se muestran detalles de conexión.");
        }
        if (exitCode != 0) System.exit(exitCode);
    }

    private static void execute(Console console, AdminPasswordRecoveryService recovery) {
        String email = console.readLine("Correo de la cuenta administradora existente: ");
        recovery.verifyTarget(email);
        console.printf("Cuenta verificada: ACTIVO, rol ADMINISTRADOR y ACCESO_ADMINISTRAR presente.%n");

        String confirmation = console.readLine("Para continuar, escriba RESTABLECER: ");
        if (!"RESTABLECER".equals(confirmation)) {
            throw new AdminPasswordRecoveryService.RecoveryException("Confirmación no válida; no se modificaron datos.");
        }

        char[] password = console.readPassword("Nueva contraseña (12-128 caracteres; máximo 72 bytes UTF-8): ");
        char[] repeated = console.readPassword("Repita la nueva contraseña: ");
        try {
            if (password == null || repeated == null || !Arrays.equals(password, repeated)) {
                throw new AdminPasswordRecoveryService.RecoveryException("Las entradas no coinciden; no se modificaron datos.");
            }
            recovery.resetPassword(email, password);
            console.printf("Contraseña actualizada para la cuenta verificada. No se muestra el hash.%n");
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (repeated != null) Arrays.fill(repeated, '\0');
        }
    }
}
