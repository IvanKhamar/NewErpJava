package ua.newerp.domain.stock;

import ua.newerp.domain.common.Text;

public class StockCategory {

    private Integer id;
    private String name = "";
    private int sortOrder;

    protected StockCategory() {
    }

    public StockCategory(String name, int sortOrder) {
        update(name, sortOrder);
    }

    public void update(String name, int sortOrder) {
        this.name = Text.require(name, "Назва категорії", 100);
        this.sortOrder = sortOrder;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
