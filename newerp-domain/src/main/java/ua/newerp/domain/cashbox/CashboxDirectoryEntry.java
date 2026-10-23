package ua.newerp.domain.cashbox;

import java.time.LocalDateTime;

import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.common.Text;

/**
 * A cash register directory entry with a name and a description: a warehouse (point of sale) or sole proprietor details.
 * Two separate tables of the same shape; in JPA this base class is a mapped superclass, not an inheritance hierarchy.
 */
public abstract class CashboxDirectoryEntry {

    private Integer id;
    private String name = "";
    private String description = "";
    private LocalDateTime dateAdded;

    protected CashboxDirectoryEntry() {
    }

    protected CashboxDirectoryEntry(String name, String description, LocalDateTime createdAt) {
        rename(name, description);
        this.dateAdded = createdAt;
    }

    public void rename(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new DomainException("Назва обов'язкова.");
        }
        this.name = name.strip();
        this.description = Text.orEmpty(description);
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getDateAdded() {
        return dateAdded;
    }
}
