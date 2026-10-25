package ua.newerp.web.marketing;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.marketing.VoucherCardDto;
import ua.newerp.application.marketing.VoucherService;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.StatusBadge;

/** Gift vouchers as the old site's "Подарункові сертифікати": the list with balances; a click opens the voucher. */
@Route("marketing/vouchers")
@PageTitle("Подарункові сертифікати — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class VouchersView extends VerticalLayout {

    final TextField search = new TextField();
    final Button add = new Button("Новий сертифікат", VaadinIcon.PLUS.create());
    final Grid<VoucherCardDto> grid = new Grid<>();
    private final VoucherService service;

    public VouchersView(VoucherService service) {
        this.service = service;
        search.setPlaceholder("Код або ім'я");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(e -> load());
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.addClickListener(e -> new VoucherDialog(service, null, this::load).open());
        var header = new HorizontalLayout(new H2("Подарункові сертифікати"), add);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        grid.addColumn(VoucherCardDto::code).setHeader("Код").setAutoWidth(true);
        grid.addColumn(VoucherCardDto::toName).setHeader("Кому").setFlexGrow(1);
        grid.addColumn(VoucherCardDto::fromName).setHeader("Від кого").setAutoWidth(true);
        grid.addColumn(v -> Fmt.money(v.amount())).setHeader("Сума").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(v -> Fmt.money(v.balance())).setHeader("Залишок").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addComponentColumn(VouchersView::state).setHeader("Стан").setAutoWidth(true);
        grid.addColumn(v -> Fmt.date(v.createdAt())).setHeader("Створено").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setEmptyStateText("Сертифікатів немає.");
        grid.addItemClickListener(e -> new VoucherDialog(service, e.getItem().id(), this::load).open());
        grid.setAllRowsVisible(true);

        add(header, search, grid);
        load();
    }

    void load() {
        grid.setItems(service.list(search.getValue()));
    }

    static StatusBadge state(VoucherCardDto v) {
        if (!v.active()) {
            return new StatusBadge("вимкнено", "#9e9e9e");
        }
        if (!v.usable()) {
            return new StatusBadge("не оплачено", "#ff9800");
        }
        return v.balance().signum() > 0 ? new StatusBadge("діє", "#4caf50") : new StatusBadge("витрачено", "#9e9e9e");
    }
}
