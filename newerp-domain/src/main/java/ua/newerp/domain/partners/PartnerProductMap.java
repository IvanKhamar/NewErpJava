package ua.newerp.domain.partners;

import java.math.BigDecimal;

/** A row of dd_partner_product_map: a single product in the partner's feed. */
public class PartnerProductMap {

    private Integer id;
    private int productId;
    private String sizeName;
    private String externalId = "";
    private String externalName = "";
    private BigDecimal priceSupply;
    private BigDecimal priceSale;

    protected PartnerProductMap() {
    }

    PartnerProductMap(int productId, String sizeName) {
        this.productId = productId;
        this.sizeName = sizeName;
    }

    void apply(ProductMapping mapping) {
        externalId = mapping.externalId();
        externalName = mapping.externalName();
        priceSupply = mapping.priceSupply();
        priceSale = mapping.priceSale();
    }

    public Integer getId() {
        return id;
    }

    public int getProductId() {
        return productId;
    }

    /** null: all sizes of the product. */
    public String getSizeName() {
        return sizeName;
    }

    /** The product's code at the partner (vendorCode in the feed). */
    public String getExternalId() {
        return externalId;
    }

    public String getExternalName() {
        return externalName;
    }

    /** The price for the partner. null: the size's (or product's) price in the shop. */
    public BigDecimal getPriceSupply() {
        return priceSupply;
    }

    /** The crossed-out "old" price. null: none. */
    public BigDecimal getPriceSale() {
        return priceSale;
    }
}
