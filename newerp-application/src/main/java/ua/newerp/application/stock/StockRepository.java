package ua.newerp.application.stock;

import java.util.List;
import java.util.Optional;

import ua.newerp.application.common.LookupItem;
import ua.newerp.domain.stock.StockCategory;
import ua.newerp.domain.stock.StockItem;
import ua.newerp.domain.stock.StockMovement;
import ua.newerp.domain.stock.StockSupplier;

/**
 * Stock data. Loaded entities are tracked inside the transaction: their changes are written at commit
 * (like EF's change tracker + SaveChanges at the end of a use case).
 */
public interface StockRepository {

    List<LookupItem> listCategories();

    List<StockSupplierDto> listSuppliers();

    List<StockItemDto> listItems(StockItemFilter filter);

    Optional<StockItemDto> getItemDto(int itemId);

    List<StockMovementDto> listMovements(int itemId);

    boolean categoryExists(int categoryId);

    /** Whether another category (not {@code exceptId}) already has this name. */
    boolean categoryNameTaken(String name, Integer exceptId);

    Optional<StockCategory> findCategory(int categoryId);

    Optional<StockSupplier> findSupplier(int supplierId);

    /** Items of the category, archived ones too (they keep their category_id). */
    int countItemsInCategory(int categoryId);

    /** Items of the supplier (archived too) plus its purchase orders: what still refers to it. */
    int countSupplierReferences(int supplierId);

    /** Names of active models whose cutting or sewing BOM takes this item. */
    List<String> modelsUsingItem(int itemId);

    /** Ids of draft and sent purchase orders with this item. */
    List<Integer> openPurchaseOrdersWithItem(int itemId);

    void remove(StockCategory category);

    void remove(StockSupplier supplier);

    boolean supplierExists(int supplierId);

    Optional<StockItem> findItem(int itemId);

    /** Saves a new object right away, so its id is known. */
    void add(StockCategory category);

    void add(StockSupplier supplier);

    void add(StockItem item);

    void add(StockMovement movement);
}
