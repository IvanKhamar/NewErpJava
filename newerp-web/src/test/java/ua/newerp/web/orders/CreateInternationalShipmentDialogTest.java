package ua.newerp.web.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;

import ua.newerp.application.delivery.CreateInternationalShipmentRequest;
import ua.newerp.application.delivery.HsCodeDto;
import ua.newerp.application.delivery.InternationalFormDto;
import ua.newerp.application.delivery.InternationalShipmentService;
import ua.newerp.application.delivery.ShipmentDto;
import ua.newerp.domain.delivery.InternationalShipment;
import ua.newerp.domain.delivery.SeatPreset;

/** The dialog with a mocked service; the recipient and the order are invented. */
@ViewPackages(packages = "ua.newerp.web.noroutes")
class CreateInternationalShipmentDialogTest extends BrowserlessTest {

    final InternationalShipmentService service = mock(InternationalShipmentService.class);
    final InternationalFormDto.Country poland = new InternationalFormDto.Country("PL", "Польща");
    final InternationalFormDto.Country usa = new InternationalFormDto.Country("US", "Сполучені Штати");

    @Test
    void sendsTheFormWithTheTypedCustomsFields() {
        var anna = new InternationalShipment.Recipient("Anna", "Kowalska", "48600100200", "anna@example.com", "PL", "", "Kraków",
                "Floriańska 5", "", "", "31-019");
        var dress = new InternationalShipment.Item("Сукня лляна", "", "", 0, new BigDecimal("2"), new BigDecimal("3000"));
        when(service.form(1001)).thenReturn(new InternationalFormDto(anna, InternationalShipment.Terms.defaults(), List.of(dress),
                new BigDecimal("3000"), "UAH", SeatPreset.ALL, true, true));
        when(service.countries()).thenReturn(List.of(poland, usa));
        var created = new ArrayList<ShipmentDto>();
        var shipment = new ShipmentDto(1, 1001, "international", "TEST0000000001", new BigDecimal("3000"), "", 0, 0, false, "", false, null, null,
                null);
        when(service.create(eq(1001), any(), eq(7))).thenReturn(shipment);

        var dialog = new CreateInternationalShipmentDialog(service, 1001, 7, created::add);
        dialog.open();
        roundTrip();
        assertThat(dialog.country.getValue()).isEqualTo(poland);
        assertThat(dialog.region.isRequiredIndicatorVisible()).isFalse();
        dialog.country.setValue(usa);
        assertThat(dialog.region.isRequiredIndicatorVisible()).isTrue();
        dialog.country.setValue(poland);

        dialog.building.setValue("5");
        var row = dialog.items.getFirst();
        row.hsCode.setValue(new HsCodeDto("6204491000", "Сукні", "Linen dress"));
        row.weight.setValue(450);
        assertThat(row.nameEng.getValue()).isEqualTo("Linen dress"); // filled from the HS suggestion
        test(dialog.create).click();

        ArgumentCaptor<CreateInternationalShipmentRequest> request = ArgumentCaptor.forClass(CreateInternationalShipmentRequest.class);
        verify(service).create(eq(1001), request.capture(), eq(7));
        assertThat(request.getValue().recipient().countryCode()).isEqualTo("PL");
        assertThat(request.getValue().recipient().building()).isEqualTo("5");
        assertThat(request.getValue().items()).containsExactly(
                new InternationalShipment.Item("Сукня лляна", "Linen dress", "6204491000", 450, new BigDecimal("2"), new BigDecimal("3000")));
        assertThat(request.getValue().parcels()).containsExactly(SeatPreset.ALL.getFirst().seat());
        assertThat(request.getValue().currency()).isEqualTo("UAH");
        assertThat(created).containsExactly(shipment);
    }
}
