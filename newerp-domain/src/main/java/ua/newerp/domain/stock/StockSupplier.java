package ua.newerp.domain.stock;

import ua.newerp.domain.common.Text;

public class StockSupplier {

    private Integer id;
    private String name = "";
    // The old module stores an empty string, not NULL.
    private String phone = "";
    private String email = "";
    private String categories = "";
    private String notes = "";

    protected StockSupplier() {
    }

    public StockSupplier(String name, String phone, String email, String categories, String notes) {
        update(name, phone, email, categories, notes);
    }

    public void update(String name, String phone, String email, String categories, String notes) {
        this.name = Text.require(name, "Назва постачальника", 150);
        this.phone = Text.orEmpty(phone);
        this.email = Text.orEmpty(email);
        this.categories = Text.orEmpty(categories);
        this.notes = Text.orEmpty(notes);
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    /** Free text: what exactly it supplies (fabrics, fittings...). */
    public String getCategories() {
        return categories;
    }

    public String getNotes() {
        return notes;
    }
}
