package ua.newerp.web.orders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.data.value.ValueChangeMode;

import ua.newerp.application.orders.NewOrderLineRequest;
import ua.newerp.application.orders.ProductOptionValueDto;
import ua.newerp.application.orders.ProductPickDto;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Search;

/**
 * The products of an order being made or changed: a product search and the lines with size, quantity and price.
 * Used by the new order page and the composition dialog; onChange runs after each change of a sum.
 */
public class OrderLinesEditor extends Div {

    final ComboBox<ProductPickDto> product = new ComboBox<>("Додати товар");
    final Button add = new Button("Додати");
    final List<Line> lines = new ArrayList<>();

    private final Div rows = new Div();
    private final Runnable onChange;

    public OrderLinesEditor(Function<String, List<ProductPickDto>> search, Runnable onChange) {
        this.onChange = onChange;
        Search.bind(product, 2, search);
        product.setItemLabelGenerator(p -> p.name() + " (" + p.model() + ")");
        product.setPlaceholder("Назва, модель чи №");
        product.setWidth("28rem");
        add.setEnabled(false);
        product.addValueChangeListener(e -> add.setEnabled(e.getValue() != null));
        add.addClickListener(e -> addProduct());
        var searchRow = new HorizontalLayout(product, add);
        searchRow.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        add(searchRow, rows);
    }

    /** A line already in the order: its price is the one it was sold at, basePrice is needed only when the size changes. */
    public void addLine(int productId, String name, String model, BigDecimal basePrice, List<ProductOptionValueDto> options,
            Integer optionValueId, int quantity, BigDecimal price) {
        var line = new Line(productId, name, model, basePrice, options, optionValueId, quantity, price);
        lines.add(line);
        rows.add(line.layout);
    }

    void addProduct() {
        ProductPickDto picked = product.getValue();
        if (picked == null) {
            return;
        }
        var line = new Line(picked.id(), picked.name(), picked.model(), picked.price(), picked.options(), null, 1, picked.price());
        line.chooseOption(picked.options().isEmpty() ? null : picked.options().getFirst());
        lines.add(line);
        rows.add(line.layout);
        product.clear();
        onChange.run();
    }

    public BigDecimal subTotal() {
        return lines.stream().map(Line::sum).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<NewOrderLineRequest> requests() {
        return lines.stream().map(Line::toRequest).toList();
    }

    static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** A line being edited: the product with its sizes in the catalog, the chosen size, quantity and price. */
    final class Line {
        final int productId;
        final BigDecimal basePrice;
        final Select<ProductOptionValueDto> option = new Select<>();
        final IntegerField quantity = new IntegerField();
        final BigDecimalField price = new BigDecimalField();
        final Span sum = new Span();
        final HorizontalLayout layout = new HorizontalLayout();

        Line(int productId, String name, String model, BigDecimal basePrice, List<ProductOptionValueDto> options, Integer optionValueId,
                int quantity, BigDecimal price) {
            this.productId = productId;
            this.basePrice = basePrice;
            var title = new Div(new Span(name));
            var modelText = new Div(new Span(model));
            modelText.addClassName("secondary");
            title.add(modelText);
            title.setWidth("20rem");

            option.setItems(options);
            option.setItemLabelGenerator(o -> o.value() + " (є " + o.quantity() + ")");
            option.setValue(options.stream().filter(o -> optionValueId != null && o.productOptionValueId() == optionValueId)
                    .findFirst().orElse(null));
            if (options.isEmpty()) {
                option.getStyle().set("visibility", "hidden"); // keeps the column in place for the lines below
            }
            option.setWidth("10rem");
            option.addValueChangeListener(e -> {
                if (e.isFromClient()) {
                    chooseOption(e.getValue());
                }
            });
            this.quantity.setMin(1);
            this.quantity.setStepButtonsVisible(true);
            this.quantity.setValue(quantity);
            this.quantity.setWidth("8rem");
            this.quantity.setValueChangeMode(ValueChangeMode.EAGER);
            this.price.setValue(Fmt.plain(price));
            this.price.setWidth("8rem");
            this.price.setValueChangeMode(ValueChangeMode.EAGER);
            this.quantity.addValueChangeListener(e -> changed());
            this.price.addValueChangeListener(e -> changed());
            sum.setWidth("7rem");
            var remove = new Button(VaadinIcon.TRASH.create(), e -> {
                lines.remove(this);
                rows.remove(layout);
                onChange.run();
            });
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            remove.setAriaLabel("Прибрати товар");
            layout.add(title, option, this.quantity, this.price, sum, remove);
            layout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
            sum.setText(Fmt.money(sum()));
        }

        // As on the old site: the option's price, when above 0, replaces the product's price.
        void chooseOption(ProductOptionValueDto value) {
            option.setValue(value);
            price.setValue(Fmt.plain(value != null && value.price().signum() > 0 ? value.price() : basePrice));
        }

        BigDecimal sum() {
            int qty = quantity.getValue() == null ? 0 : quantity.getValue();
            return orZero(price.getValue()).multiply(BigDecimal.valueOf(qty));
        }

        NewOrderLineRequest toRequest() {
            return new NewOrderLineRequest(productId, option.getValue() == null ? null : option.getValue().productOptionValueId(),
                    quantity.getValue() == null ? 0 : quantity.getValue(), orZero(price.getValue()));
        }

        private void changed() {
            sum.setText(Fmt.money(sum()));
            onChange.run();
        }
    }
}
