package pe.redsolidaria.modules.emergencies.api.dto;

import java.math.BigDecimal;

public final class EmergencyBalanceDtos {
    private EmergencyBalanceDtos() {}
    public record ResourceBalance(Long recursoId, String recursoNombre, String unidadCodigo,
            BigDecimal requerida, BigDecimal recibidaAcopio, BigDecimal asignada, BigDecimal entregada,
            BigDecimal disponible, BigDecimal pendienteAsignar, BigDecimal pendienteEntregar, BigDecimal faltanteAbastecimiento,
            BigDecimal trasladada, BigDecimal recibidaDistribucion, BigDecimal enTransito, BigDecimal diferencia) {
        public ResourceBalance(Long recursoId, String recursoNombre, String unidadCodigo, BigDecimal requerida,
                BigDecimal recibidaAcopio, BigDecimal asignada, BigDecimal entregada, BigDecimal disponible,
                BigDecimal pendienteAsignar, BigDecimal pendienteEntregar, BigDecimal faltanteAbastecimiento) {
            this(recursoId,recursoNombre,unidadCodigo,requerida,recibidaAcopio,asignada,entregada,disponible,
                pendienteAsignar,pendienteEntregar,faltanteAbastecimiento,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
        }
    }
}
