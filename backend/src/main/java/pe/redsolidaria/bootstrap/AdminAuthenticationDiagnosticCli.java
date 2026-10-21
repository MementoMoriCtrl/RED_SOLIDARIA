package pe.redsolidaria.bootstrap;

import java.io.Console;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import pe.redsolidaria.RedSolidariaApplication;

public final class AdminAuthenticationDiagnosticCli {
    private static final String REQUIRED_ARGUMENT = "--diagnose-admin-auth";

    private AdminAuthenticationDiagnosticCli() {}

    public static void main(String[] args) {
        Console console = System.console();
        if (args == null || args.length != 1 || !REQUIRED_ARGUMENT.equals(args[0]) || console == null) {
            System.err.println("Se requiere la confirmación de diagnóstico y una consola interactiva.");
            System.exit(2);
            return;
        }

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(RedSolidariaApplication.class)
                .profiles("local-bootstrap")
                .web(WebApplicationType.NONE)
                .run("--spring.main.web-application-type=none",
                        "--spring.main.banner-mode=off",
                        "--spring.main.log-startup-info=false",
                        "--spring.flyway.enabled=false",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.jpa.show-sql=false",
                        "--app.bootstrap.enabled=false",
                        "--logging.level.root=ERROR",
                        "--logging.level.org.hibernate.orm.connections.pooling=ERROR")) {
            runDiagnostic(console, context.getBean(AdminAuthenticationDiagnostic.class));
        } catch (RuntimeException failure) {
            console.printf("Diagnóstico abortado. No se muestran datos sensibles.%n");
            System.exit(1);
        }
    }

    private static void runDiagnostic(Console console, AdminAuthenticationDiagnostic diagnostic) {
        char[] emailChars = console.readPassword("Correo de la cuenta (entrada oculta): ");
        char[] password = console.readPassword("Contraseña usada en HTTP Basic: ");
        if (emailChars == null || password == null) {
            AdminAuthenticationDiagnostic.clear(emailChars);
            AdminAuthenticationDiagnostic.clear(password);
            console.printf("Diagnóstico cancelado.%n");
            return;
        }

        try {
            String email = new String(emailChars);
            AdminAuthenticationDiagnostic.Result result = diagnostic.inspect(email, password);
            if (result.status() == AdminAuthenticationDiagnostic.Status.DESTINATION_REJECTED) {
                console.printf("Diagnóstico incompleto: no se pudo verificar MySQL local red_solidaria.%n");
                return;
            }
            if (result.status() == AdminAuthenticationDiagnostic.Status.LAZY_RELATION_UNAVAILABLE) {
                console.printf("Diagnóstico incompleto: JPA no pudo cargar rol y permisos fuera de la sesión de base de datos.%n");
                return;
            }
            if (result.status() == AdminAuthenticationDiagnostic.Status.USER_LOOKUP_FAILED) {
                console.printf("Diagnóstico incompleto: falló la consulta de usuario o permisos; no se muestran detalles.%n");
                return;
            }
            console.printf("Destino: MySQL local red_solidaria verificado.%n");
            console.printf("Cuenta localizada: %s%n", yesNo(result.accountFound()));
            console.printf("Cuenta habilitada: %s%n", yesNo(result.enabled()));
            console.printf("Autoridad ACCESO_ADMINISTRAR: %s%n", yesNo(result.hasAdminPermission()));
            console.printf("Contraseña coincide con el hash almacenado: %s%n", yesNo(result.passwordMatches()));
        } finally {
            AdminAuthenticationDiagnostic.clear(emailChars);
            AdminAuthenticationDiagnostic.clear(password);
        }
    }

    private static String yesNo(boolean value) { return value ? "sí" : "no"; }
}
