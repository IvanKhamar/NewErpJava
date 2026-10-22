package ua.newerp.infrastructure.production;

import java.io.Serializable;

/**
 * Composite key of dd_stock_production_component_stock (component_id + size_id), like HasKey(x => new { ... }) in EF.
 * JPA needs it as a separate class with the same field names as the entity; a record gives equals and hashCode.
 */
public record ComponentStockId(int componentId, int sizeId) implements Serializable {
}
