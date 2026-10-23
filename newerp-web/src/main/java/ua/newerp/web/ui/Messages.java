package ua.newerp.web.ui;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/** Short pop-up messages at the bottom of the page (the Snackbar of the Blazor UI). */
public final class Messages {

    private Messages() {
    }

    public static void success(String text) {
        show(text, NotificationVariant.LUMO_SUCCESS);
    }

    public static void error(String text) {
        show(text, NotificationVariant.LUMO_ERROR);
    }

    private static void show(String text, NotificationVariant variant) {
        Notification.show(text, 5000, Notification.Position.BOTTOM_START).addThemeVariants(variant);
    }
}
