package ua.newerp.web.ui;

import com.vaadin.flow.component.confirmdialog.ConfirmDialog;

/** A yes/no question before a step that is hard to undo (ShowMessageBoxAsync in the Blazor UI). */
public final class Confirm {

    private Confirm() {
    }

    public static ConfirmDialog ask(String header, String text, String yes, String no, Runnable onYes) {
        var dialog = new ConfirmDialog(header, text, yes, e -> onYes.run());
        dialog.setCancelable(true);
        dialog.setCancelText(no);
        dialog.open();
        return dialog;
    }
}
