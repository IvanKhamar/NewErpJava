package ua.newerp.domain.partners;

import java.math.BigDecimal;

/** A row of dd_partner_category_map. Its id is the category id in the feed. */
public class PartnerCategoryMap {

    private Integer id;
    private int categoryId;
    private String sizeName;
    private String externalId = "";
    private String externalName = "";
    private BigDecimal percentSupply;
    private BigDecimal percentSale;

    protected PartnerCategoryMap() {
    }

    PartnerCategoryMap(int categoryId, String sizeName) {
        this.categoryId = categoryId;
        this.sizeName = sizeName;
    }

    void apply(CategoryMapping mapping) {
        externalId = mapping.externalId();
        externalName = mapping.externalName();
        percentSupply = mapping.percentSupply();
        percentSale = mapping.percentSale();
    }

    public Integer getId() {
        return id;
    }

    public int getCategoryId() {
        return categoryId;
    }

    /** null: all sizes of the category. */
    public String getSizeName() {
        return sizeName;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getExternalName() {
        return externalName;
    }

    /** Markup on the size's price, %. null: no markup. */
    public BigDecimal getPercentSupply() {
        return percentSupply;
    }

    /** The crossed-out "old" price as the size's price plus this percent. null: no old price. */
    public BigDecimal getPercentSale() {
        return percentSale;
    }
}
