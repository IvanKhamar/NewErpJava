package ua.newerp.domain.production;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.common.Text;

/** Cut component of a model (dd_stock_production_components) with its own consumables BOM. */
public class ProductionComponent {

    /** One row of the cutting BOM. */
    public record BomRow(Integer sizeId, int itemId, BigDecimal qtyPerUnit) {
    }

    private Integer id;
    private int productionId;
    private String name = "";
    private int sortOrder;
    private final List<ComponentBomLine> bom = new ArrayList<>();

    protected ProductionComponent() {
    }

    public ProductionComponent(int productionId, String name, int sortOrder) {
        this.productionId = productionId;
        rename(name, sortOrder);
    }

    public void rename(String name, int sortOrder) {
        this.name = Text.require(name, "Назва деталі", 100);
        this.sortOrder = sortOrder;
    }

    /** Replaces the cutting BOM: how much of a consumable goes into one component (per size if needed). */
    public void setBom(Collection<BomRow> rows, Collection<Integer> sizeIdsOfModel) {
        for (BomRow row : rows) {
            if (row.qtyPerUnit().signum() <= 0) {
                throw new DomainException("Кількість розхідника на одну деталь має бути більшою за нуль.");
            }
            if (row.sizeId() != null && !sizeIdsOfModel.contains(row.sizeId())) {
                throw new DomainException("Розмір " + row.sizeId() + " не належить моделі цієї деталі.");
            }
        }
        // Arrays.asList allows null (List.of does not), and lists compare by content: a ready-made tuple key
        var keys = new HashSet<List<Integer>>();
        if (!rows.stream().allMatch(r -> keys.add(Arrays.asList(r.sizeId(), r.itemId())))) {
            throw new DomainException("Специфікація крою містить повторювані рядки.");
        }

        bom.clear();
        rows.forEach(r -> bom.add(new ComponentBomLine(r.sizeId(), r.itemId(), r.qtyPerUnit())));
    }

    public Integer getId() {
        return id;
    }

    public int getProductionId() {
        return productionId;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public List<ComponentBomLine> getBom() {
        return Collections.unmodifiableList(bom);
    }
}
