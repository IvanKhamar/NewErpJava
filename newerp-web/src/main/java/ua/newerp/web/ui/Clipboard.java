package ua.newerp.web.ui;

import com.vaadin.flow.component.UI;

/** Copies text to the user's clipboard in the browser (the server cannot reach it directly). */
public final class Clipboard {

    private Clipboard() {
    }

    public static void copy(String text, String message) {
        UI.getCurrent().getPage().executeJs("navigator.clipboard.writeText($0)", text);
        Messages.success(message);
    }
}
