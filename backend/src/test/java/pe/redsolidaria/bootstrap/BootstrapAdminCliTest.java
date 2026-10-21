package pe.redsolidaria.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;

class BootstrapAdminCliTest {
    @Test
    void requiresTheExactExplicitConfirmationArgument() {
        assertThrows(IllegalArgumentException.class, () -> BootstrapAdminCli.requireExplicitConfirmation(new String[0]));
        assertThrows(IllegalArgumentException.class, () -> BootstrapAdminCli.requireExplicitConfirmation(new String[]{"--confirm-bootstrap", "other"}));
        assertThrows(IllegalArgumentException.class, () -> BootstrapAdminCli.requireExplicitConfirmation(new String[]{"--skip-bootstrap"}));
        assertDoesNotThrow(() -> BootstrapAdminCli.requireExplicitConfirmation(new String[]{"--confirm-bootstrap"}));
    }

    @Test
    void normalApplicationCannotAutoRunTheBootstrapCommand() {
        assertFalse(CommandLineRunner.class.isAssignableFrom(LocalAdminBootstrapCommand.class));
        assertFalse(ApplicationRunner.class.isAssignableFrom(LocalAdminBootstrapCommand.class));
        assertArrayEquals(new String[]{"local-bootstrap"}, LocalAdminBootstrapCommand.class.getAnnotation(Profile.class).value());
        assertEquals("true", LocalAdminBootstrapCommand.class.getAnnotation(ConditionalOnProperty.class).havingValue());
        assertEquals(org.springframework.boot.WebApplicationType.NONE, BootstrapAdminCli.webApplicationType());
    }
}
