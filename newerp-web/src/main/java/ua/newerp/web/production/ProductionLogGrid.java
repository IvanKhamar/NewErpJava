package ua.newerp.web.production;

import java.util.List;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.theme.lumo.LumoUtility;

import ua.newerp.application.production.ProductionLogDto;
import ua.newerp.domain.production.ProductionLogEntry;
import ua.newerp.web.Fmt;

/** The cutting and sewing log (ProductionLogTable.razor): of all models, or of one without the model column. */
class ProductionLogGrid extends Grid<ProductionLogDto> {

    ProductionLogGrid(List<ProductionLogDto> entries, boolean withModel) {
        addColumn(e -> Fmt.date(e.createdAt())).setHeader("Коли").setAutoWidth(true);
        if (withModel) {
            addComponentColumn(e -> new Anchor(ProductionUi.model(e.productionId()), e.productionName())).setHeader("Модель")
                    .setAutoWidth(true);
        }
        addColumn(ProductionLogDto::sizeName).setHeader("Розмір").setAutoWidth(true);
        addColumn(ProductionUi::logLabel).setHeader("Що").setAutoWidth(true);
        addComponentColumn(e -> {
            boolean in = ProductionLogEntry.IN.equals(e.direction());
            var quantity = new Span((in ? "+" : "−") + Fmt.qty(e.quantity()));
            quantity.addClassName(in ? LumoUtility.TextColor.SUCCESS : LumoUtility.TextColor.ERROR);
            return quantity;
        }).setHeader("Кількість").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        addColumn(e -> Fmt.qty(e.stockAfter())).setHeader("Стало").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        addColumn(ProductionLogDto::comment).setHeader("Коментар").setFlexGrow(1);
        addColumn(ProductionLogDto::username).setHeader("Хто").setAutoWidth(true);
        addThemeVariants(GridVariant.LUMO_COMPACT);
        setEmptyStateText("Записів ще немає.");
        setItems(entries);
    }
}
