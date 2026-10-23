package ua.newerp.application.catalog;

/**
 * @param search part of the product name or model
 * @param onlyWithQuota only sizes whose pre-order quota is above 0
 */
public record PreOrderFilter(String search, Boolean onlyWithQuota) {

    public boolean withQuotaOnly() {
        return Boolean.TRUE.equals(onlyWithQuota);
    }
}
