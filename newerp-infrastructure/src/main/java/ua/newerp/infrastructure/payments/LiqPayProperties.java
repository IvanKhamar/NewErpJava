package ua.newerp.infrastructure.payments;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings of the "liq-pay" section. The public and private keys only in application-local.yml or environment variables. */
@ConfigurationProperties("liq-pay")
public record LiqPayProperties(String baseUrl, String publicKey, String privateKey) {

    public LiqPayProperties {
        baseUrl = baseUrl == null ? "https://www.liqpay.ua" : baseUrl;
        publicKey = publicKey == null ? "" : publicKey;
        privateKey = privateKey == null ? "" : privateKey;
    }

    public boolean isConfigured() {
        return !publicKey.isBlank() && !privateKey.isBlank();
    }
}
