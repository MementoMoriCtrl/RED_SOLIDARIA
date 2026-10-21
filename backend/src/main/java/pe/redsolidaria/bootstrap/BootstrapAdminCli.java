package pe.redsolidaria.bootstrap;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import pe.redsolidaria.RedSolidariaApplication;

public final class BootstrapAdminCli {
    public static final String CONFIRMATION_FLAG = "--confirm-bootstrap";
    public static final String PROFILE = "local-bootstrap";

    private BootstrapAdminCli() {}

    public static void main(String[] args) {
        requireExplicitConfirmation(args);
        try (var context = new SpringApplicationBuilder(RedSolidariaApplication.class)
                .profiles(PROFILE)
                .web(webApplicationType())
                .run()) {
            context.getBean(LocalAdminBootstrapCommand.class).execute();
            System.out.println("Bootstrap completado. Se creó un administrador local; no se muestran credenciales.");
        }
    }

    static void requireExplicitConfirmation(String[] args) {
        if (args == null || args.length != 1 || !CONFIRMATION_FLAG.equals(args[0])) {
            throw new IllegalArgumentException("Se requiere ejecutar explícitamente con " + CONFIRMATION_FLAG);
        }
    }

    static WebApplicationType webApplicationType() { return WebApplicationType.NONE; }
}
