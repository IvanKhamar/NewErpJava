package ua.newerp.domain.catalog;

import ua.newerp.domain.common.DomainException;

/**
 * A pre-order of a product size, as on the old site: when there is no stock, the storefront sells ahead up to
 * pre_order_quantity pieces and the stock goes negative. Later what was sold ahead is taken off the quota (updatePreOrdersQuantity).
 */
public final class PreOrder {

    public static final int MAX_QUOTA = 9999;

    /** Stock and quota after settling. */
    public record Settled(int quantity, int quota) {
    }

    private PreOrder() {
    }

    /** As in the old site's cart: a size without stock tracking is always in stock; a pre-order when stock ≤ 0 and there is a quota. */
    public static SizeAvailability availability(int quantity, int quota, boolean subtract) {
        if (!subtract || quantity > 0) {
            return SizeAvailability.IN_STOCK;
        }
        return quota > 0 ? SizeAvailability.PRE_ORDER : SizeAvailability.SOLD_OUT;
    }

    /**
     * Takes what was sold ahead (negative stock) off the quota. The old code did quota += stock, and overselling past the quota
     * gave a negative quota, which the storefront took as permission to keep selling. Here the quota does not go below 0,
     * and what was oversold past it stays negative in the stock, so it is visible.
     */
    public static Settled settle(int quantity, int quota) {
        if (quantity >= 0 || quota <= 0) {
            return new Settled(quantity, quota);
        }
        int covered = Math.min(quota, -quantity);
        return new Settled(quantity + covered, quota - covered);
    }

    public static void validateQuota(int quota) {
        if (quota < 0 || quota > MAX_QUOTA) {
            throw new DomainException("Ліміт передзамовлення — від 0 до " + MAX_QUOTA + " штук.");
        }
    }
}
