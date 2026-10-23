package ua.newerp.web.orders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.IntConsumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;

import ua.newerp.application.orders.OrderProductLine;
import ua.newerp.application.returns.CreateReturnRequest;
import ua.newerp.application.returns.ReturnLookupItem;
import ua.newerp.application.returns.ReturnLookupsDto;
import ua.newerp.application.returns.ReturnService;
import ua.newerp.web.ui.Actions;

/**
 * A product return from an order (CreateReturnDialog.razor): the customer comes from the order, here only the product,
 * quantity, reason and status. The new return's id goes to onCreated.
 */
public class CreateReturnDialog extends Dialog {

    final Select<OrderProductLine> product = new Select<>();
    final IntegerField quantity = new IntegerField("Кількість");
    final Checkbox opened = new Checkbox("Розпакований");
    final Select<ReturnLookupItem> reason = new Select<>();
    final Select<ReturnLookupItem> status = new Select<>();
    final TextArea comment = new TextArea("Коментар");
    final Button save = new Button("Оформити");

    private final ReturnService service;
    private final int orderId;
    private final IntConsumer onCreated;

    public CreateReturnDialog(ReturnService service, int orderId, List<OrderProductLine> products, IntConsumer onCreated) {
        this.service = service;
        this.orderId = orderId;
        this.onCreated = onCreated;
        ReturnLookupsDto lookups = service.lookups();
        setHeaderTitle("Повернення товару");
        setWidth("min(32rem, 100%)");

        // One line per product: the same product in two sizes is still one product to return.
        var distinct = new LinkedHashMap<Integer, OrderProductLine>();
        products.forEach(p -> distinct.putIfAbsent(p.productId(), p));
        product.setLabel("Товар");
        product.setItems(distinct.values());
        product.setItemLabelGenerator(p -> p.name() + " (" + p.model() + ")");
        product.setValue(distinct.isEmpty() ? null : distinct.firstEntry().getValue());
        product.setWidthFull();

        quantity.setMin(1);
        quantity.setValue(1);
        quantity.setStepButtonsVisible(true);
        quantity.setWidth("8rem");
        var amount = new HorizontalLayout(quantity, opened);
        amount.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        reason.setLabel("Причина");
        setUp(reason, lookups.reasons());
        status.setLabel("Статус");
        setUp(status, lookups.statuses());
        comment.setWidthFull();

        var form = new VerticalLayout(product, amount, reason, status, comment);
        form.setPadding(false);
        form.setSpacing(false);
        add(form);

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
        getFooter().add(new Button("Скасувати", e -> close()), save);
    }

    void save() {
        var request = new CreateReturnRequest(orderId, product.getValue() == null ? 0 : product.getValue().productId(),
                quantity.getValue() == null ? 0 : quantity.getValue(), id(reason.getValue()), id(status.getValue()), 0, opened.getValue(),
                comment.getValue());
        Actions.run(null, () -> {
            int id = service.create(request);
            close();
            onCreated.accept(id);
        });
    }

    private static void setUp(Select<ReturnLookupItem> select, List<ReturnLookupItem> items) {
        select.setItems(items);
        select.setItemLabelGenerator(ReturnLookupItem::name);
        select.setValue(items.isEmpty() ? null : items.getFirst());
        select.setWidthFull();
    }

    private static int id(ReturnLookupItem item) {
        return item == null ? 0 : item.id();
    }
}
