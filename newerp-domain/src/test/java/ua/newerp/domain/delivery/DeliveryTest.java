package ua.newerp.domain.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class DeliveryTest {

    static TrackingInfo tracking(int code, boolean redelivery) {
        return new TrackingInfo("20450000000000", "Отримано", code, "Київ, Відділення №1", redelivery,
                LocalDateTime.of(2026, 10, 1, 10, 0), LocalDate.of(2026, 10, 3), null);
    }

    @Test
    void warehouseSortOrderAndStatus() {
        var branch = new NovaPoshtaWarehouse("ref", "Відділення №12 (до 30 кг): вул. Шевченка, 1", "Branch", "c", " Київ ", 30, true);
        assertThat(branch.getSortOrder()).isEqualTo(12);
        assertThat(branch.isStatus()).isTrue();
        assertThat(branch.getCityDescription()).isEqualTo("Київ");

        var cargo = new NovaPoshtaWarehouse("ref", "Вантажне відділення", "CargoBranch", "c", "Київ", 0, true);
        assertThat(cargo.getSortOrder()).isEqualTo(1);
        assertThat(cargo.isStatus()).isFalse();
    }

    @Test
    void seatVolumeInCubicMeters() {
        assertThat(SeatPreset.ALL.get(0).seat().volume()).isEqualByComparingTo("0.006696"); // 31*36*6 cm
        assertThat(new ShipmentSeat(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN).volume())
                .isEqualByComparingTo("0.001");
    }

    @Test
    void trackingUpdatesOnlyWhenCodeChanges() {
        var doc = new ShipmentDocument(5, "20450000000000", new BigDecimal("70"));
        assertThat(doc.getRef()).isEqualTo("20450000000000");
        assertThat(doc.getCarrier()).isEqualTo(ShipmentDocument.NOVA_POSHTA);

        assertThat(doc.applyTracking(tracking(9, false))).isTrue();
        assertThat(doc.applyTracking(tracking(9, true))).isFalse();
        assertThat(doc.isRedelivery()).isFalse();
        assertThat(doc.isFinal()).isTrue();
        assertThat(doc.getTone()).isEqualTo(ShipmentTone.SUCCESS);
    }

    @Test
    void tones() {
        var doc = new ShipmentDocument(5, "1", BigDecimal.ZERO);
        doc.applyTracking(tracking(9, true));
        assertThat(doc.getTone()).isEqualTo(ShipmentTone.INFO);
        doc.applyTracking(tracking(11, true));
        assertThat(doc.getTone()).isEqualTo(ShipmentTone.SUCCESS);
        doc.applyTracking(tracking(102, false));
        assertThat(doc.getTone()).isEqualTo(ShipmentTone.WARNING);
        doc.applyTracking(tracking(3, false));
        assertThat(doc.getTone()).isEqualTo(ShipmentTone.ERROR);
        doc.applyTracking(tracking(7, false));
        assertThat(doc.getTone()).isEqualTo(ShipmentTone.INFO);
        assertThat(doc.isFinal()).isFalse();
    }
}
