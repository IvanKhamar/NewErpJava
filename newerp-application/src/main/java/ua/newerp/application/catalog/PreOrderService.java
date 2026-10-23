package ua.newerp.application.catalog;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.catalog.PreOrder;

/**
 * Pre-orders of sizes, as on the old site: a quota and an expected date are set for each product size, the storefront sells
 * ahead, and what was sold ahead is regularly taken off the quota.
 */
@Service
@Transactional
public class PreOrderService {

    private static final int LIST_LIMIT = 200;

    private final PreOrderStore store;

    public PreOrderService(PreOrderStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public List<PreOrderRow> list(PreOrderFilter filter) {
        String search = filter.search() == null || filter.search().isBlank() ? null : filter.search().strip();
        return store.list(new PreOrderFilter(search, filter.withQuotaOnly()), LIST_LIMIT);
    }

    public void update(int productOptionValueId, UpdatePreOrderRequest request) {
        PreOrder.validateQuota(request.quota());
        if (!store.update(productOptionValueId, request.quota(), request.dateAvailable())) {
            throw new NotFoundException("Розміру " + productOptionValueId + " не знайдено.");
        }
    }

    public PreOrderSettleResult settle() {
        return new PreOrderSettleResult(store.settle(null));
    }
}
