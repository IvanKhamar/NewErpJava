package ua.newerp.domain.delivery;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ua.newerp.domain.common.Text;

/**
 * A branch or parcel locker from the Nova Poshta directory (dd_novaposhta_warehouse of the old site).
 * The old site's storefront uses it too, so sorting and "working" follow the old code.
 */
public class NovaPoshtaWarehouse {

    /** Categories shown as working: branch, partner store, mobile branch, parcel locker. */
    public static final Set<String> ACTIVE_CATEGORIES = Set.of("Branch", "Store", "MobileBranch", "Postomat");

    // Compiled once (like [GeneratedRegex]): "Відділення №12: …" → 12
    private static final Pattern BRANCH_NUMBER = Pattern.compile("№\\s*(\\d+)");

    private String ref = "";
    private String description = "";
    private String category = "";
    private String cityRef = "";
    private String cityDescription = "";
    private int maxWeight;
    private int sortOrder;
    private boolean status;

    protected NovaPoshtaWarehouse() {
    }

    public NovaPoshtaWarehouse(String reference, String description, String category, String cityRef,
            String cityDescription, int maxWeight, boolean working) {
        this.ref = reference;
        this.description = Text.truncate(description, 255);
        this.category = category;
        this.cityRef = cityRef;
        this.cityDescription = Text.truncate(cityDescription.strip(), 255);
        this.maxWeight = maxWeight;
        // As the old site: branch number from the name, so №2 goes before №10.
        Matcher m = BRANCH_NUMBER.matcher(description);
        this.sortOrder = m.find() ? Integer.parseInt(m.group(1)) : 1;
        this.status = working && ACTIVE_CATEGORIES.contains(category);
    }

    public String getRef() {
        return ref;
    }

    public String getDescription() {
        return description;
    }

    /** CategoryOfWarehouse from the API: Branch, Postomat, Store… */
    public String getCategory() {
        return category;
    }

    public String getCityRef() {
        return cityRef;
    }

    public String getCityDescription() {
        return cityDescription;
    }

    /** Max parcel weight, kg (TotalMaxWeightAllowed; 0 = no limit). */
    public int getMaxWeight() {
        return maxWeight;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    /** Works and accepts parcels. */
    public boolean isStatus() {
        return status;
    }
}
