package ua.newerp.infrastructure.payments;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings of the "privat-bank" section. Account ids and tokens only in application-local.yml or environment variables. */
@ConfigurationProperties("privat-bank")
public record PrivatBankProperties(String baseUrl, List<Account> accounts) {

    public PrivatBankProperties {
        baseUrl = baseUrl == null ? "https://acp.privatbank.ua" : baseUrl;
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
    }

    /** An Autoclient connection in Privat24 for business: id and token (the bank issues them for each API client). */
    public record Account(String id, String token) {

        boolean isSet() {
            return id != null && !id.isBlank() && token != null && !token.isBlank();
        }
    }

    public boolean isConfigured() {
        return accounts.stream().anyMatch(Account::isSet);
    }
}
