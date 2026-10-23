package ua.newerp.infrastructure.sms;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import ua.newerp.application.sms.SmsSendResult;
import ua.newerp.application.sms.SmsSender;

/**
 * The real SMSClub (API v2, as admin/model/tool/sms.php of the old site): POST /sms/send with a Bearer token.
 * The raw reply of the service is returned as is; it goes to the dd_sms_log journal.
 */
class SmsClubSender implements SmsSender {

    static final String NOT_ALLOWED_RESPONSE = "НЕ НАДІСЛАНО: номера немає в SmsClub:OnlyPhones";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient http;
    private final SmsClubProperties options;

    SmsClubSender(RestClient http, SmsClubProperties options) {
        this.http = http;
        this.options = options;
    }

    @Override
    public SmsSendResult send(String phone, String text) {
        if (!options.onlyPhones().isEmpty() && !options.onlyPhones().contains(phone)) {
            return new SmsSendResult(false, NOT_ALLOWED_RESPONSE);
        }

        try {
            Response response = http.post().uri(options.baseUrl().replaceAll("/+$", "") + "/sms/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(options.token()))
                    .body(Map.of("phone", List.of(phone), "message", text, "src_addr", options.sender()))
                    // exchange: the status and body as they are, without an exception for 4xx/5xx (like HttpClient.SendAsync)
                    .exchange((req, res) -> new Response(res.getStatusCode().is2xxSuccessful(),
                            res.getStatusCode().value(), new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
            return new SmsSendResult(response.success() && isAccepted(response.body(), phone),
                    response.body().isEmpty() ? "HTTP " + response.status() : response.body());
        } catch (RestClientException e) {
            // The journal is written anyway: it shows there was an attempt and the SMS did not go out.
            return new SmsSendResult(false, "НЕ НАДІСЛАНО: SMSClub недоступний (" + e.getMessage() + ")");
        }
    }

    /**
     * A successful reply: {"success_request": {"info": {"message id": "number"}}}.
     * SMSClub lists the numbers that did not get the SMS in add_info, so only a number in info counts as sent.
     */
    static boolean isAccepted(String body, String phone) {
        try {
            JsonNode info = JSON.readTree(body).path("success_request").path("info");
            return info.isObject() && info.valueStream().anyMatch(v -> v.isString() && v.stringValue().equals(phone));
        } catch (JacksonException e) {
            return false;
        }
    }

    private record Response(boolean success, int status, String body) {
    }
}
