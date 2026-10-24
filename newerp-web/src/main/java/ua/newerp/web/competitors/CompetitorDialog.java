package ua.newerp.web.competitors;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextField;

import ua.newerp.application.competitors.CompetitorDto;
import ua.newerp.application.competitors.CompetitorService;
import ua.newerp.application.competitors.SaveCompetitorRequest;
import ua.newerp.web.ui.Actions;

/** A new competitor or changes to one; «Стежити» off stops the checks and hides it from the comparison. */
class CompetitorDialog extends Dialog {

    final TextField name = new TextField("Назва");
    final TextField site = new TextField("Сайт");
    final TextField regex = new TextField("Вираз для ціни");
    final Checkbox active = new Checkbox("Стежити");
    final Button save = new Button("Зберегти");

    private final CompetitorService service;
    private final CompetitorDto competitor;
    private final Runnable onSaved;

    CompetitorDialog(CompetitorService service, CompetitorDto competitor, Runnable onSaved) {
        this.service = service;
        this.competitor = competitor;
        this.onSaved = onSaved;
        setHeaderTitle(competitor == null ? "Новий конкурент" : competitor.name());
        setWidth("min(36rem, 100%)");

        name.setMaxLength(100);
        site.setMaxLength(255);
        site.setPlaceholder("https://example.com.ua");
        site.setHelperText("Сторінки товарів мають бути на цьому сайті");
        regex.setMaxLength(255);
        regex.setHelperText("Необов'язково. Коли ціну не знайдено за розміткою сторінки: перша група виразу — ціна");
        if (competitor == null) {
            active.setValue(true);
        } else {
            name.setValue(competitor.name());
            site.setValue(competitor.siteUrl());
            regex.setValue(competitor.priceRegex());
            active.setValue(competitor.active());
        }
        var form = new FormLayout(name, site, regex, active);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        add(form);

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
        getFooter().add(new Button("Скасувати", e -> close()), save);
    }

    void save() {
        var request = new SaveCompetitorRequest(name.getValue(), site.getValue(), regex.getValue(), active.getValue());
        boolean saved = Actions.run("Збережено.", () -> {
            if (competitor == null) {
                service.createCompetitor(request);
            } else {
                service.updateCompetitor(competitor.id(), request);
            }
        });
        if (saved) {
            close();
            onSaved.run();
        }
    }
}
