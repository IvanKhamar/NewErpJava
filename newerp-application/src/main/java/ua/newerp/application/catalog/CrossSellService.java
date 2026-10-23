package ua.newerp.application.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.LookupItem;
import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.catalog.CrossSellItem;
import ua.newerp.domain.common.DomainException;

/**
 * What the checkout cross-sell panel offers: single products and whole categories, each with its discount. The order of the
 * products in the panel is set on the product order page (the cross-sell group).
 */
@Service
@Transactional
public class CrossSellService {

    private final CrossSellStore store;

    public CrossSellService(CrossSellStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public List<CrossSellItemDto> list() {
        return store.list();
    }

    @Transactional(readOnly = true)
    public List<LookupItem> categories() {
        return store.categories();
    }

    public CrossSellItemDto add(SaveCrossSellItemRequest request) {
        String type = request.type() == null ? null : request.type().strip().toLowerCase(Locale.ROOT);
        var item = new CrossSellItem(type, request.targetId(), request.discount());
        if (!store.targetExists(item.type(), item.targetId())) {
            throw new NotFoundException((CrossSellItem.PRODUCT.equals(item.type()) ? "Товар " : "Категорію ") + item.targetId()
                    + " не знайдено.");
        }
        if (store.findId(item.type(), item.targetId()).isPresent()) {
            throw new DomainException(CrossSellItem.PRODUCT.equals(item.type()) ? "Цей товар уже є в панелі допродажу."
                    : "Ця категорія вже є в панелі допродажу.");
        }
        return require(store.add(item));
    }

    public CrossSellItemDto setDiscount(int id, BigDecimal discount) {
        require(id);
        store.setDiscount(id, CrossSellItem.requireDiscount(discount));
        return require(id);
    }

    public void delete(int id) {
        require(id);
        store.delete(id);
    }

    private CrossSellItemDto require(int id) {
        return store.find(id).orElseThrow(() -> new NotFoundException("Рядок допродажу " + id + " не знайдено."));
    }
}
