package ua.newerp.application.production;

import java.util.List;

/**
 * An order line with what the stock needs: the production model the product is linked to (directly or through
 * a category), and the OpenCart option values among which we look for the size.
 */
public record OrderSizeLine(int productId, String productName, int quantity, Integer productionId, List<Integer> optionValueIds) {
}
