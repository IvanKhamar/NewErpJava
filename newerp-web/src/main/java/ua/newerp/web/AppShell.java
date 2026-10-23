package ua.newerp.web;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * Settings of the HTML page all views live in (like index.html + App.razor in Blazor): the Lumo theme and its utility classes.
 * Vaadin views are Java classes on the server; the browser only draws the components and sends events back.
 */
@StyleSheet(Lumo.STYLESHEET)
@StyleSheet(Lumo.UTILITY_STYLESHEET)
@StyleSheet("context://styles/newerp.css")
public class AppShell implements AppShellConfigurator {

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.setPageTitle("NewErp");
    }
}
