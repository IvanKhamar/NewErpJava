package ua.newerp.web.customers;

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
import com.vaadin.flow.component.html.H2;

import ua.newerp.application.common.PagedResult;
import ua.newerp.application.customers.AddCustomerNoteRequest;
import ua.newerp.application.customers.CustomerAddressDto;
import ua.newerp.application.customers.CustomerDetails;
import ua.newerp.application.customers.CustomerFilter;
import ua.newerp.application.customers.CustomerGroupDto;
import ua.newerp.application.customers.CustomerListItem;
import ua.newerp.application.customers.CustomerOrderItem;
import ua.newerp.application.customers.CustomerService;
import ua.newerp.application.customers.CustomerStatsDto;
import ua.newerp.web.orders.OrderCardView;
import ua.newerp.web.orders.OrderListView;

/** The customer list and card with a mocked service; customer 5 and its orders are invented. */
@ViewPackages(classes = {OrderListView.class, OrderCardView.class, CustomerListView.class, CustomerCardView.class})
class CustomerViewsTest extends BrowserlessTest {

    final CustomerService customers = mock(CustomerService.class);
    final LocalDateTime added = LocalDateTime.of(2026, 3, 1, 9, 0);

    @Test
    void listFiltersByGroupAndState() {
        when(customers.groups()).thenReturn(List.of(new CustomerGroupDto(1, "Роздріб"), new CustomerGroupDto(2, "Опт")));
        when(customers.list(any(CustomerFilter.class))).thenReturn(PagedResult.of(List.of(new CustomerListItem(5, "Марта Ткач",
                "marta@example.com", "380501234567", "Опт", true, added, 3)), 1, 20, 1));
        var view = new CustomerListView(customers);
        UI.getCurrent().add(view);
        test(view.group).selectItem("Опт");
        test(view.state).selectItem("Вимкнені");

        assertThat(view.filter(1, 20)).isEqualTo(new CustomerFilter("", 2, false, 1, 20));
        assertThat(view.fetch(0, 50)).extracting(CustomerListItem::id).containsExactly(5);
    }

    @Test
    void cardShowsTheCustomerAndAddsANote() {
        when(customers.get(5)).thenReturn(new CustomerDetails(5, "Марта", "Ткач", "marta@example.com", "380501234567", "Опт", true, false,
                added, List.of(new CustomerAddressDto(1, "Марта Ткач", "Львів", "вул. Липова, 3", "", true)),
                List.of(new CustomerOrderItem(990, added, 7, "Скасовано", new BigDecimal("900"), "UAH", false)),
                new CustomerStatsDto(1, 1, BigDecimal.ZERO, added, added, "Red", "Клієнт скасовував замовлення."), List.of()));
        var view = new CustomerCardView(customers);
        UI.getCurrent().add(view);
        view.open(5);

        assertThat(find(H2.class, view).single().getText()).isEqualTo("Марта Ткач");
        test(view.note).setValue("Просить дзвонити після 18:00");
        test(view.addNote).click();

        verify(customers).addNote(5, new AddCustomerNoteRequest("Просить дзвонити після 18:00"));
    }
}
