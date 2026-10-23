package ua.newerp.web.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;

import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.cashbox.CashboxService;
import ua.newerp.application.cashbox.CashboxSetupDto;
import ua.newerp.application.cashbox.CashierDto;
import ua.newerp.application.cashbox.ChequeFilter;
import ua.newerp.application.cashbox.DirectoryEntryDto;
import ua.newerp.application.cashbox.SaveCashierRequest;
import ua.newerp.application.cashbox.WorkShiftFilter;
import ua.newerp.application.common.PagedResult;
import ua.newerp.web.auth.CurrentSession;

/** The cash register page with a mocked service; cashiers and warehouses are invented. */
@ViewPackages(classes = CashboxView.class)
class CashboxViewTest extends BrowserlessTest {

    final CashboxService service = mock(CashboxService.class);
    final CurrentSession session = mock(CurrentSession.class);
    final DirectoryEntryDto shop = new DirectoryEntryDto(2, "Магазин на Подолі", "");
    final CashierDto cashier = new CashierDto(1, "Ірина", "", "cod", null, 2, "Магазин на Подолі", 0, "", true, true, null, null);

    @BeforeEach
    void setUp() {
        when(service.setup()).thenReturn(new CashboxSetupDto(List.of(cashier), List.of(shop), List.of(), true));
        when(service.cheques(any(ChequeFilter.class))).thenReturn(PagedResult.of(List.of(), 1, 50, 0));
        when(service.shifts(any(WorkShiftFilter.class))).thenReturn(List.of());
        when(service.reports(null, null, null)).thenReturn(List.of());
        when(session.user()).thenReturn(Optional.of(new CurrentUser(7, "olena", "Олена Коваль", "Manager", false)));
    }

    @Test
    void managerOpensAShiftButDoesNotSeeTheSetup() {
        when(session.hasAnyRole("Admin")).thenReturn(false);
        var view = new CashboxView(service, session);
        UI.getCurrent().add(view);

        assertThat(view.newCashier.isVisible()).isFalse();
        view.openShift(cashier);
        verify(service).openShift(1, 7);

        view.chequeState.setValue(CashboxView.ChequeState.NOT_ACCEPTED); // the tab is not attached until it is opened
        assertThat(view.chequeFilter(1, 50).fiscalized()).isFalse();
    }

    @Test
    void cashierDialogSavesTheChosenWarehouse() {
        var dialog = new CashierDialog(service, service.setup(), null, () -> { });
        dialog.open();
        roundTrip();
        assertThat(dialog.save.isEnabled()).isFalse();

        test(dialog.name).setValue("Тарас");
        test(dialog.warehouse).selectItem("Магазин на Подолі");
        dialog.autoClose.setValue(LocalTime.of(21, 0));
        test(dialog.save).click();

        verify(service).createCashier(new SaveCashierRequest("Тарас", "", "", LocalTime.of(21, 0), 2, 0, true));
    }
}
