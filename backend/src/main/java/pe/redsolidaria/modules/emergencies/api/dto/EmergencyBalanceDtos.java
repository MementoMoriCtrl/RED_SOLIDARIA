package pe.redsolidaria.modules.emergencies.api.dto;

import java.math.BigDecimal;

public final class EmergencyBalanceDtos {
    private EmergencyBalanceDtos() {}
    public record ResourceBalance(Long recursoId, String recursoNombre, String unidadCodigo,
            BigDecimal requerida, BigDecimal recibidaAcopio, BigDecimal asignada, BigDecimal entregada,
            BigDecimal disponible, BigDecimal pendienteAsignar, BigDecimal pendienteEntregar, BigDecimal faltanteAbastecimiento) {}
}
