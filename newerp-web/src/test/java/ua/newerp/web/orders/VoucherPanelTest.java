package ua.newerp.web.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;

import ua.newerp.application.marketing.OrderVoucherDto;
import ua.newerp.application.marketing.VoucherService;

/** The voucher block of the order card with a mocked service; the voucher is invented. */
@ViewPackages(packages = "ua.newerp.web.noroutes")
class VoucherPanelTest extends BrowserlessTest {

    final VoucherService service = mock(VoucherService.class);

    @Test
    void spendsACodeWithTheWholeAvailableSumWhenNoAmountIsTyped() {
        when(service.forOrder(1001)).thenReturn(Optional.empty());
        when(service.redeem(1001, "gift1000", null))
                .thenReturn(new OrderVoucherDto(1001, 1, "GIFT1000", new BigDecimal("650"), new BigDecimal("850"), BigDecimal.ZERO));
        List<String> reloads = new ArrayList<>();
        var panel = new VoucherPanel(service, 1001, () -> reloads.add("reload"));
        UI.getCurrent().add(panel);

        panel.code.setValue("gift1000");
        test(panel.redeem).click();

        verify(service).redeem(1001, "gift1000", null);
        assertThat(reloads).containsExactly("reload");
    }

    @Test
    void showsTheSpentVoucherInsteadOfTheForm() {
        when(service.forOrder(1002))
                .thenReturn(Optional.of(new OrderVoucherDto(1002, 1, "GIFT1000", new BigDecimal("300"), new BigDecimal("500"), new BigDecimal("350"))));
        var panel = new VoucherPanel(service, 1002, () -> { });
        UI.getCurrent().add(panel);

        assertThat(panel.code.isAttached()).isFalse();
        assertThat(panel.release.isAttached()).isTrue();
    }
}
