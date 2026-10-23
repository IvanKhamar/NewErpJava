package ua.newerp.web.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.theme.lumo.LumoUtility;

import ua.newerp.web.ui.Clipboard;

/** Shows a temporary password once (TemporaryPasswordDialog.razor): the server keeps only its hash, so it cannot be seen later. */
class TemporaryPasswordDialog extends Dialog {

    final Span password;

    TemporaryPasswordDialog(String username, String temporaryPassword) {
        setHeaderTitle("Тимчасовий пароль");
        setWidth("min(30rem, 100%)");
        password = new Span(temporaryPassword);
        password.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.FontWeight.SEMIBOLD);
        password.getStyle().set("font-family", "monospace");
        var copy = new Button(VaadinIcon.COPY.create(), e -> Clipboard.copy(temporaryPassword, "Пароль скопійовано."));
        copy.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        copy.setAriaLabel("Скопіювати пароль");
        var login = new Span("Логін: " + username);
        login.addClassName(LumoUtility.Margin.End.AUTO);
        var box = new HorizontalLayout(login, password, copy);
        box.setWidthFull();
        box.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        box.addClassName("panel");
        var warning = new Paragraph("Цей пароль показується лише зараз.");
        warning.addClassNames("note", "tone-2");
        add(new Paragraph("Передайте людині логін і пароль. Після входу система попросить задати власний пароль."), new Div(box), warning);
        var done = new Button("Готово", e -> close());
        done.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(done);
    }
}
