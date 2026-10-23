package ua.newerp.web.ui;

import java.util.List;
import java.util.function.Function;

import com.vaadin.flow.component.combobox.ComboBox;

/** Autocomplete on a ComboBox: what the user types goes to a search service, starting from a minimum length. */
public final class Search {

    private Search() {
    }

    public static <T> void bind(ComboBox<T> combo, int minLength, Function<String, List<T>> search) {
        // The fetch callback must use the query's offset and limit; the services return one short page anyway.
        combo.setItems(query -> {
            String text = query.getFilter().orElse("").strip();
            List<T> found = text.length() < minLength ? List.of() : search.apply(text);
            return found.stream().skip(query.getOffset()).limit(query.getLimit());
        });
    }
}
