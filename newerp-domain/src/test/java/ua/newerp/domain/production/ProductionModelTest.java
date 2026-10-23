package ua.newerp.domain.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ua.newerp.domain.TestIds.withId;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import ua.newerp.domain.common.DomainException;

import ua.newerp.domain.production.ProductionModel.CategoryLinkInput;
import ua.newerp.domain.production.ProductionModel.SewnBomRow;
import ua.newerp.domain.production.ProductionModel.SizeInput;

class ProductionModelTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 0);

    static SizeInput size(String name) {
        return new SizeInput(name, new BigDecimal("50"));
    }

    /** Model "Сукня" with sizes S (id 1) and M (id 2), as if loaded from the database. */
    static ProductionModel dress() {
        ProductionModel model = withId(new ProductionModel("Сукня лляна", ProductionModel.CLOTHING, 10, 0), 7);
        model.setSizes(List.of(size("S"), size("M")));
        withId(model.getSizes().get(0), 1);
        withId(model.getSizes().get(1), 2);
        return model;
    }

    static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    @Test
    void storefrontQuantitiesAreTheWholeSewnStockOfLinkedSizes() {
        ProductionModel model = withId(new ProductionModel("Сукня", ProductionModel.CLOTHING, 0, 0), 7);
        model.setSizes(List.of(size("S"), size("M"), size("L"), size("XL")));
        for (int i = 0; i < 4; i++) {
            withId(model.getSizes().get(i), i + 1);
        }
        model.recordSewn(1, n("4.7"), null, 1, "a", NOW);
        model.recordSewn(2, n("2"), null, 1, "a", NOW);
        model.recordSewn(3, n("9"), null, 1, "a", NOW);
        // L is linked to the same value as M; XL is not linked
        model.setSizeOptions(List.of(new ProductionModel.SizeOption(1, 46), new ProductionModel.SizeOption(2, 47),
                new ProductionModel.SizeOption(3, 47)));

        assertThat(model.storefrontQuantities()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(46, 4, 47, 2));
    }

    @Test
    void validatesTypeAndMinTotal() {
        assertThatThrownBy(() -> new ProductionModel("X", "shoes", 0, 0))
                .hasMessage("Тип моделі має бути clothing або ready_made.");
        assertThatThrownBy(() -> new ProductionModel("X", ProductionModel.CLOTHING, -1, 0))
                .hasMessage("Мінімальний запас не може бути від'ємним.");
    }

    @Test
    void setSizesKeepsExistingByNameIgnoringCase() {
        ProductionModel model = dress();
        ProductionSize m = model.getSizes().get(1);

        List<Integer> removed = model.setSizes(List.of(size("m"), size("L"), size("S")));

        assertThat(removed).isEmpty();
        assertThat(model.getSizes()).extracting(ProductionSize::getName).containsExactly("S", "M", "L");
        assertThat(m.getSortOrder()).isEqualTo(1);
        assertThat(model.getSizes().get(0).getSortOrder()).isEqualTo(3);
    }

    @Test
    void setSizesRejectsDuplicatesAndRemovingSizeWithStock() {
        ProductionModel model = dress();
        assertThatThrownBy(() -> model.setSizes(List.of(size("S"), size("XL"), size("xl"))))
                .hasMessage("Розмір \"XL\" указано двічі.");

        model.recordSewn(2, n("3"), null, 1, "a", NOW);
        assertThatThrownBy(() -> model.setSizes(List.of(size("S"))))
                .hasMessage("Розмір \"M\" не можна прибрати: на ньому є залишок.");
    }

    @Test
    void setSizesReturnsRemovedIds() {
        ProductionModel model = dress();
        assertThat(model.setSizes(List.of(size("S")))).containsExactly(2);
        assertThat(model.getSizes()).hasSize(1);
    }

    @Test
    void cutSendToSewingSewnShip() {
        ProductionModel model = dress();

        ProductionLogEntry cut = model.recordCut(1, n("5"), " партія ", 3, "olena", NOW);
        assertThat(cut.getStage()).isEqualTo(ProductionLogEntry.CUT);
        assertThat(cut.getDirection()).isEqualTo(ProductionLogEntry.IN);
        assertThat(cut.getComment()).isEqualTo("партія");
        assertThat(cut.getSizeName()).isEqualTo("S");

        ProductionLogEntry toSewing = model.sendToSewing(1, n("2"), null, 3, "olena", NOW);
        assertThat(toSewing.getDirection()).isEqualTo(ProductionLogEntry.OUT);
        assertThat(toSewing.getStockBefore()).isEqualByComparingTo("5");
        assertThat(toSewing.getStockAfter()).isEqualByComparingTo("3");

        model.recordSewn(1, n("2"), null, 3, "olena", NOW);
        model.ship(1, n("1"), null, 3, "olena", NOW);
        assertThat(model.getTotalSewn()).isEqualByComparingTo("1");

        assertThatThrownBy(() -> model.sendToSewing(1, n("4"), null, 3, "olena", NOW))
                .hasMessage("Розмір S: розкроєно лише 3, а вказано 4.");
        assertThatThrownBy(() -> model.ship(1, n("2"), null, 3, "olena", NOW))
                .hasMessage("Розмір S: пошито лише 1, а вказано 2.");
        assertThatThrownBy(() -> model.recordCut(1, n("0"), null, 3, "olena", NOW))
                .hasMessage("Кількість має бути більшою за нуль.");
        assertThatThrownBy(() -> model.recordCut(99, n("1"), null, 3, "olena", NOW))
                .hasMessage("Розмір 99 не належить моделі \"Сукня лляна\".");
    }

    @Test
    void recountSetsTheCountedStockAndLogsTheDifference() {
        ProductionModel model = dress();
        model.recordCut(1, n("5"), null, 3, "olena", NOW);

        ProductionLogEntry cut = model.recount(1, ProductionLogEntry.CUT, n("3"), "Інвентаризація", 3, "olena", NOW);
        assertThat(cut.getDirection()).isEqualTo(ProductionLogEntry.OUT);
        assertThat(cut.getQuantity()).isEqualByComparingTo("2");
        assertThat(cut.getStockAfter()).isEqualByComparingTo("3");
        ProductionLogEntry sewn = model.recount(2, ProductionLogEntry.SEWN, n("4"), "Інвентаризація", 3, "olena", NOW);
        assertThat(sewn.getDirection()).isEqualTo(ProductionLogEntry.IN);
        assertThat(model.getTotalSewn()).isEqualByComparingTo("4");
        assertThat(model.recount(1, ProductionLogEntry.CUT, n("3.000"), "Інвентаризація", 3, "olena", NOW)).isNull();

        assertThatThrownBy(() -> model.recount(1, ProductionLogEntry.SEWN, n("-1"), null, 3, "olena", NOW))
                .hasMessage("Розмір S: кількість має бути від 0, не більше 3 знаків після коми.");
        assertThatThrownBy(() -> model.recount(1, "boxed", n("1"), null, 3, "olena", NOW)).isInstanceOf(DomainException.class);

        var stock = new ComponentStock(5, 1);
        stock.add(n("4"));
        assertThat(stock.recount(n("4.0"))).isFalse();
        assertThat(stock.recount(n("1"))).isTrue();
        assertThat(stock.getStock()).isEqualByComparingTo("1");
    }

    @Test
    void orderTakesWhatThereIsAndReturns() {
        ProductionModel model = dress();
        model.recordSewn(2, n("2"), null, 1, "a", NOW);

        ProductionLogEntry taken = model.takeSewnForOrder(2, n("5"), 1001, 1, "system", NOW);

        assertThat(taken.getQuantity()).isEqualByComparingTo("2");
        assertThat(taken.getComment()).isEqualTo("Замовлення #1001");
        assertThat(model.takeSewnForOrder(2, n("1"), 1001, 1, "system", NOW)).isNull();

        ProductionLogEntry back = model.returnSewnFromOrder(2, n("2"), 1001, 1, "system", NOW);
        assertThat(back.getComment()).isEqualTo("Скасування #1001");
        assertThat(model.getSizes().get(1).getSewnStock()).isEqualByComparingTo("2");
    }

    @Test
    void sewnBomRowIsComponentOrItem() {
        ProductionModel model = dress();
        assertThatThrownBy(() -> model.setSewnBom(List.of(new SewnBomRow(null, 10, 20, n("1"))), List.of(10)))
                .hasMessage("Рядок специфікації пошиву: вкажіть або деталь крою, або розхідник.");
        assertThatThrownBy(() -> model.setSewnBom(List.of(new SewnBomRow(null, 11, null, n("1"))), List.of(10)))
                .hasMessage("Деталь 11 не належить цій моделі.");
        assertThatThrownBy(() -> model.setSewnBom(
                List.of(new SewnBomRow(1, null, 20, n("1")), new SewnBomRow(1, null, 20, n("2"))), List.of()))
                .hasMessage("Специфікація пошиву містить повторювані рядки.");

        model.setSewnBom(List.of(new SewnBomRow(null, 10, null, n("2")), new SewnBomRow(1, null, 20, n("0.5"))), List.of(10));
        assertThat(model.getSewnBom()).hasSize(2);

        model.removeComponentFromSewnBom(10);
        assertThat(model.getSewnBom()).singleElement().extracting(SewnBomLine::getItemId).isEqualTo(20);
    }

    @Test
    void sizeUsedInSewnBomCannotBeRemoved() {
        ProductionModel model = dress();
        model.setSewnBom(List.of(new SewnBomRow(2, null, 20, n("1"))), List.of());
        assertThatThrownBy(() -> model.setSizes(List.of(size("S"))))
                .hasMessage("Розмір \"M\" використовується в специфікації пошиву.");
    }

    @Test
    void setLinksAddsRemovesAndUpdates() {
        ProductionModel model = dress();
        model.setLinks(List.of(100, 101), List.of(new CategoryLinkInput(5, " сукня ", false)));

        model.setLinks(List.of(101, 102), List.of(new CategoryLinkInput(5, "плаття", true), new CategoryLinkInput(6, null, false)));

        assertThat(model.getProductLinks()).extracting(ProductionProductLink::getProductId).containsExactly(101, 102);
        assertThat(model.getCategoryLinks()).extracting(ProductionCategoryLink::getCategoryId).containsExactly(5, 6);
        assertThat(model.getCategoryLinks().get(0).getNameFilter()).isEqualTo("плаття");
        assertThat(model.getCategoryLinks().get(0).isMatchExact()).isTrue();
        assertThat(model.getCategoryLinks().get(1).getNameFilter()).isEmpty();

        assertThatThrownBy(() -> model.setLinks(List.of(), List.of(new CategoryLinkInput(5, null, false), new CategoryLinkInput(5, "x", true))))
                .hasMessage("Категорію 5 указано двічі.");
        assertThatThrownBy(() -> model.setLinks(List.of(0), List.of()))
                .hasMessage("Id товарів і категорій мають бути додатними.");
    }

    @Test
    void setSizeOptions() {
        ProductionModel model = dress();
        model.setSizeOptions(List.of(new ProductionModel.SizeOption(1, 55)));
        assertThat(model.getSizes().get(0).getOptionValueId()).isEqualTo(55);
    }
}
