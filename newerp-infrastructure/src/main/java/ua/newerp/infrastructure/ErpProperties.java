package ua.newerp.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * General settings from the "erp" section of application.yml (ErpOptions in .NET).
 *
 * @param languageId language of OpenCart directories (dd_order_status, dd_order_source keep a name per language).
 *                   In the real database all names are stored with language_id = 3; other languages never worked.
 */
@ConfigurationProperties("erp")
public record ErpProperties(int languageId) {
}
