package ua.newerp.domain.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import ua.newerp.domain.delivery.InternationalShipment.Item;
import ua.newerp.domain.delivery.InternationalShipment.Parcel;
import ua.newerp.domain.delivery.InternationalShipment.Recipient;
import ua.newerp.domain.delivery.InternationalShipment.Terms;

/** The invented recipient lives in Poland; the rules and the maths are the old site's. */
class InternationalShipmentTest {

    final Recipient anna = new Recipient(" Anna ", "Kowalska", "+48 600-100-200", null, "pl", null, "Kraków", "Floriańska", "5", null,
            "31-019");
    final Item dress = new Item("Сукня лляна", "Linen dress", "6204.49.10", 450, new BigDecimal("2"), new BigDecimal("3000"));
    final Item belt = new Item("Пояс", "Leather belt", "4203300000", 150, null, new BigDecimal("500"));
    final ShipmentSeat medium = SeatPreset.ALL.get(1).seat();

    @Test
    void cleansTheInputAndFillsTheDefaults() {
        InternationalShipment shipment = InternationalShipment.of(1001, anna, null, List.of(dress), List.of(medium), new BigDecimal("3000"), null);

        assertThat(shipment.recipient().countryCode()).isEqualTo("PL");
        assertThat(shipment.recipient().firstName()).isEqualTo("Anna");
        assertThat(shipment.recipient().phone()).isEqualTo("48600100200");
        assertThat(shipment.items().getFirst().hsCode()).isEqualTo("62044910");
        assertThat(shipment.terms()).isEqualTo(new Terms("DAP", "Selling", "Recipient", "parcel", null, "Printed apparel / merchandise"));
        assertThat(shipment.currency()).isEqualTo("UAH");
    }

    @Test
    void oneParcelWeighsItsItemsAndInsuresTheWholeCost() {
        InternationalShipment shipment = InternationalShipment.of(1001, anna, null, List.of(dress, belt), List.of(medium),
                new BigDecimal("3500"), "UAH");

        // Medium box 41 × 48 × 8 cm.
        assertThat(shipment.parcels()).containsExactly(new Parcel(1, 410, 480, 80, 600, new BigDecimal("3500.00")));
        assertThat(shipment.items().get(1).amount()).isEqualByComparingTo("1");
    }

    @Test
    void severalParcelsWeighThemselvesAndShareTheInsurance() {
        var light = new ShipmentSeat(new BigDecimal("0.35"), new BigDecimal("31"), new BigDecimal("36"), new BigDecimal("6.5"));
        var heavy = new ShipmentSeat(new BigDecimal("1.2"), new BigDecimal("49"), new BigDecimal("61"), new BigDecimal("14"));
        InternationalShipment shipment = InternationalShipment.of(1001, anna, null, List.of(dress), List.of(light, heavy, heavy),
                new BigDecimal("1000"), "UAH");

        assertThat(shipment.parcels()).extracting(Parcel::weightGrams).containsExactly(350, 1200, 1200);
        assertThat(shipment.parcels().getFirst().heightMm()).isEqualTo(65);
        assertThat(shipment.parcels()).extracting(Parcel::insuranceCost).containsOnly(new BigDecimal("333.33"));
        assertThat(shipment.parcels()).extracting(Parcel::rowNumber).containsExactly(1, 2, 3);
    }

    @Test
    void listsEverythingMissingInOneMessage() {
        var nobody = new Recipient("", null, null, null, "UA", null, null, null, null, null, null);
        var bare = new Item("Сукня", "", "abc", 0, null, null);

        assertThatThrownBy(() -> InternationalShipment.of(1001, nobody, null, List.of(bare), List.of(), BigDecimal.ZERO, null))
                .hasMessage("Не заповнені або некоректні поля: Прізвище, Ім'я, Телефон, Населений пункт, Вулиця, Будинок, Індекс, "
                        + "Країна отримувача (не Україна), код УКТЗЕД (Сукня), англійська назва товару (Сукня), "
                        + "вага товару в грамах (Сукня), декларована вартість, параметри місця відправлення (розміри/вага).");
    }

    @Test
    void theUsIrelandAndCanadaNeedARegion() {
        var john = new Recipient("John", "Smith", "12025550100", "", "US", " ", "Austin", "Congress Ave", "100", "", "78701");

        assertThatThrownBy(() -> InternationalShipment.of(1001, john, null, List.of(dress), List.of(medium), BigDecimal.TEN, null))
                .hasMessageContaining("Штат/область");
        var texas = new Recipient("John", "Smith", "12025550100", "", "US", "TX", "Austin", "Congress Ave", "100", "", "78701");
        assertThat(InternationalShipment.of(1001, texas, null, List.of(dress), List.of(medium), BigDecimal.TEN, null).recipient().region())
                .isEqualTo("TX");
    }
}
