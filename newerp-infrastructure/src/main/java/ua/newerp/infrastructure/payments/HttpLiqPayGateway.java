package ua.newerp.infrastructure.payments;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import ua.newerp.application.payments.LiqPayCheckout;
import ua.newerp.application.payments.LiqPayGateway;
import ua.newerp.application.payments.LiqPayStatusReply;
import ua.newerp.domain.common.DomainException;

/**
 * The real LiqPay. Every request is JSON in base64 (data) and a signature base64(sha1(private + data + private)),
 * like the PHP SDK the old site used. The payment link is built locally, without a request to LiqPay.
 */
class HttpLiqPayGateway implements LiqPayGateway {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient http;
    private final LiqPayProperties options;

    HttpLiqPayGateway(RestClient http, LiqPayProperties options) {
        this.http = http;
        this.options = options;
    }

    @Override
    public boolean isTest() {
        return false;
    }

    @Override
    public String checkoutUrl(LiqPayCheckout checkout) {
        return buildCheckoutUrl(options, checkout);
    }

    @Override
    public LiqPayStatusReply status(String orderId) {
        ObjectNode parameters = JSON.createObjectNode()
                .put("action", "status").put("version", "3").put("public_key", options.publicKey()).put("order_id", orderId);
        Signed signed = sign(options, parameters);
        var form = new LinkedMultiValueMap<String, String>();
        form.add("data", signed.data());
        form.add("signature", signed.signature());

        Response response = http.post().uri(baseUrl(options) + "/api/request")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange((req, res) -> new Response(res.getStatusCode().value(),
                        new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        if (response.status() < 200 || response.status() >= 300) {
            throw new DomainException("LiqPay недоступний: " + response.status() + ".");
        }
        return parseStatus(response.body());
    }

    /** A link to the LiqPay payment page (checkout) with the parameters of the old site's payment.php form. */
    static String buildCheckoutUrl(LiqPayProperties options, LiqPayCheckout checkout) {
        ObjectNode parameters = JSON.createObjectNode()
                .put("public_key", options.publicKey())
                .put("version", "3")
                .put("action", "pay")
                .put("amount", checkout.amount())
                .put("currency", checkout.currency())
                .put("description", checkout.description())
                .put("order_id", checkout.orderId())
                .put("language", "uk");
        Signed signed = sign(options, parameters);
        return baseUrl(options) + "/api/3/checkout?data=" + URLEncoder.encode(signed.data(), StandardCharsets.UTF_8)
                + "&signature=" + URLEncoder.encode(signed.signature(), StandardCharsets.UTF_8);
    }

    static Signed sign(LiqPayProperties options, ObjectNode parameters) {
        String data = Base64.getEncoder().encodeToString(JSON.writeValueAsString(parameters).getBytes(StandardCharsets.UTF_8));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1")
                    .digest((options.privateKey() + data + options.privateKey()).getBytes(StandardCharsets.UTF_8));
            return new Signed(data, Base64.getEncoder().encodeToString(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is part of every Java runtime", e);
        }
    }

    static LiqPayStatusReply parseStatus(String json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (JacksonException e) {
            throw new DomainException("LiqPay повернув незрозумілу відповідь.");
        }
        // end_date: when the payment finished, in Unix milliseconds
        BigDecimal endMillis = number(root.path("end_date"));
        LocalDateTime endDate = endMillis == null
                ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(endMillis.longValue()), ZoneOffset.UTC);
        return new LiqPayStatusReply(text(root.path("status")), text(root.path("err_code")), number(root.path("amount")), endDate);
    }

    private static String text(JsonNode node) {
        return node.isString() ? node.stringValue() : null;
    }

    private static BigDecimal number(JsonNode node) {
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isString()) {
            try {
                return new BigDecimal(node.stringValue());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String baseUrl(LiqPayProperties options) {
        return options.baseUrl().replaceAll("/+$", "");
    }

    record Signed(String data, String signature) {
    }

    private record Response(int status, String body) {
    }
}
