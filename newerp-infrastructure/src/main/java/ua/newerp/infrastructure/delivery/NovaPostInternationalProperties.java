package ua.newerp.infrastructure.delivery;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the "nova-post-international" section. The API key only in application-local.yml or an environment variable,
 * never in application.yml. The sender is the shop's company, as in the old site's "Міжнародна доставка" settings.
 *
 * @param sandbox       true: api-stage.novapost.com (test waybills), false: the real API
 * @param fakeDocuments only for local development: without a key the stub "creates" test waybills (number starting with TEST)
 */
@ConfigurationProperties("nova-post-international")
public record NovaPostInternationalProperties(String apiKey, boolean sandbox, String baseUrl, boolean fakeDocuments, Sender sender) {

    public static final String PRODUCTION_URL = "https://api.novapost.com/v.1.0";
    public static final String SANDBOX_URL = "https://api-stage.novapost.com/v.1.0";

    public NovaPostInternationalProperties {
        apiKey = apiKey == null ? "" : apiKey.strip();
        baseUrl = baseUrl == null || baseUrl.isBlank() ? (sandbox ? SANDBOX_URL : PRODUCTION_URL) : baseUrl.strip();
        sender = sender == null ? new Sender(null, null, null, null, null, null, null, null, null, null) : sender;
    }

    public boolean configured() {
        return !apiKey.isEmpty();
    }

    /**
     * The sending company. With a tax number (EDRPOU) the API also needs its registration address, which is this address.
     *
     * @param phone 380XXXXXXXXX
     */
    public record Sender(String companyTin, String companyName, String name, String phone, String email, String city, String street,
            String building, String flat, String postCode) {

        public Sender {
            companyTin = orEmpty(companyTin);
            companyName = orEmpty(companyName);
            name = orEmpty(name);
            phone = orEmpty(phone).replaceAll("\\D", "");
            email = orEmpty(email);
            city = orEmpty(city);
            street = orEmpty(street);
            building = orEmpty(building);
            flat = orEmpty(flat);
            postCode = orEmpty(postCode);
        }

        private static String orEmpty(String value) {
            return value == null ? "" : value.strip();
        }
    }
}
