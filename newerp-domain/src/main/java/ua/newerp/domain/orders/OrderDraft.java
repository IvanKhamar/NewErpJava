package ua.newerp.domain.orders;

import java.math.BigDecimal;
import java.util.List;

import ua.newerp.domain.common.DomainException;

/**
 * An order a manager creates by hand (a call, social networks). Checks the same as the old site's quick order form
 * and counts the totals: the products + shipping − discount.
 */
public final class OrderDraft {

    private final List<OrderDraftLine> lines;
    private final BigDecimal shippingCost;
    private final BigDecimal discount;
    private final BigDecimal prepayment;

    private OrderDraft(List<OrderDraftLine> lines, BigDecimal shippingCost, BigDecimal discount, BigDecimal prepayment) {
        this.lines = List.copyOf(lines);
        this.shippingCost = shippingCost;
        this.discount = discount;
        this.prepayment = prepayment;
    }

    public static OrderDraft create(String firstname, String telephone, String shippingMethod, String paymentMethod,
            List<OrderDraftLine> lines, BigDecimal shippingCost, BigDecimal discount, BigDecimal prepayment) {
        if (isBlank(firstname) && isBlank(telephone)) {
            throw new DomainException("Вкажіть ім'я або телефон клієнта.");
        }
        if (isBlank(shippingMethod)) {
            throw new DomainException("Необхідний спосіб доставки.");
        }
        if (isBlank(paymentMethod)) {
            throw new DomainException("Необхідний спосіб оплати.");
        }
        return forLines(lines, shippingCost, discount, prepayment);
    }

    /**
     * Only the lines and sums: for a new order (through {@link #create}) and for changing an existing order's lines,
     * where the customer and the shipping are already written.
     */
    public static OrderDraft forLines(List<OrderDraftLine> lines, BigDecimal shippingCost, BigDecimal discount, BigDecimal prepayment) {
        shippingCost = orZero(shippingCost);
        discount = orZero(discount);
        prepayment = orZero(prepayment);
        if (lines == null || lines.isEmpty()) {
            throw new DomainException("Кошик не може бути порожнім.");
        }
        if (lines.stream().anyMatch(l -> l.quantity() <= 0)) {
            throw new DomainException("Кількість товару має бути більшою за нуль.");
        }
        if (lines.stream().anyMatch(l -> l.price() == null || l.price().signum() < 0)) {
            throw new DomainException("Ціна товару не може бути від'ємною.");
        }
        if (shippingCost.signum() < 0 || discount.signum() < 0 || prepayment.signum() < 0) {
            throw new DomainException("Доставка, знижка й завдаток не можуть бути від'ємними.");
        }

        var draft = new OrderDraft(lines, shippingCost, discount, prepayment);
        if (draft.total().signum() < 0) {
            throw new DomainException("Знижка не може перевищувати суму замовлення.");
        }
        if (prepayment.compareTo(draft.total()) > 0) {
            throw new DomainException("Сума завдатку не може перевищувати суму замовлення.");
        }
        return draft;
    }

    public List<OrderDraftLine> lines() {
        return lines;
    }

    public BigDecimal shippingCost() {
        return shippingCost;
    }

    public BigDecimal discount() {
        return discount;
    }

    /** The deposit the customer has already paid (the rest is, for example, cash on delivery). */
    public BigDecimal prepayment() {
        return prepayment;
    }

    public BigDecimal subTotal() {
        return lines.stream().map(OrderDraftLine::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal total() {
        return subTotal().add(shippingCost).subtract(discount);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
