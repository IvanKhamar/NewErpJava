package ua.newerp.web.marketing;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.marketing.CouponDto;
import ua.newerp.application.marketing.CouponService;
import ua.newerp.domain.marketing.Coupon;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;

/** Discount coupons of the shop: what each one gave in placed orders, creating, changing and turning off. */
@Route("marketing/coupons")
@PageTitle("Купони — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class CouponsView extends VerticalLayout {

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    /** The list states with their labels and badge tones; null shows all. */
    static final Map<String, String> STATES = Map.of(Coupon.ACTIVE, "Діє", Coupon.DISABLED, "Вимкнено", Coupon.NOT_STARTED, "Ще не почався",
            Coupon.EXPIRED, "Закінчився", Coupon.USED_UP, "Використано");
    private static final Map<String, String> TONES = Map.of(Coupon.ACTIVE, "tone-1", Coupon.NOT_STARTED, "tone-3", Coupon.USED_UP, "tone-2",
            Coupon.EXPIRED, "tone-2");

    final Select<String> state = new Select<>();
    final Grid<CouponDto> grid = new Grid<>();
    final Button add = new Button("Новий купон", VaadinIcon.PLUS.create());
    private final CouponService service;
    List<CouponDto> coupons = List.of();

    public CouponsView(CouponService service) {
        this.service = service;
        var hint = new Paragraph("Використано — оформлені замовлення з цим купоном; знижка — скільки вони разом зняли. Купони не видаляються: "
                + "непотрібний вимкніть, і замовлення на ньому лишаться.");
        hint.addClassName("secondary");

        state.setLabel("Стан");
        state.setItems(Coupon.ACTIVE, Coupon.NOT_STARTED, Coupon.USED_UP, Coupon.EXPIRED, Coupon.DISABLED);
        state.setItemLabelGenerator(s -> s == null ? "Усі" : STATES.get(s));
        state.setEmptySelectionAllowed(true);
        state.setEmptySelectionCaption("Усі");
        state.addValueChangeListener(e -> load());
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.addClickListener(e -> new CouponDialog(service, null, this::load).open());
        var toolbar = new HorizontalLayout(state, add);
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        grid.addColumn(CouponDto::code).setHeader("Код").setAutoWidth(true);
        grid.addColumn(CouponDto::name).setHeader("Назва").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(CouponsView::discount).setHeader("Знижка").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(c -> c.minTotal().signum() == 0 ? "" : "від " + Fmt.money(c.minTotal())).setHeader("Замовлення")
                .setAutoWidth(true);
        grid.addColumn(c -> day(c.start()) + " – " + day(c.end())).setHeader("Діє").setAutoWidth(true);
        grid.addColumn(c -> c.used() + (c.usesTotal() > 0 ? " з " + c.usesTotal() : "")).setHeader("Використано")
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(c -> c.discountGiven().signum() == 0 ? "" : Fmt.money(c.discountGiven())).setHeader("Знижка разом")
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addComponentColumn(c -> {
            var badge = new Span(STATES.getOrDefault(c.state(), c.state()));
            badge.addClassName("status-badge");
            if (TONES.containsKey(c.state())) {
                badge.addClassName(TONES.get(c.state()));
            }
            return badge;
        }).setHeader("Стан").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setEmptyStateText("Купонів немає.");
        grid.addItemClickListener(e -> new CouponDialog(service, e.getItem(), this::load).open());
        grid.setAllRowsVisible(true);

        add(new H2("Купони"), hint, toolbar, grid);
        load();
    }

    void load() {
        coupons = service.list(state.getValue());
        grid.setItems(coupons);
    }

    static String discount(CouponDto c) {
        String value = Coupon.PERCENT.equals(c.type()) ? Fmt.qty(c.discount()) + "%" : Fmt.money(c.discount());
        if (c.discount().signum() == 0) {
            value = "";
        }
        return c.freeShipping() ? (value.isEmpty() ? "" : value + " + ") + "доставка" : value;
    }

    private static String day(LocalDate value) {
        return value == null ? "—" : DAY.format(value);
    }
}
