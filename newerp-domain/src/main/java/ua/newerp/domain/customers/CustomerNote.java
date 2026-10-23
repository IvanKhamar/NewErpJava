package ua.newerp.domain.customers;

import ua.newerp.domain.common.DomainException;

/** A manager's note about a customer (dd_customer_history, the "Історія" tab of the old site's customer card). */
public final class CustomerNote {

    public static final int MAX_LENGTH = 2000;

    private CustomerNote() {
    }

    public static String validate(String comment) {
        String text = comment == null ? "" : comment.strip();
        if (text.isEmpty()) {
            throw new DomainException("Напишіть текст нотатки.");
        }
        if (text.length() > MAX_LENGTH) {
            throw new DomainException("Нотатка довша за " + MAX_LENGTH + " символів.");
        }
        return text;
    }
}
