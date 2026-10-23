package ua.newerp.web.returns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.application.common.PagedResult;
import ua.newerp.application.returns.AddReturnHistoryRequest;
import ua.newerp.application.returns.ReturnDetails;
import ua.newerp.application.returns.ReturnFilter;
import ua.newerp.application.returns.ReturnHistoryEntry;
import ua.newerp.application.returns.ReturnListItem;
import ua.newerp.application.returns.ReturnLookupItem;
import ua.newerp.application.returns.ReturnLookupsDto;
import ua.newerp.application.returns.ReturnService;
import ua.newerp.web.orders.OrderCardView;
import ua.newerp.web.orders.OrderListView;

/** The return list and card with a mocked service; return 77 of order 1001 is invented. */
@ViewPackages(classes = {OrderListView.class, OrderCardView.class, ReturnListView.class, ReturnCardView.class})
class ReturnViewsTest extends BrowserlessTest {

    final ReturnService returns = mock(ReturnService.class);
    final LocalDateTime added = LocalDateTime.of(2026, 10, 5, 12, 0);

    @BeforeEach
    void lookups() {
        when(returns.lookups()).thenReturn(new ReturnLookupsDto(List.of(new ReturnLookupItem(1, "Очікує"), new ReturnLookupItem(3, "Завершено")),
                List.of(new ReturnLookupItem(2, "Не підійшов розмір")), List.of(new ReturnLookupItem(1, "Гроші повернуто"))));
    }

    @Test
    void listFiltersByStatus() {
        when(returns.list(any(ReturnFilter.class))).thenReturn(PagedResult.of(List.of(new ReturnListItem(77, 1001, "Ірина Шевчук",
                "380671112233", "Сукня лляна", "SL-01", 1, 1, "Очікує", null, added, added)), 1, 20, 1));
        var view = new ReturnListView(returns);
        UI.getCurrent().add(view);
        test(view.status).selectItem("Завершено");

        assertThat(view.filter(1, 20).statusId()).isEqualTo(3);
        assertThat(view.fetch(0, 50)).extracting(ReturnListItem::id).containsExactly(77);
    }

    @Test
    void cardSavesANewStatusWithAction() {
        when(returns.get(77)).thenReturn(new ReturnDetails(77, 1001, 50, "Ірина Шевчук", "380671112233", "iryna@example.com",
                "Сукня лляна", "SL-01", 1, true, 2, "Не підійшов розмір", 0, null, 1, "Очікує", "", added, added,
                List.of(new ReturnHistoryEntry(added, 1, "Очікує", "Заявка з сайту"))));
        var view = new ReturnCardView(returns);
        UI.getCurrent().add(view);
        view.open(77);

        assertThat(find(H2.class, view).single().getText()).isEqualTo("Повернення №77");
        test(view.status).selectItem("Завершено");
        test(view.action).selectItem("Гроші повернуто");
        test(view.comment).setValue("Кошти на картку");
        test(view.save).click();

        verify(returns).addHistory(77, new AddReturnHistoryRequest(3, "Кошти на картку", 1));
    }

    @Test
    void unknownReturnSaysSo() {
        when(returns.get(5)).thenThrow(new NotFoundException("Повернення 5 не знайдено."));
        var view = new ReturnCardView(returns);
        UI.getCurrent().add(view);
        view.open(5);

        assertThat(find(Paragraph.class, view).single().getText()).isEqualTo("Повернення №5 не знайдено.");
    }
}
