package ua.newerp.infrastructure.sms;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the "sms-club" section. The token only in application-local.yml or the SMS_CLUB_TOKEN environment variable.
 *
 * @param sender the sender's alpha name, as on the old site
 * @param onlyPhones if not empty, SMS go only to these numbers (380XXXXXXXXX): for checks on a local database,
 *                   where phones are invented and may belong to real people
 */
@ConfigurationProperties("sms-club")
public record SmsClubProperties(String baseUrl, String token, String sender, List<String> onlyPhones) {

    public SmsClubProperties {
        baseUrl = baseUrl == null ? "https://im.smsclub.mobi" : baseUrl;
        token = token == null ? "" : token;
        sender = sender == null ? "Vidro.ua" : sender;
        onlyPhones = onlyPhones == null ? List.of() : List.copyOf(onlyPhones);
    }

    public boolean isConfigured() {
        return !token.isBlank();
    }
}
