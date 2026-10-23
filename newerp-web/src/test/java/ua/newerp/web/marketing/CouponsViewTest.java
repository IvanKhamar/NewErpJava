package ua.newerp.web.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;

import ua.newerp.application.marketing.CouponDto;
import ua.newerp.application.marketing.CouponService;
import ua.newerp.application.marketing.SaveCouponRequest;
import ua.newerp.domain.marketing.Coupon;

/** The coupons page with a mocked service; the coupons are invented. */
@ViewPackages(packages = "ua.newerp.web.noroutes")
class CouponsViewTest extends BrowserlessTest {

    final CouponService service = mock(CouponService.class);
    final CouponDto autumn = new CouponDto(1, "Осінь −10%", "AUTUMN10", Coupon.PERCENT, BigDecimal.TEN, new BigDecimal("500"), false, false,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30), 100, 1, true, LocalDateTime.of(2026, 9, 1, 10, 0), 3,
            new BigDecimal("450"), Coupon.ACTIVE);

    @Test
    void listsCouponsAndFiltersByState() {
        when(service.list(null)).thenReturn(List.of(autumn));
        var view = new CouponsView(service);
        UI.getCurrent().add(view);

        assertThat(test(view.grid).getCellText(0, 0)).isEqualTo("AUTUMN10");
        assertThat(test(view.grid).getCellText(0, 2)).isEqualTo("10%");
        assertThat(test(view.grid).getCellText(0, 5)).isEqualTo("3 з 100");

        test(view.state).selectItem("Вимкнено");
        verify(service).list(Coupon.DISABLED);
    }

    @Test
    void createsAndTurnsOffACoupon() {
        var dialog = new CouponDialog(service, null, () -> { });
        dialog.open();
        roundTrip();
        test(dialog.name).setValue("Зима");
        test(dialog.code).setValue("WINTER200");
        test(dialog.type).selectItem("сума, ₴");
        test(dialog.discount).setValue(new BigDecimal("200"));
        test(dialog.save).click();
        verify(service).create(any(SaveCouponRequest.class));

        var edit = new CouponDialog(service, autumn, () -> { });
        edit.open();
        roundTrip();
        test(edit.active).click();
        test(edit.save).click();
        verify(service).update(eq(1), eq(new SaveCouponRequest("Осінь −10%", "AUTUMN10", Coupon.PERCENT, BigDecimal.TEN,
                new BigDecimal("500"), false, false, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30), 100, 1, false)));
    }
}
