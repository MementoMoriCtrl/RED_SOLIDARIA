package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import pe.redsolidaria.modules.donations.domain.Donation;
import pe.redsolidaria.modules.donations.domain.DonationLine;
import pe.redsolidaria.modules.resources.domain.Resource;

class DonationReceiptDomainTest {
    @Test void accumulatesSequentialPartialReceiptsAndRejectsExcess() {
        DonationLine line = new DonationLine(mock(Donation.class), mock(Resource.class), new BigDecimal("5.000"));
        line.recibir(new BigDecimal("1.250"));
        line.recibir(new BigDecimal("2.000"));
        assertEquals(new BigDecimal("3.250"), line.getCantidadRecibidaAcopio());
        line.recibir(new BigDecimal("1.750"));
        assertEquals(new BigDecimal("5.000"), line.getCantidadRecibidaAcopio());
        assertThrows(IllegalArgumentException.class, () -> line.recibir(new BigDecimal("0.001")));
    }
}
