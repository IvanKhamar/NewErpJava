package ua.newerp.web.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/** Label/value rows (MudSimpleTable of two columns). */
public class InfoTable extends Div {

    public InfoTable() {
        addClassName("info-table");
    }

    public InfoTable row(String label, String value) {
        return row(label, new Span(value == null || value.isBlank() ? "—" : value));
    }

    public InfoTable row(String label, Component value) {
        var name = new Span(label);
        name.addClassName("info-label");
        add(name, value);
        return this;
    }
}
