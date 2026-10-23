package ua.newerp.domain.catalog;

import java.math.BigDecimal;

import ua.newerp.domain.common.DomainException;

/**
 * A line of the checkout cross-sell panel (dd_crosssell_item): one product, or every product of a category, offered with a
 * discount in percent (0 = shown without a discount).
 */
public record CrossSellItem(String type, int targetId, BigDecimal discount) {

    public static final String PRODUCT = "product";
    public static final String CATEGORY = "category";

    private static final BigDecimal MAX_DISCOUNT = new BigDecimal("99.99");

    public CrossSellItem {
        if (!PRODUCT.equals(type) && !CATEGORY.equals(type)) {
            throw new DomainException("Тип рядка допродажу — product або category.");
        }
        if (targetId <= 0) {
            throw new DomainException("Оберіть товар або категорію.");
        }
        discount = requireDiscount(discount);
    }

    /** 0 to 99.99 percent with at most two decimals (decimal(5,2) of the old table); null means no discount. */
    public static BigDecimal requireDiscount(BigDecimal discount) {
        if (discount == null) {
            return BigDecimal.ZERO;
        }
        if (discount.signum() < 0 || discount.compareTo(MAX_DISCOUNT) > 0 || discount.stripTrailingZeros().scale() > 2) {
            throw new DomainException("Знижка — від 0 до 99,99 %, не більше двох знаків після коми.");
        }
        return discount;
    }
}
