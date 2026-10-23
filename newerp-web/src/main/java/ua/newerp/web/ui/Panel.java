package ua.newerp.web.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;

/** A framed block with a heading (MudPaper Outlined in the Blazor UI). */
public class Panel extends Div {

    public Panel(String title, Component... content) {
        addClassName("panel");
        add(new H4(title));
        add(content);
    }
}
