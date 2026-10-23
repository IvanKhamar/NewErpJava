package ua.newerp.web.stock;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.vaadin.flow.component.select.Select;

import ua.newerp.application.common.LookupItem;
import ua.newerp.application.locations.LocationDto;
import ua.newerp.application.locations.LocationService;
import ua.newerp.application.stock.StockLocationService;
import ua.newerp.web.Fmt;

/** The choice of a place for stock: the main stock, then the warehouses by name. Hidden while there are no warehouses. */
public final class Places {

    /** The main stock; a LookupItem needs an int id, so it is told apart by reference. */
    public static final LookupItem MAIN = new LookupItem(0, StockLocationService.MAIN_NAME);

    private Places() {
    }

    public static Select<LookupItem> select(String label, LocationService warehouses) {
        return select(label, warehouses.list(null));
    }

    public static Select<LookupItem> select(String label, List<LocationDto> warehouses) {
        List<LookupItem> places = new ArrayList<>();
        places.add(MAIN);
        warehouses.stream()
                .sorted(Comparator.comparing(LocationDto::name, Collator.getInstance(Fmt.UK)))
                .forEach(w -> places.add(new LookupItem(w.id(), w.name())));
        var select = new Select<LookupItem>();
        select.setLabel(label);
        select.setItems(places);
        select.setItemLabelGenerator(LookupItem::name);
        select.setValue(MAIN);
        select.setVisible(places.size() > 1);
        return select;
    }

    /** null for the main stock. */
    public static Integer warehouseId(Select<LookupItem> select) {
        return select.getValue() == null || select.getValue() == MAIN ? null : select.getValue().id();
    }
}
