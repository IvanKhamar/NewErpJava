package ua.newerp.web.marketing;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;

import ua.newerp.application.marketing.CouponDto;
import ua.newerp.application.marketing.CouponService;
import ua.newerp.application.marketing.SaveCouponRequest;
import ua.newerp.domain.marketing.Coupon;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;

/** A new coupon or changes to one; turning it off is the «Діє» checkbox. */
class CouponDialog extends Dialog {

    final TextField name = new TextField("Назва");
    final TextField code = new TextField("Код");
    final RadioButtonGroup<String> type = new RadioButtonGroup<>("Знижка");
    final BigDecimalField discount = new BigDecimalField("Розмір знижки");
    final BigDecimalField minTotal = new BigDecimalField("Від суми замовлення, ₴");
    final DatePicker start = new DatePicker("Діє з");
    final DatePicker end = new DatePicker("Діє до");
    final IntegerField usesTotal = new IntegerField("Скільки разів усього");
    final IntegerField usesPerCustomer = new IntegerField("Скільки разів одному покупцю");
    final Checkbox freeShipping = new Checkbox("Безкоштовна доставка");
    final Checkbox loggedOnly = new Checkbox("Лише для зареєстрованих");
    final Checkbox active = new Checkbox("Діє");
    final Button save = new Button("Зберегти");

    private final CouponService service;
    private final CouponDto coupon;
    private final Runnable onSaved;

    CouponDialog(CouponService service, CouponDto coupon, Runnable onSaved) {
        this.service = service;
        this.coupon = coupon;
        this.onSaved = onSaved;
        setHeaderTitle(coupon == null ? "Новий купон" : "Купон " + coupon.code());
        setWidth("min(40rem, 100%)");

        name.setMaxLength(128);
        code.setMaxLength(20);
        code.setHelperText("4–20 латинських літер і цифр");
        type.setItems(Coupon.PERCENT, Coupon.FIXED);
        type.setItemLabelGenerator(t -> Coupon.PERCENT.equals(t) ? "відсоток" : "сума, ₴");
        discount.setLocale(Fmt.UK);
        minTotal.setLocale(Fmt.UK);
        start.setLocale(Fmt.UK);
        end.setLocale(Fmt.UK);
        usesTotal.setMin(0);
        usesPerCustomer.setMin(0);
        usesTotal.setHelperText("0 — без обмеження");
        usesPerCustomer.setHelperText("0 — без обмеження");

        if (coupon == null) {
            LocalDate today = LocalDate.now();
            type.setValue(Coupon.PERCENT);
            start.setValue(today);
            end.setValue(today.plusMonths(1));
            usesTotal.setValue(0);
            usesPerCustomer.setValue(1);
            active.setValue(true);
        } else {
            name.setValue(coupon.name());
            code.setValue(coupon.code());
            type.setValue(coupon.type());
            discount.setValue(Fmt.plain(coupon.discount()));
            minTotal.setValue(coupon.minTotal().signum() == 0 ? null : Fmt.plain(coupon.minTotal()));
            start.setValue(coupon.start());
            end.setValue(coupon.end());
            usesTotal.setValue(coupon.usesTotal());
            usesPerCustomer.setValue(coupon.usesPerCustomer());
            freeShipping.setValue(coupon.freeShipping());
            loggedOnly.setValue(coupon.loggedOnly());
            active.setValue(coupon.active());
        }

        var form = new FormLayout(name, code, type, discount, minTotal, start, end, usesTotal, usesPerCustomer, freeShipping, loggedOnly,
                active);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("30rem", 2));
        add(form);
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
        getFooter().add(new Button("Скасувати", e -> close()), save);
    }

    void save() {
        var request = new SaveCouponRequest(name.getValue(), code.getValue(), type.getValue(), orZero(discount.getValue()),
                orZero(minTotal.getValue()), freeShipping.getValue(), loggedOnly.getValue(), start.getValue(), end.getValue(),
                usesTotal.getValue() == null ? 0 : usesTotal.getValue(), usesPerCustomer.getValue() == null ? 0 : usesPerCustomer.getValue(),
                active.getValue());
        Actions.run("Купон збережено.", () -> {
            if (coupon == null) {
                service.create(request);
            } else {
                service.update(coupon.id(), request);
            }
            close();
            onSaved.run();
        });
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
