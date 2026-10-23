package ua.newerp.web.production;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.production.ProductionLogFilter;
import ua.newerp.application.production.ProductionRepository;
import ua.newerp.web.Access;

/** The last 200 cutting and sewing records of all models (ProductionLog.razor). */
@Route("production/log")
@PageTitle("Журнал крою й пошиву — NewErp")
@RolesAllowed({Access.ADMIN, Access.STOREKEEPER})
public class ProductionLogView extends VerticalLayout {

    public ProductionLogView(ProductionRepository repository) {
        setSizeFull();
        var hint = new Paragraph("Останні 200 записів. Записи «Замовлення #…» і «Скасування #…» робить система, коли змінюється статус замовлення.");
        hint.addClassName("secondary");
        var grid = new ProductionLogGrid(repository.listLog(new ProductionLogFilter(null, null, 200)), true);
        grid.setSizeFull();
        add(new H2("Журнал крою й пошиву"), hint, grid);
        setFlexGrow(1, grid);
    }
}
