package ua.newerp.domain.returns;

import java.util.List;

import ua.newerp.domain.common.DomainException;

/**
 * A product return (dd_return), as "Продажі → Повернення" of the old site: customer, product, quantity, reason,
 * action (refunded, exchange…) and a status with a log. The old module changed neither stock nor money.
 */
public final class ProductReturn {

    public static final int MAX_COMMENT_LENGTH = 2000;

    private ProductReturn() {
    }

    /**
     * The old site's form checks (first and last name up to 32 characters, phone 3–32, a reason is required) plus what it did
     * not check: the product must be in the order, and no more can be returned than was bought minus the returns already made.
     * The e-mail is optional (the old site required it, though phone orders often have none), but up to 96 characters if given.
     */
    public static ReturnableLine validate(String firstname, String lastname, String telephone, String email, List<ReturnableLine> orderLines,
            int productId, int quantity, int reasonId, String comment, int alreadyReturned) {
        int first = firstname.strip().length();
        if (first < 1 || first > 32) {
            throw new DomainException("Ім'я клієнта має містити від 1 до 32 символів.");
        }
        if (lastname.strip().length() > 32) {
            throw new DomainException("Прізвище клієнта має містити до 32 символів.");
        }
        int phone = telephone.strip().length();
        if (phone < 3 || phone > 32) {
            throw new DomainException("Телефон клієнта має містити від 3 до 32 символів.");
        }
        if (email.strip().length() > 96) {
            throw new DomainException("E-mail занадто довгий.");
        }
        if (reasonId <= 0) {
            throw new DomainException("Оберіть причину повернення.");
        }
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new DomainException("Коментар довший за " + MAX_COMMENT_LENGTH + " символів.");
        }

        ReturnableLine line = orderLines.stream().filter(l -> l.productId() == productId).findFirst()
                .orElseThrow(() -> new DomainException("Цього товару немає в замовленні."));
        int left = orderLines.stream().filter(l -> l.productId() == productId).mapToInt(ReturnableLine::quantity).sum() - alreadyReturned;
        if (left <= 0) {
            throw new DomainException("«" + line.name() + "» з цього замовлення вже повернено повністю.");
        }
        if (quantity <= 0 || quantity > left) {
            throw new DomainException("Кількість має бути від 1 до " + left + ".");
        }
        return line;
    }
}
