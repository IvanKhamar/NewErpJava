package ua.newerp.domain.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ua.newerp.domain.TestIds.withId;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ua.newerp.domain.stock.PurchaseOrder.LineInput;
import ua.newerp.domain.stock.PurchaseOrder.Receipt;

class PurchaseOrderTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 0);

    static LineInput line(int itemId, String qty) {
        return new LineInput(itemId, new BigDecimal(qty), BigDecimal.TEN);
    }

    /** An order as if loaded from the database: ids on the order and its lines. */
    static PurchaseOrder ordered() {
        PurchaseOrder order = withId(new PurchaseOrder(3, List.of(line(1, "10"), line(2, "5")), null, 1, NOW), 42);
        withId(order.getLines().get(0), 101);
        withId(order.getLines().get(1), 102);
        order.markOrdered(NOW);
        return order;
    }

    @Test
    void newOrderIsDraft() {
        PurchaseOrder order = new PurchaseOrder(3, List.of(line(1, "10")), "  терміново ", 1, NOW);
        assertThat(order.getStatus()).isEqualTo(PurchaseOrder.DRAFT);
        assertThat(order.getComment()).isEqualTo("терміново");
        assertThat(order.isOpen()).isTrue();
    }

    @Test
    void validatesLines() {
        assertThatThrownBy(() -> new PurchaseOrder(3, List.of(), null, 1, NOW))
                .hasMessage("Замовлення має містити хоча б одну позицію.");
        assertThatThrownBy(() -> new PurchaseOrder(3, List.of(line(1, "0")), null, 1, NOW))
                .hasMessage("Кількість у замовленні має бути більшою за нуль.");
        assertThatThrownBy(() -> new PurchaseOrder(3, List.of(line(2, "1"), line(1, "1"), line(1, "2")), null, 1, NOW))
                .hasMessage("Позицію 1 указано двічі.");
    }

    @Test
    void sentOrderLinesCannotChange() {
        PurchaseOrder order = ordered();
        assertThatThrownBy(() -> order.setLines(List.of(line(1, "1"))))
                .hasMessage("Замовлення №42 у статусі ordered: змінювати рядки не можна.");
    }

    @Test
    void receivesInPartsAndClosesWhenAllArrived() {
        PurchaseOrder order = ordered();
        StockItem item1 = new StockItem(null, 3, "Льон", "м", BigDecimal.ZERO, null, BigDecimal.ZERO);
        StockItem item2 = new StockItem(null, 3, "Ґудзики", "шт", BigDecimal.ZERO, null, BigDecimal.ZERO);
        Map<Integer, StockItem> items = Map.of(1, item1, 2, item2);

        List<StockMovement> first = order.receive(List.of(new Receipt(101, new BigDecimal("4"))), items, 1, "a", NOW);

        assertThat(first).singleElement().satisfies(m ->
                assertThat(m.getComment()).isEqualTo("Прихід за замовленням постачальнику №42"));
        assertThat(item1.getQuantity()).isEqualByComparingTo("4");
        assertThat(order.getStatus()).isEqualTo(PurchaseOrder.ORDERED);

        order.receive(List.of(new Receipt(101, new BigDecimal("7")), new Receipt(102, new BigDecimal("5"))), items, 1, "a", NOW);

        assertThat(order.getLines().get(0).getRemaining()).isEqualByComparingTo("0");
        assertThat(order.getStatus()).isEqualTo(PurchaseOrder.RECEIVED);
        assertThat(order.getClosedAt()).isEqualTo(NOW);
        assertThat(order.isOpen()).isFalse();
    }

    @Test
    void receiveRejectsForeignLine() {
        PurchaseOrder order = ordered();
        assertThatThrownBy(() -> order.receive(List.of(new Receipt(999, BigDecimal.ONE)), Map.of(), 1, "a", NOW))
                .hasMessage("Рядок 999 не належить замовленню №42.");
    }

    @Test
    void cancelOnlyOpen() {
        PurchaseOrder order = ordered();
        order.cancel(NOW);
        assertThat(order.getStatus()).isEqualTo(PurchaseOrder.CANCELLED);
        assertThatThrownBy(() -> order.cancel(NOW)).hasMessage("Замовлення №42 вже закрите.");
    }
}
