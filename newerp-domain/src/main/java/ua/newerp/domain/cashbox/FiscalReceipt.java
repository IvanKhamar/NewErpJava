package ua.newerp.domain.cashbox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.common.Html;

/**
 * A sale receipt for Checkbox (POST /api/v1/receipts/sell).
 *
 * @param email null: do not send the receipt by email
 */
public record FiscalReceipt(String cashierName, List<ReceiptGood> goods, ReceiptPayment payment, long discountKopecks, String footer,
        String email) {

    /** OpenCart payment codes Checkbox must see as cashless. The rest is cash (as on the old site). */
    public static final Set<String> CASHLESS_PAYMENT_CODES =
            Set.of("wayforpay", "liqpay", "mono", "bank_transfer", "paid", "paid_shipping", "cod");

    public static final String CASH_ON_DELIVERY_CODE = "cod";

    /**
     * Builds the receipt like the old site: products with a price (free ones are skipped), one payment for the sum to pay,
     * and a discount for the difference when the products cost more (a coupon, a prepayment).
     */
    public static FiscalReceipt build(int orderId, String cashierName, List<ReceiptSourceLine> lines, BigDecimal sum,
            String paymentCode, String paymentMethod, String email) {
        List<ReceiptGood> goods = lines.stream()
                .filter(l -> l.price().signum() != 0)
                .map(l -> new ReceiptGood(String.valueOf(l.productId()), l.name().strip(), kopecks(l.price()), l.quantity() * 1000L))
                .toList();
        if (goods.isEmpty()) {
            throw new DomainException("У замовленні немає товарів з ціною, чек видати нема з чого.");
        }

        long total = kopecks(sum);
        if (total <= 0) {
            throw new DomainException("Сума до оплати нульова (можливо, замовлення повністю оплачене завдатком), чек не потрібен.");
        }

        long goodsTotal = goods.stream().mapToLong(g -> g.priceKopecks() * g.quantityThousandths() / 1000).sum();
        // The old site sent such a receipt as is, and Checkbox rejected it. We explain right away.
        if (total > goodsTotal) {
            throw new DomainException("Сума до оплати (" + money(sum) + ") більша за суму товарів ("
                    + money(BigDecimal.valueOf(goodsTotal, 2)) + "): мабуть, у замовленні є платна доставка. Checkbox такий чек не прийме.");
        }

        ReceiptPaymentType type = CASHLESS_PAYMENT_CODES.contains(paymentCode) ? ReceiptPaymentType.CASHLESS : ReceiptPaymentType.CASH;
        String label = CASH_ON_DELIVERY_CODE.equals(paymentCode) ? "Оплата в Nova pay" : Html.toPlainText(paymentMethod);
        if (label.isEmpty()) {
            label = type == ReceiptPaymentType.CASH ? "Готівка" : "Безготівкова оплата";
        }

        return new FiscalReceipt(cashierName, goods, new ReceiptPayment(type, label, total), goodsTotal - total,
                "Замовлення № " + orderId, email == null || email.isBlank() ? null : email.strip());
    }

    private static long kopecks(BigDecimal value) {
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
