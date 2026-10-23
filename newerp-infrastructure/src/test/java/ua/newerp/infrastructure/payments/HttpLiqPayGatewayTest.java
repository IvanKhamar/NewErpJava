package ua.newerp.infrastructure.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import ua.newerp.application.payments.LiqPayCheckout;
import ua.newerp.application.payments.LiqPayStatusReply;

/** The gateway against a fake HTTP server (MockRestServiceServer). Keys are invented. */
class HttpLiqPayGatewayTest {

    static final LiqPayProperties OPTIONS = new LiqPayProperties(null, "pub-test", "priv-test");
    static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void checkoutUrlHasSignedBase64Data() {
        URI url = URI.create(HttpLiqPayGateway.buildCheckoutUrl(OPTIONS,
                new LiqPayCheckout("2045", new BigDecimal("300.5"), "UAH", "Оплата замовлення #2045.")));

        assertThat(url.getScheme() + "://" + url.getHost() + url.getPath()).isEqualTo("https://www.liqpay.ua/api/3/checkout");
        Map<String, String> query = form(url.getRawQuery());
        JsonNode json = decode(query.get("data"));
        assertThat(json.path("public_key").stringValue()).isEqualTo("pub-test");
        assertThat(json.path("action").stringValue()).isEqualTo("pay");
        assertThat(json.path("version").stringValue()).isEqualTo("3");
        assertThat(json.path("order_id").stringValue()).isEqualTo("2045");
        assertThat(json.path("amount").decimalValue()).isEqualByComparingTo("300.5");
        assertThat(json.path("currency").stringValue()).isEqualTo("UAH");
        assertThat(json.path("description").stringValue()).isEqualTo("Оплата замовлення #2045.");
        assertThat(query.get("signature")).isEqualTo(signature(query.get("data")));
    }

    @Test
    void statusPostsASignedRequestAndParsesAPaidReply() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var body = new String[1];
        server.expect(requestTo("https://www.liqpay.ua/api/request")).andExpect(method(HttpMethod.POST))
                .andExpect(request -> body[0] = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8))
                .andRespond(withSuccess("{\"result\":\"ok\",\"status\":\"success\",\"amount\":300.5,\"end_date\":1792483200000}",
                        MediaType.APPLICATION_JSON));

        LiqPayStatusReply reply = new HttpLiqPayGateway(builder.build(), OPTIONS).status("2045");

        assertThat(reply.status()).isEqualTo("success");
        assertThat(reply.errorCode()).isNull();
        assertThat(reply.amount()).isEqualByComparingTo("300.5");
        assertThat(reply.endDate()).isEqualTo(LocalDateTime.ofInstant(Instant.ofEpochMilli(1792483200000L), ZoneOffset.UTC));
        Map<String, String> form = form(body[0]);
        JsonNode request = decode(form.get("data"));
        assertThat(request.path("action").stringValue()).isEqualTo("status");
        assertThat(request.path("order_id").stringValue()).isEqualTo("2045");
        assertThat(form.get("signature")).isEqualTo(signature(form.get("data")));
        server.verify();
    }

    @Test
    void parseStatusNotFound() {
        assertThat(HttpLiqPayGateway.parseStatus("{\"result\":\"error\",\"err_code\":\"payment_not_found\"}").errorCode())
                .isEqualTo("payment_not_found");
    }

    @Test
    void stubIsTestAndNeverPaid() {
        var stub = new StubLiqPayGateway();
        assertThat(stub.isTest()).isTrue();
        assertThat(stub.checkoutUrl(new LiqPayCheckout("1", BigDecimal.TEN, "UAH", "x")))
                .startsWith("https://www.liqpay.ua/api/3/checkout?data=");
        assertThat(stub.status("1").errorCode()).isEqualTo("payment_not_found");
    }

    private static Map<String, String> form(String text) {
        return Arrays.stream(text.split("&")).map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(p -> p[0], p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
    }

    private static JsonNode decode(String data) {
        return JSON.readTree(new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8));
    }

    private static String signature(String data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1")
                    .digest((OPTIONS.privateKey() + data + OPTIONS.privateKey()).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
