package ua.newerp.web.admin;

import java.util.Objects;
import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;

import ua.newerp.application.auth.Roles;

/** Picks a role (RoleDialog.razor); an admin cannot take the admin role off themselves. */
class RoleDialog extends Dialog {

    final RadioButtonGroup<String> role = new RadioButtonGroup<>();
    final Button save = new Button("Зберегти");

    /** current: null when the user has no access yet. */
    RoleDialog(String title, String current, boolean isSelf, Consumer<String> onSave) {
        setHeaderTitle(title);
        setWidth("min(28rem, 100%)");
        role.setItems(Roles.ALL);
        role.setItemLabelGenerator(AdminUi::roleLabel);
        role.setItemEnabledProvider(r -> !isSelf || Roles.ADMIN.equals(r));
        role.setValue(current == null ? Roles.MANAGER : current);
        add(role);
        if (isSelf) {
            add(caption("Зняти роль адміністратора із себе не можна, щоб система не лишилася без адміністратора."));
        } else if (current == null) {
            add(caption("Система згенерує тимчасовий пароль і покаже його один раз."));
        }

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.setEnabled(!Objects.equals(role.getValue(), current));
        role.addValueChangeListener(e -> save.setEnabled(!Objects.equals(e.getValue(), current)));
        save.addClickListener(e -> {
            close();
            onSave.accept(role.getValue());
        });
        getFooter().add(new Button("Скасувати", e -> close()), save);
    }

    private static Paragraph caption(String text) {
        var paragraph = new Paragraph(text);
        paragraph.addClassName("secondary");
        return paragraph;
    }
}
