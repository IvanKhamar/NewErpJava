package ua.newerp.web.orders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.data.value.ValueChangeMode;

import ua.newerp.application.orders.NewOrderLineRequest;
import ua.newerp.application.orders.NewOrderService;
import ua.newerp.application.orders.OrderCompositionDto;
import ua.newerp.application.orders.OrderCompositionLine;
import ua.newerp.application.orders.ProductOptionValueDto;
import ua.newerp.application.orders.ProductPickDto;
import ua.newerp.application.orders.UpdateOrderCompositionRequest;
import ua.newerp.application.orders.UpdateOrderCompositionResult;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Search;

/**
 * A new composition of an order: products, sizes, quantities, prices and sums (EditCompositionDialog.razor).
 * The new composition fully replaces the old one; the card gets the result through onSaved.
 */
public class EditCompositionDialog extends Dialog {

    final ComboBox<ProductPickDto> product = new ComboBox<>("Додати товар");
    final Button add = new Button("Додати");
    final List<Line> lines = new ArrayList<>();
    final BigDecimalField shippingCost = money("Доставка, грн");
    final BigDecimalField discount = money("Знижка, грн");
    final BigDecimalField prepayment = money("Завдаток, грн");
    final H4 total = new H4();
    final Button save = new Button("Зберегти");

    private final Div lineRows = new Div();
    private final NewOrderService service;
    private final int orderId;
    private final int userId;
    private final Consumer<UpdateOrderCompositionResult> onSaved;

    public EditCompositionDialog(NewOrderService service, int orderId, int userId, Consumer<UpdateOrderCompositionResult> onSaved) {
        this.service = service;
        this.orderId = orderId;
        this.userId = userId;
        this.onSaved = onSaved;
        OrderCompositionDto composition = service.getComposition(orderId);
        setHeaderTitle("Склад замовлення");
        setWidth("min(64rem, 100%)");

        Search.bind(product, 2, service::searchProducts);
        product.setItemLabelGenerator(p -> p.name() + " (" + p.model() + ")");
        product.setPlaceholder("Назва, модель чи №");
        product.setWidth("28rem");
        add.setEnabled(false);
        product.addValueChangeListener(e -> add.setEnabled(e.getValue() != null));
        add.addClickListener(e -> addProduct());
        var search = new HorizontalLayout(product, add);
        search.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        // A line's price is the one it was sold at; the base price is needed only when the manager changes the size.
        for (OrderCompositionLine line : composition.lines()) {
            addLine(new Line(line.productId(), line.name(), line.model(), line.price(), line.options(), line.productOptionValueId(),
                    line.quantity(), line.price()));
        }
        shippingCost.setValue(Fmt.plain(composition.shippingCost()));
        discount.setValue(Fmt.plain(composition.discount()));
        prepayment.setValue(Fmt.plain(composition.prepayment()));
        for (BigDecimalField field : List.of(shippingCost, discount, prepayment)) {
            field.addValueChangeListener(e -> updateTotal());
        }
        var sums = new HorizontalLayout(shippingCost, discount, prepayment);
        var hint = new Paragraph("Пошиті вироби перерахуються за новим складом, якщо статус замовлення їх списує.");
        hint.addClassName("secondary");
        add(search, lineRows, sums, total, hint);
        updateTotal();

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
        getFooter().add(new Button("Скасувати", e -> close()), save);
    }

    void addProduct() {
        ProductPickDto picked = product.getValue();
        if (picked == null) {
            return;
        }
        var line = new Line(picked.id(), picked.name(), picked.model(), picked.price(), picked.options(), null, 1, picked.price());
        line.chooseOption(picked.options().isEmpty() ? null : picked.options().getFirst());
        addLine(line);
        product.clear();
        updateTotal();
    }

    void save() {
        var request = new UpdateOrderCompositionRequest(lines.stream().map(Line::toRequest).toList(), orZero(shippingCost.getValue()),
                orZero(discount.getValue()), orZero(prepayment.getValue()));
        Actions.run(null, () -> {
            UpdateOrderCompositionResult result = service.updateComposition(orderId, request, userId);
            close();
            onSaved.accept(result);
        });
    }

    BigDecimal total() {
        BigDecimal sum = lines.stream().map(Line::sum).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.add(orZero(shippingCost.getValue())).subtract(orZero(discount.getValue()));
    }

    private void addLine(Line line) {
        lines.add(line);
        lineRows.add(line.layout);
    }

    private void updateTotal() {
        total.setText("Разом: " + Fmt.money(total()));
    }

    private static BigDecimalField money(String label) {
        var field = new BigDecimalField(label);
        field.setValueChangeMode(ValueChangeMode.EAGER);
        return field;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** A line being edited: the product with its sizes in the catalog, the chosen size, quantity and price. */
    final class Line {
        final int productId;
        final BigDecimal basePrice;
        final Select<ProductOptionValueDto> option = new Select<>();
        final IntegerField quantity = new IntegerField();
        final BigDecimalField price = money(null);
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
            this.quantity.addValueChangeListener(e -> changed());
            this.price.addValueChangeListener(e -> changed());
            sum.setWidth("7rem");
            var remove = new Button(VaadinIcon.TRASH.create(), e -> {
                lines.remove(this);
                lineRows.remove(layout);
                updateTotal();
            });
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            remove.setAriaLabel("Прибрати товар");
            layout.add(title, option, this.quantity, this.price, sum, remove);
            layout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
            sum.setText(Fmt.money(sum()));
        }

        // As in a new order: the option's price, when above 0, replaces the product's price.
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
            updateTotal();
        }
    }
}
