package ua.newerp.domain.payments;

import java.math.BigDecimal;
import java.math.RoundingMode;

import ua.newerp.domain.common.DomainException;

/** Rules of paying an order through LiqPay, as in catalog/controller/liqpay/payment.php of the old site. */
public final class LiqPayPayment {

    private LiqPayPayment() {
    }

    /** The link amount: the whole order total by default; it can be less (a prepayment), but not more and not zero. */
    public static BigDecimal validateAmount(BigDecimal amount, BigDecimal orderTotal) {
        BigDecimal value = (amount == null ? orderTotal : amount).setScale(2, RoundingMode.HALF_EVEN);
        if (value.signum() <= 0) {
            throw new DomainException("Сума оплати має бути більшою за нуль.");
        }
        if (value.compareTo(orderTotal) > 0) {
            throw new DomainException("Сума оплати не може перевищувати суму замовлення ("
                    + orderTotal.setScale(2, RoundingMode.HALF_UP).toPlainString() + ").");
        }
        return value;
    }

    /** The payment purpose: the same text the old site made. */
    public static String description(int orderId, String customer, String telephone) {
        return "Оплата замовлення #" + orderId + ".\nПокупець: " + customer.strip() + ". \nТелефон: " + telephone.strip();
    }

    /**
     * LiqPay's answer to action=status: the status field, and err_code payment_not_found for an unknown payment.
     * Test (sandbox) payments count as paid, so the check can be walked in test mode.
     */
    public static LiqPayState interpret(String status, String errorCode) {
        if ("payment_not_found".equals(errorCode)) {
            return LiqPayState.NOT_FOUND;
        }
        if (status == null || status.isEmpty()) {
            if (errorCode != null) {
                throw new DomainException("LiqPay відповів помилкою: " + errorCode + ".");
            }
            return LiqPayState.WAITING;
        }
        return switch (status) {
            case "success", "sandbox", "wait_accept", "wait_compensation" -> LiqPayState.PAID;
            case "reversed" -> LiqPayState.REVERSED;
            case "failure", "error" -> LiqPayState.FAILED;
            default -> LiqPayState.WAITING;
        };
    }

    public static String label(LiqPayState state) {
        return switch (state) {
            case NOT_FOUND -> "Ще не оплачено";
            case WAITING -> "Оплата в процесі";
            case PAID -> "Оплачено";
            case FAILED -> "Оплата не вдалася";
            case REVERSED -> "Гроші повернено";
        };
    }
}
