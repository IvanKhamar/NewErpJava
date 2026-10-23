package ua.newerp.web.finance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.data.value.ValueChangeMode;

import ua.newerp.application.cashbox.CashboxService;
import ua.newerp.application.cashbox.CashboxSetupDto;
import ua.newerp.application.cashbox.CashierDto;
import ua.newerp.application.cashbox.DirectoryEntryDto;
import ua.newerp.application.cashbox.SaveCashierRequest;
import ua.newerp.web.ui.Actions;

/** A new cashier or changes to one (CashierDialog.razor). The Checkbox keys are not entered here: they live only in the server's secrets. */
public class CashierDialog extends Dialog {

    /** "Not set" in the warehouse and requisites selects; id 0 like in the old tables. */
    static final DirectoryEntryDto NONE = new DirectoryEntryDto(0, "не вказано", "");

    final TextField name = new TextField("Ім'я (друкується в чеку)");
    final TextField description = new TextField("Опис");
    final TextField paymentCode = new TextField("Код оплати замовлень (cod, liqpay...); порожньо — касир за замовчуванням");
    final TimePicker autoClose = new TimePicker("Автоматично закривати зміну о");
    final Select<DirectoryEntryDto> warehouse = new Select<>();
    final Select<DirectoryEntryDto> requisites = new Select<>();
    final Checkbox active = new Checkbox("Активний", true);
    final Button save = new Button("Зберегти");

    private final CashboxService service;
    private final CashierDto cashier;
    private final Runnable onSaved;

    /** cashier: null for a new one. */
    public CashierDialog(CashboxService service, CashboxSetupDto setup, CashierDto cashier, Runnable onSaved) {
        this.service = service;
        this.cashier = cashier;
        this.onSaved = onSaved;
        setHeaderTitle(cashier == null ? "Новий касир" : "Касир «" + cashier.name() + "»");
        setWidth("min(32rem, 100%)");

        name.setMaxLength(255);
        name.setValueChangeMode(ValueChangeMode.EAGER); // Save turns on while typing
        paymentCode.setMaxLength(128);
        autoClose.setClearButtonVisible(true);
        setUp(warehouse, "Склад (точка продажу)", setup.warehouses());
        setUp(requisites, "Реквізити (ФОП)", setup.requisites());
        if (cashier != null) {
            name.setValue(cashier.name());
            description.setValue(Objects.toString(cashier.description(), ""));
            paymentCode.setValue(Objects.toString(cashier.paymentCode(), ""));
            autoClose.setValue(cashier.autoCloseTime());
            warehouse.setValue(find(setup.warehouses(), cashier.warehouseId()));
            requisites.setValue(find(setup.requisites(), cashier.requisitesId()));
            active.setValue(cashier.isActive());
        }
        var form = new FormLayout(name, description, paymentCode, autoClose, warehouse, requisites, active);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        add(form);

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.setEnabled(!name.getValue().isBlank());
        name.addValueChangeListener(e -> save.setEnabled(!e.getValue().isBlank()));
        save.addClickListener(e -> save());
        getFooter().add(new Button("Скасувати", e -> close()), save);
    }

    void save() {
        var request = new SaveCashierRequest(name.getValue(), description.getValue(), paymentCode.getValue(), autoClose.getValue(),
                warehouse.getValue().id(), requisites.getValue().id(), active.getValue());
        Actions.run("Касира збережено.", () -> {
            if (cashier == null) {
                service.createCashier(request);
            } else {
                service.updateCashier(cashier.id(), request);
            }
            close();
            onSaved.run();
        });
    }

    private static void setUp(Select<DirectoryEntryDto> select, String label, List<DirectoryEntryDto> entries) {
        var items = new ArrayList<DirectoryEntryDto>();
        items.add(NONE);
        items.addAll(entries);
        select.setLabel(label);
        select.setItems(items);
        select.setItemLabelGenerator(DirectoryEntryDto::name);
        select.setValue(NONE);
    }

    private static DirectoryEntryDto find(List<DirectoryEntryDto> entries, int id) {
        return entries.stream().filter(e -> e.id() == id).findFirst().orElse(NONE);
    }
}
