package ua.newerp.web.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

import ua.newerp.application.common.PagedResult;
import ua.newerp.application.payments.BankPaymentDto;
import ua.newerp.application.payments.BankPaymentFilter;
import ua.newerp.application.payments.BankPaymentService;
import ua.newerp.application.payments.LinkPaymentRequest;
import ua.newerp.application.payments.PaymentSuggestionDto;
import ua.newerp.domain.payments.MatchReason;

/** The bank payments page with a mocked service; payers and orders are invented. */
@ViewPackages(classes = BankPaymentsView.class)
class BankPaymentsViewTest extends BrowserlessTest {

    final BankPaymentService service = mock(BankPaymentService.class);
    final LocalDateTime now = LocalDateTime.of(2026, 10, 22, 14, 5);
    final BankPaymentDto payment = new BankPaymentDto(5, "", "", new BigDecimal("1500"), BigDecimal.ZERO, "Коваленко Ірина",
            "Оплата за замовлення 1042", now, 0, "", true);

    @Test
    void showsUnlinkedPaymentsByDefaultAndFiltersBySearch() {
        when(service.list(any(BankPaymentFilter.class))).thenReturn(PagedResult.of(List.of(payment), 1, 50, 1));
        var view = new BankPaymentsView(service);
        UI.getCurrent().add(view);

        assertThat(view.filter(1, 50).linked()).isFalse();
        test(view.view).selectItem("приховані");
        test(view.search).setValue("1500");
        assertThat(view.filter(2, 50)).isEqualTo(new BankPaymentFilter(null, null, null, null, "1500", true, 2, 50));
        assertThat(view.fetch(0, 10)).containsExactly(payment);
    }

    @Test
    void matchDialogLinksTheChosenOrder() {
        var suggestion = new PaymentSuggestionDto(1042, "Коваленко Ірина", "+380671112233", new BigDecimal("1500"), BigDecimal.ZERO, now,
                "Новий", null, MatchReason.ORDER_NUMBER | MatchReason.TOTAL, 90);
        when(service.suggest(5, "")).thenReturn(List.of(suggestion));
        var linked = new BankPaymentDto(5, "", "", new BigDecimal("1500"), BigDecimal.ZERO, "Коваленко Ірина", "", now, 1042,
                "Коваленко Ірина", true);
        when(service.link(5, new LinkPaymentRequest(1042))).thenReturn(linked);
        BankPaymentDto[] result = new BankPaymentDto[1];
        var dialog = new MatchPaymentDialog(service, payment, p -> result[0] = p);
        dialog.open();
        roundTrip();

        dialog.link(suggestion);

        verify(service).link(5, new LinkPaymentRequest(1042));
        assertThat(result[0].orderId()).isEqualTo(1042);
        assertThat(MatchPaymentDialog.reasons(suggestion.reasons())).containsExactly("номер у призначенні", "сума");
    }
}
