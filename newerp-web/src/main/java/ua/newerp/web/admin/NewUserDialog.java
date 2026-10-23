package ua.newerp.web.admin;

import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;

import ua.newerp.application.auth.CreateUserRequest;
import ua.newerp.application.auth.Roles;
import ua.newerp.application.auth.UserAccessResult;
import ua.newerp.application.auth.UserAdminService;
import ua.newerp.web.ui.Actions;

/** A new employee (NewUserDialog.razor): a dd_user row of the old site (without the old admin login) and NewErp access. */
class NewUserDialog extends Dialog {

    final TextField firstName = new TextField("Ім'я");
    final TextField lastName = new TextField("Прізвище");
    final TextField username = new TextField("Логін");
    final Select<String> role = new Select<>();
    final EmailField email = new EmailField("Email (необов'язково)");
    final Button create = new Button("Створити");

    NewUserDialog(UserAdminService service, Consumer<UserAccessResult> onCreated) {
        setHeaderTitle("Новий працівник");
        setWidth("min(34rem, 100%)");
        firstName.setMaxLength(32);
        lastName.setMaxLength(32);
        username.setMaxLength(20);
        username.setHelperText("3–20 символів");
        email.setMaxLength(96);
        role.setLabel("Роль");
        role.setItems(Roles.ALL);
        role.setItemLabelGenerator(AdminUi::roleLabel);
        role.setValue(Roles.MANAGER);
        var form = new FormLayout(firstName, lastName, username, role, email);
        form.setColspan(email, 2);
        var note = new Paragraph("Тимчасовий пароль система згенерує сама й покаже один раз.");
        note.addClassName("secondary");
        add(form, note);

        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.addClickListener(e -> Actions.run(null, () -> {
            var result = service.create(new CreateUserRequest(username.getValue(), firstName.getValue(), lastName.getValue(),
                    email.getValue(), role.getValue(), null));
            close();
            onCreated.accept(result);
        }));
        getFooter().add(new Button("Скасувати", e -> close()), create);
        firstName.focus();
    }
}
