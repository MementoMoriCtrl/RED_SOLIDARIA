package pe.redsolidaria.modules.logistics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.validation.constraints.*;

public final class ResolutionDtos {
    private ResolutionDtos() {}
    public record Request(@NotNull UUID operacionId, @NotBlank @Pattern(regexp="PERDIDA|DANO|ERROR_DOCUMENTAL|OTRO") String tipo,
                          @NotBlank @Size(max=2000) String motivo, @Size(max=2000) String evidencia) {}
    public record Resolution(String operacionId, String trasladoId, Long actorId, LocalDateTime fecha, String tipo,
                             String motivo, String evidencia, BigDecimal cantidadEnviada, BigDecimal cantidadRecibida, BigDecimal diferencia) {}
}
