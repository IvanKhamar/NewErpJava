package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

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
        StockCategory category = new StockCategory(request.name(), request.sortOrder());
        if (repository.categoryNameTaken(category.getName())) {
            throw new DomainException("Категорія \"" + category.getName() + "\" вже існує.");
        }
        repository.add(category);
        return category.getId();
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
