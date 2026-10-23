package ua.newerp.api.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import ua.newerp.IntegrationTest;

/** SMS go to the stub here, as in local development. */
@Sql({"/sql/users.sql", "/sql/orders.sql", "/sql/marketing.sql"})
class MarketingControllerTest extends IntegrationTest {

    private static final String PHONE = "380671112233";

    @Autowired
    JdbcClient jdbc;

    MockHttpSession olena;

    @BeforeEach
    void logIn() {
        olena = login("olena");
    }

    @Test
    void birthdayGreetingWithCouponIsLoggedAndCounted() {
        assertThat(mvc.get().uri("/marketing/birthdays?period=Today").session(olena)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                [{"phone":"380671112233","firstName":"Ірина","instagram":"@iryna","sentThisYear":0}]
                """);

        String code = JsonPath.read(body(mvc.post().uri("/marketing/birthdays/coupon-code").session(olena).exchange()), "$.code");
        assertThat(code).matches("DN[0-9A-F]{8}");
        MvcTestResult sent = post("/marketing/birthdays/sms", "{\"phone\":\"%s\",\"text\":\"Вітаємо! Промокод %s\",\"couponCode\":\"%s\"}"
                .formatted(PHONE, code, code));
        assertThat(sent).hasStatusOk().bodyJson().extractingPath("$.sent").isEqualTo(false);
        assertThat(sent).bodyJson().extractingPath("$.couponId").isNotNull();

        Map<String, Object> coupon = jdbc.sql("SELECT type, discount, uses_total, status FROM dd_coupon WHERE code = :code").param("code", code)
                .query().singleRow();
        assertThat(coupon).containsEntry("type", "P").containsEntry("uses_total", 1).containsEntry("status", true);
        assertThat(jdbc.sql("SELECT order_id, phone FROM dd_sms_log WHERE pre_order_id = 1").query().singleRow())
                .containsEntry("order_id", 0).containsEntry("phone", PHONE);

        assertThat(mvc.get().uri("/marketing/birthdays?period=Week").session(olena)).bodyJson().extractingPath("$[0].sentThisYear").isEqualTo(1);
        assertThat(post("/marketing/birthdays/sms", "{\"phone\":\"%s\",\"text\":\"Ще раз\",\"couponCode\":\"%s\"}".formatted(PHONE, code)))
                .hasStatus(400); // the coupon exists already

        assertThat(mvc.get().uri("/marketing/customers/orders?phone=0671112233").session(olena)).bodyJson().isLenientlyEqualTo("""
                [{"orderId":1001,"status":"Нове"}]
                """);
        assertThat(mvc.get().uri("/marketing/birthdays").session(login("keeper"))).hasStatus(403);
    }

    @Test
    void exportMergesSourcesAndFillsNamesFromOrders() {
        MvcTestResult phones = mvc.get().uri("/marketing/contacts.csv?field=Phone&sources=Orders&sources=Questions&from=2026-10-01&to=2026-10-31")
                .session(olena).exchange();
        assertThat(phones).hasStatusOk().hasContentTypeCompatibleWith("text/csv");
        List<String> lines = List.of(body(phones).replace("﻿", "").split("\r\n"));

        assertThat(lines.get(0)).isEqualTo("Ім'я;Прізвище;Місто;Телефон;E-mail");
        assertThat(lines).contains("Ірина;Шевчук;Київ;380671112233;iryna@example.com", ";;;380935551122;")
                .noneMatch(l -> l.contains("380931234567")); // outside the period

        assertThat(body(mvc.get().uri("/marketing/contacts.csv?field=Email&sources=Subscribers").session(olena).exchange()))
                .contains(";;;news@test.ua;");
        assertThat(mvc.get().uri("/marketing/contacts.csv?field=Email").session(olena)).hasStatus(400);
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).session(olena).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
