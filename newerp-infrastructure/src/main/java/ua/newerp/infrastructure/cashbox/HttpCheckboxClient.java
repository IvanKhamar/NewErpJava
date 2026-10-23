package ua.newerp.infrastructure.cashbox;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import ua.newerp.application.cashbox.CheckboxClient;
import ua.newerp.application.cashbox.CheckboxShiftState;
import ua.newerp.domain.cashbox.FiscalReceipt;
import ua.newerp.domain.cashbox.ReceiptGood;
import ua.newerp.domain.cashbox.ReceiptPaymentType;
import ua.newerp.domain.common.DomainException;

/**
 * Client of the Checkbox REST API (https://api.checkbox.in.ua). The cashier's token comes from signing in (cashier/signin) and is
 * kept in memory; when it expires (401), the client signs in again. The old site stored the token in the database instead.
 * Requests are not logged, so passwords and tokens never get into the log.
 */
class HttpCheckboxClient implements CheckboxClient {

    /** How many times to ask whether the Z report is ready after closing a shift. */
    static final int CLOSE_POLL_ATTEMPTS = 5;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient http;
    private final CheckboxProperties options;
    private final Duration closePollDelay;
    private final Map<Integer, String> tokens = new ConcurrentHashMap<>();

    HttpCheckboxClient(RestClient http, CheckboxProperties options, Duration closePollDelay) {
        this.http = http;
        this.options = options;
        this.closePollDelay = closePollDelay;
    }

    @Override
    public boolean isFake() {
        return false;
    }

    @Override
    public boolean canUse(int cashierId) {
        return find(cashierId) != null;
    }

    @Override
    public String openShift(int cashierId) {
        // If the shift is already open (opened on the old site or in the Checkbox app), take it.
        JsonNode current = send(cashierId, HttpMethod.GET, "/api/v1/cashier/shift", null, false);
        String openId = text(current, "id");
        if (!openId.isEmpty() && !"CLOSED".equals(text(current, "status"))) {
            return openId;
        }

        String id = text(send(cashierId, HttpMethod.POST, "/api/v1/shifts", null, true), "id");
        if (id.isEmpty()) {
            throw new DomainException("Checkbox не повернув id нової зміни.");
        }
        return id;
    }

    @Override
    public CheckboxShiftState getShift(int cashierId, String shiftId) {
        JsonNode shift = send(cashierId, HttpMethod.GET, "/api/v1/shifts/" + encode(shiftId), null, false);
        String zReportId = text(shift.path("z_report"), "id");
        return new CheckboxShiftState(shiftId, "CLOSED".equals(text(shift, "status")), zReportId.isEmpty() ? null : zReportId);
    }

    @Override
    public String closeShift(int cashierId) {
        JsonNode shift = send(cashierId, HttpMethod.POST, "/api/v1/shifts/close", null, false);
        String zReportId = text(shift.path("z_report"), "id");
        if (!zReportId.isEmpty()) {
            return zReportId;
        }

        // Closing is asynchronous: first the status is CLOSING, the Z report appears in a few seconds.
        String shiftId = text(shift, "id");
        if (shiftId.isEmpty()) {
            return null;
        }
        for (int attempt = 0; attempt < CLOSE_POLL_ATTEMPTS; attempt++) {
            sleep(closePollDelay);
            CheckboxShiftState state = getShift(cashierId, shiftId);
            if (state.zReportId() != null) {
                return state.zReportId();
            }
        }
        return null;
    }

    @Override
    public String createXReport(int cashierId) {
        String id = text(send(cashierId, HttpMethod.POST, "/api/v1/reports", null, false), "id");
        if (id.isEmpty()) {
            throw new DomainException("Checkbox не повернув id X-звіту.");
        }
        return id;
    }

    @Override
    public String getReportText(int cashierId, String reportId) {
        return sendRaw(cashierId, HttpMethod.GET, "/api/v1/reports/" + encode(reportId) + "/text", null, false);
    }

    @Override
    public String sell(int cashierId, FiscalReceipt receipt) {
        ObjectNode body = JSON.createObjectNode();
        body.put("cashier_name", receipt.cashierName());
        ArrayNode goods = body.putArray("goods");
        for (ReceiptGood g : receipt.goods()) {
            ObjectNode item = goods.addObject();
            item.putObject("good").put("code", g.code()).put("name", g.name()).put("price", g.priceKopecks());
            item.put("quantity", g.quantityThousandths());
        }
        body.putArray("payments").addObject()
                .put("type", receipt.payment().type() == ReceiptPaymentType.CASH ? "CASH" : "CASHLESS")
                .put("value", receipt.payment().valueKopecks())
                .put("label", receipt.payment().label());
        body.put("footer", receipt.footer());
        if (receipt.discountKopecks() > 0) {
            body.putArray("discounts").addObject().put("type", "DISCOUNT").put("mode", "VALUE").put("value", receipt.discountKopecks());
        }
        if (receipt.email() != null) {
            body.putObject("delivery").put("email", receipt.email());
        }

        String id = text(send(cashierId, HttpMethod.POST, "/api/v1/receipts/sell", body, false), "id");
        if (id.isEmpty()) {
            throw new DomainException("Checkbox не повернув id чека.");
        }
        return id;
    }

    @Override
    public String receiptUrl(String receiptId) {
        return baseUrl() + "/api/v1/receipts/" + encode(receiptId) + "/html";
    }

    private CheckboxProperties.Cashier find(int cashierId) {
        return options.cashiers().stream().filter(c -> c.cashierId() == cashierId && c.hasKeys()).findFirst().orElse(null);
    }

    private JsonNode send(int cashierId, HttpMethod method, String path, JsonNode body, boolean withLicense) {
        String text = sendRaw(cashierId, method, path, body, withLicense);
        return text.isBlank() ? JSON.missingNode() : JSON.readTree(text);
    }

    /** A request on behalf of the cashier. On 401 it signs in again (the token may have expired) and repeats the request once. */
    private String sendRaw(int cashierId, HttpMethod method, String path, JsonNode body, boolean withLicense) {
        CheckboxProperties.Cashier cashier = find(cashierId);
        if (cashier == null) {
            throw new DomainException("Для касира " + cashierId + " не задано ключі Checkbox.");
        }
        for (int attempt = 0; ; attempt++) {
            String cached = tokens.get(cashierId);
            String token = cached != null ? cached : signIn(cashier);
            Response response = exchange(method, path, body, token, withLicense ? cashier.licenseKey() : null);
            if (response.status() == HttpStatus.UNAUTHORIZED.value() && attempt == 0) {
                tokens.remove(cashierId);
                continue;
            }
            return ensureSuccess(response);
        }
    }

    private String signIn(CheckboxProperties.Cashier cashier) {
        ObjectNode body = JSON.createObjectNode().put("login", cashier.login()).put("password", cashier.password());
        String text = ensureSuccess(exchange(HttpMethod.POST, "/api/v1/cashier/signin", body, null, null));
        String token = text(JSON.readTree(text), "access_token");
        if (token.isEmpty()) {
            throw new DomainException("Checkbox не повернув токен касира.");
        }
        tokens.put(cashier.cashierId(), token);
        return token;
    }

    private Response exchange(HttpMethod method, String path, JsonNode body, String token, String licenseKey) {
        var request = http.method(method).uri(baseUrl() + path)
                .header("X-Client-Name", options.clientName())
                .header("X-Client-Version", options.clientVersion());
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (licenseKey != null) {
            request.header("X-License-Key", licenseKey);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(JSON.writeValueAsString(body));
        }
        // exchange: we look at the status and body ourselves instead of an exception on 4xx/5xx
        return request.exchange((req, res) -> new Response(res.getStatusCode().value(),
                new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    /** A Checkbox refusal ({"message": "..."}) is shown as is: it says clearly what is wrong (no open shift, sums do not match). */
    private static String ensureSuccess(Response response) {
        if (response.status() >= 200 && response.status() < 300) {
            return response.body();
        }
        String message = null;
        try {
            message = text(JSON.readTree(response.body()), "message");
        } catch (JacksonException e) {
            // not JSON: show the text itself
        }
        throw new DomainException("Checkbox відмовив (" + response.status() + "): "
                + (message == null || message.isEmpty() ? response.body() : message));
    }

    private String baseUrl() {
        return options.baseUrl().replaceAll("/+$", "");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node == null ? null : node.path(name);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.isString() ? value.stringValue() : value.toString();
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DomainException("Перевірку закриття зміни перервано.");
        }
    }

    private record Response(int status, String body) {
    }
}
