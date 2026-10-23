package ua.newerp.infrastructure.cashbox;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the "checkbox" section. Cashier logins, passwords and keys only in application-local.yml or environment variables.
 *
 * @param fakeReceipts only for local development: without keys the stub "fiscalizes" test receipts, so the whole path can be
 *                     walked in the UI. Ignored when at least one cashier has keys.
 * @param autoCloseIntervalMinutes how often to check open shifts (minutes); 0: never (POST /cashbox/shifts/auto-close still works)
 */
@ConfigurationProperties("checkbox")
public record CheckboxProperties(
        String baseUrl,
        String clientName,
        String clientVersion,
        boolean fakeReceipts,
        Integer autoCloseIntervalMinutes,
        List<Cashier> cashiers) {

    public CheckboxProperties {
        baseUrl = baseUrl == null ? "https://api.checkbox.in.ua" : baseUrl;
        // The X-Client-Name header: Checkbox asks integrations to mark themselves like this
        clientName = clientName == null ? "NewErp" : clientName;
        clientVersion = clientVersion == null ? "1.0" : clientVersion;
        autoCloseIntervalMinutes = autoCloseIntervalMinutes == null ? 15 : autoCloseIntervalMinutes;
        cashiers = cashiers == null ? List.of() : List.copyOf(cashiers);
    }

    /**
     * A cashier's keys.
     *
     * @param cashierId  id from dd_cashbox_user
     * @param licenseKey the cash register's license key (X-License-Key)
     */
    public record Cashier(int cashierId, String login, String password, String licenseKey) {

        boolean hasKeys() {
            return login != null && !login.isBlank() && password != null && !password.isBlank();
        }
    }

    public boolean anyKeys() {
        return cashiers.stream().anyMatch(Cashier::hasKeys);
    }
}
