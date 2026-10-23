package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.stock.StockCategory;
import ua.newerp.domain.stock.StockItem;
import ua.newerp.domain.stock.StockMovement;
import ua.newerp.domain.stock.StockSupplier;

/** Use cases of the consumables stock. Each public method is one transaction. */
@Service
@Transactional
public class StockService {

    private final StockRepository repository;
    private final Clock clock;

    public StockService(StockRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public int createCategory(CreateStockCategoryRequest request) {
        StockCategory category = new StockCategory(request.name(), request.sortOrder() == null ? 0 : request.sortOrder());
        if (repository.categoryNameTaken(category.getName(), null)) {
            throw new DomainException("Категорія \"" + category.getName() + "\" вже існує.");
        }
        repository.add(category);
        return category.getId();
    }

    public void updateCategory(int categoryId, CreateStockCategoryRequest request) {
        StockCategory category = repository.findCategory(categoryId)
                .orElseThrow(() -> new NotFoundException("Категорію " + categoryId + " не знайдено."));
        category.update(request.name(), request.sortOrder() == null ? category.getSortOrder() : request.sortOrder());
        if (repository.categoryNameTaken(category.getName(), categoryId)) {
            throw new DomainException("Категорія \"" + category.getName() + "\" вже існує.");
        }
    }

    /** Only an empty category: items keep their category_id even when archived, so they would point nowhere. */
    public void deleteCategory(int categoryId) {
        StockCategory category = repository.findCategory(categoryId)
                .orElseThrow(() -> new NotFoundException("Категорію " + categoryId + " не знайдено."));
        int items = repository.countItemsInCategory(categoryId);
        if (items > 0) {
            throw new DomainException("Категорію \"" + category.getName() + "\" не можна видалити: у ній позицій — " + items
                    + ". Перенесіть їх в іншу категорію.");
        }
        repository.remove(category);
    }

    public void updateSupplier(int supplierId, CreateStockSupplierRequest request) {
        requireSupplier(supplierId).update(request.name(), request.phone(), request.email(), request.categories(), request.notes());
    }

    /** Only a supplier nothing refers to: no items (archived too) and no purchase orders. */
    public void deleteSupplier(int supplierId) {
        StockSupplier supplier = requireSupplier(supplierId);
        if (repository.countSupplierReferences(supplierId) > 0) {
            throw new DomainException("Постачальника \"" + supplier.getName() + "\" не можна видалити: на нього посилаються позиції "
                    + "або закупівлі. Змініть постачальника в позиціях.");
        }
        repository.remove(supplier);
    }

    /**
     * Archives an item (status = 0). Refused while it is in stock, in a BOM of an active model (production would then
     * not write it off) or in an open purchase order.
     */
    public void archiveItem(int itemId) {
        StockItem item = requireItem(itemId);
        List<String> models = repository.modelsUsingItem(itemId);
        if (!models.isEmpty()) {
            throw new DomainException("«" + item.getName() + "» є в специфікаціях моделей: " + String.join(", ", models)
                    + ". Спершу приберіть його звідти.");
        }
        List<Integer> orders = repository.openPurchaseOrdersWithItem(itemId);
        if (!orders.isEmpty()) {
            throw new DomainException("«" + item.getName() + "» є у відкритих закупівлях: "
                    + orders.stream().map(id -> "#" + id).collect(Collectors.joining(", ")) + ".");
        }
        item.archive();
    }

    public int createSupplier(CreateStockSupplierRequest request) {
        StockSupplier supplier = new StockSupplier(request.name(), request.phone(), request.email(), request.categories(), request.notes());
        repository.add(supplier);
        return supplier.getId();
    }

    public int createItem(SaveStockItemRequest request) {
        requireReferences(request);
        StockItem item = new StockItem(request.categoryId(), request.supplierId(), request.name(), request.unit(),
                request.minQuantity(), request.usedFor(), request.price());
        repository.add(item);
        return item.getId();
    }

    /** Changes the item's description (name, category, supplier, price, minimum). Does not touch the quantity. */
    public void updateItem(int itemId, SaveStockItemRequest request) {
        StockItem item = requireItem(itemId);
        requireReferences(request);
        item.update(request.categoryId(), request.supplierId(), request.name(), request.unit(), request.minQuantity(),
                request.usedFor(), request.price());
        // No save call: the item is tracked, Hibernate writes the change at commit.
    }

    public StockMovementDto adjust(int itemId, AdjustStockRequest request, int userId, String username) {
        StockItem item = requireItem(itemId);
        StockMovement m = item.adjust(request.delta(), request.reason(), userId, username, LocalDateTime.now(clock));
        repository.add(m);
        return new StockMovementDto(m.getId(), item.getId(), m.getDelta(), m.getStockBefore(), m.getStockAfter(),
                m.getComment(), m.getUserId(), m.getUsername(), m.getCreatedAt());
    }

    public void setMinQuantity(int itemId, BigDecimal minQuantity) {
        requireItem(itemId).setMinQuantity(minQuantity);
    }

    private StockSupplier requireSupplier(int supplierId) {
        return repository.findSupplier(supplierId)
                .orElseThrow(() -> new NotFoundException("Постачальника " + supplierId + " не знайдено."));
    }

    private StockItem requireItem(int itemId) {
        return repository.findItem(itemId).orElseThrow(() -> new NotFoundException("Позицію складу " + itemId + " не знайдено."));
    }

    private void requireReferences(SaveStockItemRequest request) {
        if (request.categoryId() != null && !repository.categoryExists(request.categoryId())) {
            throw new DomainException("Категорії " + request.categoryId() + " не існує.");
        }
        if (request.supplierId() != null && !repository.supplierExists(request.supplierId())) {
            throw new DomainException("Постачальника " + request.supplierId() + " не існує.");
        }
    }
}
