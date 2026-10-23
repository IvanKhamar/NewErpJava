package ua.newerp.web.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;

import ua.newerp.application.marketing.BirthdayContactDto;
import ua.newerp.application.marketing.BirthdaySmsRequest;
import ua.newerp.application.marketing.BirthdaySmsResult;
import ua.newerp.application.marketing.BulkSmsRequest;
import ua.newerp.application.marketing.BulkSmsResult;
import ua.newerp.application.marketing.ContactExportRequest;
import ua.newerp.application.marketing.CouponCodeDto;
import ua.newerp.application.marketing.MarketingService;
import ua.newerp.domain.marketing.BirthdayPeriod;
import ua.newerp.domain.marketing.ContactField;
import ua.newerp.domain.marketing.ContactSource;

/** The marketing pages with a mocked service; the people are invented. */
@ViewPackages(packages = "ua.newerp.web.noroutes")
class MarketingViewsTest extends BrowserlessTest {

    final MarketingService service = mock(MarketingService.class);
    final BirthdayContactDto oksana = new BirthdayContactDto("380671112233", "Оксана", "Мельник", "", "Київ", "", 23, 10,
            LocalDate.of(2026, 10, 23), 0);
    final BirthdayContactDto taras = new BirthdayContactDto("380502223344", "Тарас", "Ткач", "", "Львів", "@taras", 23, 10,
            LocalDate.of(2026, 10, 23), 1);

    @Test
    void greetingWithACouponGoesToTheClickedPerson() {
        when(service.birthdays(BirthdayPeriod.TODAY)).thenReturn(List.of(oksana));
        when(service.customerOrders("380671112233")).thenReturn(List.of());
        when(service.newCouponCode()).thenReturn(new CouponCodeDto("BD7K2Q"));
        String text = "Оксана, вітаємо з днем народження! Ваш подарунок — знижка за промокодом BD7K2Q. Vidro";
        when(service.sendBirthdaySms(new BirthdaySmsRequest("380671112233", text, "BD7K2Q"))).thenReturn(new BirthdaySmsResult(true, 12, "ok"));
        var view = new BirthdaysView(service);
        UI.getCurrent().add(view);

        view.select(oksana);
        test(view.newCoupon).click();
        assertThat(view.text.getValue()).isEqualTo(text);
        test(view.send).click();

        verify(service).sendBirthdaySms(new BirthdaySmsRequest("380671112233", text, "BD7K2Q"));
        assertThat(view.greeting.isVisible()).isFalse();
    }

    @Test
    void oneTextGoesToAllSelected() {
        when(service.birthdays(BirthdayPeriod.TODAY)).thenReturn(List.of(oksana, taras));
        when(service.sendBulk(new BulkSmsRequest(List.of("380671112233", "380502223344"), "З днем народження! Vidro")))
                .thenReturn(new BulkSmsResult(2, 0, List.of()));
        var view = new BirthdaysView(service);
        UI.getCurrent().add(view);

        view.grid.select(oksana);
        view.grid.select(taras);
        assertThat(view.bulk.isVisible()).isTrue();
        test(view.bulkText).setValue("З днем народження! Vidro");
        test(view.sendBulk).click();

        verify(service).sendBulk(new BulkSmsRequest(List.of("380671112233", "380502223344"), "З днем народження! Vidro"));
    }

    @Test
    void exportUsesTheSourcesOfTheChosenField() {
        when(service.exportCsv(new ContactExportRequest(ContactField.EMAIL, EnumSet.of(ContactSource.SUBSCRIBERS), null, null)))
                .thenReturn("email;name\n");
        var view = new ContactExportView(service);
        UI.getCurrent().add(view);

        test(view.field).selectItem("E-mail");
        test(view.orders).click();
        byte[] csv = view.csv();

        assertThat(view.questions.isVisible()).isFalse();
        assertThat(new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8)).isEqualTo("email;name\n");
        test(view.subscribers).click();
        assertThat(view.download.isEnabled()).isFalse();
    }
}
