package ua.newerp.web.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;

import ua.newerp.application.marketing.SaveVoucherRequest;
import ua.newerp.application.marketing.VoucherCardDto;
import ua.newerp.application.marketing.VoucherService;
import ua.newerp.application.sms.SmsSendResult;

/** The vouchers page and dialog with a mocked service; the vouchers are invented. */
@ViewPackages(packages = "ua.newerp.web.noroutes")
class VouchersViewTest extends BrowserlessTest {

    final VoucherService service = mock(VoucherService.class);
    final VoucherCardDto gift = new VoucherCardDto(1, 0, "GIFT1000", "Магазин", "", "Олена", "", 0, "", "", new BigDecimal("1000"),
            new BigDecimal("650"), true, true, LocalDateTime.of(2026, 9, 1, 10, 0),
            List.of(new VoucherCardDto.HistoryRow(1001, "Ірина Шевчук", new BigDecimal("-350"), LocalDateTime.of(2026, 10, 1, 10, 0))));

    @Test
    void listShowsTheStateAndTheDialogSavesAndTexts() {
        when(service.list(anyString())).thenReturn(List.of(gift));
        when(service.get(1)).thenReturn(gift);
        when(service.sendSms(1, "0671112233")).thenReturn(new SmsSendResult(false, "SMS-ключ не задано"));
        when(service.update(eq(1), any())).thenReturn(gift);
        var view = new VouchersView(service);
        UI.getCurrent().add(view);
        assertThat(test(view.grid).size()).isEqualTo(1);
        assertThat(VouchersView.state(gift).getText()).isEqualTo("діє");

        var dialog = new VoucherDialog(service, 1, view::load);
        dialog.open();
        roundTrip();
        assertThat(dialog.delete.isEnabled()).isFalse(); // already spent in an order
        dialog.amount.setValue(new BigDecimal("1200"));
        test(dialog.save).click();
        verify(service).update(1, new SaveVoucherRequest("GIFT1000", "Магазин", "", "Олена", "", 0, "", new BigDecimal("1200"), true));

        var again = new VoucherDialog(service, 1, view::load);
        again.open();
        roundTrip();
        again.phone.setValue("0671112233");
        test(again.sendSms).click();
        verify(service).sendSms(1, "0671112233");
    }
}
