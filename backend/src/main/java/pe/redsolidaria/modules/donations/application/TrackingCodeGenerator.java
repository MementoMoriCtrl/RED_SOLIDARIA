package pe.redsolidaria.modules.donations.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TrackingCodeGenerator {
    public String nextCode() {
        return "RS-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
