package ua.newerp.web.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;

import ua.newerp.application.common.LookupItem;
import ua.newerp.application.common.PagedResult;
import ua.newerp.application.orders.OrderFilter;
import ua.newerp.application.orders.OrderListItem;
import ua.newerp.application.orders.OrderQueries;
import ua.newerp.application.orders.OrderStatusDto;

/**
 * The view without a browser and without a database: BrowserlessTest gives a Vaadin session and UI in memory,
 * the service is a Mockito mock (like Moq). 120 invented orders, the server gives pages of PAGE_SIZE.
 */
// Views are created by hand with mocked services, so no routes are scanned (Vaadin could not create them itself).
@ViewPackages(packages = "ua.newerp.web.noroutes")
class OrderListViewTest extends BrowserlessTest {

    final OrderQueries queries = mock(OrderQueries.class);
    final List<OrderListItem> orders = IntStream.rangeClosed(1, 120).mapToObj(OrderListViewTest::order).toList();
    OrderListView view;

    @BeforeEach
    void open() {
        when(queries.listStatuses()).thenReturn(List.of(new OrderStatusDto(1, "Нове", "#ffcc00"), new OrderStatusDto(5, "Виконано", "")));
        when(queries.listSources()).thenReturn(List.of(new LookupItem(1, "Сайт")));
        when(queries.listManagers()).thenReturn(List.of(new LookupItem(7, "Олена Коваль")));
        when(queries.list(any())).thenAnswer(call -> {
            OrderFilter filter = call.getArgument(0);
            int from = (filter.page() - 1) * filter.pageSize();
            return PagedResult.of(orders.subList(Math.min(from, orders.size()), Math.min(from + filter.pageSize(), orders.size())),
                    filter.page(), filter.pageSize(), orders.size());
        });
        view = new OrderListView(queries);
        UI.getCurrent().add(view);
    }

    @Test
    void gridShowsTheFirstPageWithTheTotalCount() {
        var grid = test(view.grid);
        assertThat(grid.size()).isEqualTo(120);
        assertThat(grid.getCellText(0, 0)).isEqualTo("1");
        assertThat(grid.getCellText(0, 2)).isEqualTo("Клієнт 1");
        assertThat(grid.getCellText(0, 4)).isEqualTo("1 250,50 ₴".replace(' ', ' ').replaceFirst(" ₴", " ₴"));
    }

    @Test
    void fetchJoinsServerPagesForAnyOffset() {
        List<OrderListItem> rows = view.fetch(30, 70);
        assertThat(rows).extracting(OrderListItem::id).containsExactlyElementsOf(IntStream.rangeClosed(31, 100).boxed().toList());
        assertThat(view.fetch(100, 50)).extracting(OrderListItem::id).containsExactlyElementsOf(IntStream.rangeClosed(101, 120).boxed().toList());
    }

    @Test
    void filtersGoToTheQuery() {
        test(view.status).selectItem("Нове");
        test(view.manager).selectItem("Олена Коваль");
        test(view.search).setValue("0671112233");
        test(view.from).setValue(LocalDate.of(2026, 10, 1));

        Mockito.clearInvocations(queries);
        view.fetch(0, 10);
        var filter = ArgumentCaptor.forClass(OrderFilter.class);
        Mockito.verify(queries, Mockito.atLeastOnce()).list(filter.capture());
        assertThat(filter.getValue()).isEqualTo(new OrderFilter(1, null, 7, LocalDate.of(2026, 10, 1), null, "0671112233", 1,
                OrderListView.PAGE_SIZE));
    }

    private static OrderListItem order(int id) {
        return new OrderListItem(id, LocalDateTime.of(2026, 10, 1, 12, 0), "Клієнт " + id, "38067" + id, new BigDecimal("1250.50"),
                "UAH", 1, "Нове", "Сайт", 7, "Олена Коваль");
    }
}
