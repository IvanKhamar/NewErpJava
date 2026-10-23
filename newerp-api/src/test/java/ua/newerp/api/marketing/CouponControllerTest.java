package ua.newerp.api.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import ua.newerp.IntegrationTest;

@Sql({"/sql/users.sql", "/sql/orders.sql", "/sql/coupons.sql"})
class CouponControllerTest extends IntegrationTest {

    MockHttpSession olena;

    @BeforeEach
    void logIn() {
        olena = login("olena");
    }

    @Test
    void listShowsUsageFromPlacedOrdersAndSurvivesZeroDates() {
        assertThat(mvc.get().uri("/marketing/coupons").session(olena)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                [{"id":2,"code":"OLD5","start":null,"end":null,"used":0,"state":"Disabled"},
                 {"id":1,"code":"AUTUMN10","type":"P","minTotal":500.0000,"usesPerCustomer":1,"used":1,"discountGiven":150.0000}]
                """);
        assertThat(mvc.get().uri("/marketing/coupons?state=Disabled").session(olena)).bodyJson().extractingPath("$.length()").isEqualTo(1);
        assertThat(mvc.get().uri("/marketing/coupons/9").session(olena)).hasStatus(404);
        assertThat(mvc.get().uri("/marketing/coupons").session(login("keeper"))).hasStatus(403);
    }

    @Test
    void createsChangesAndTurnsOffACoupon() {
        String winter = """
                {"name":"Зима","code":"winter200","type":"F","discount":200,"minTotal":1000,"start":"2026-12-01","end":"2027-02-28",
                 "usesTotal":50,"usesPerCustomer":1,"active":true}
                """;
        MvcTestResult created = send(mvc.post().uri("/marketing/coupons"), winter);
        assertThat(created).hasStatus(201).bodyJson().isLenientlyEqualTo("""
                {"code":"WINTER200","type":"F","state":"NotStarted","used":0}
                """);
        assertThat(send(mvc.post().uri("/marketing/coupons"), winter)).hasStatus(400); // the code is taken

        int id = JsonPath.read(new String(created.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.id");
        assertThat(send(mvc.put().uri("/marketing/coupons/" + id), winter.replace("\"active\":true", "\"active\":false")))
                .hasStatusOk().bodyJson().extractingPath("$.state").isEqualTo("Disabled");
        assertThat(send(mvc.put().uri("/marketing/coupons/" + id), winter.replace("winter200", "AUTUMN10"))).hasStatus(400);
        assertThat(send(mvc.post().uri("/marketing/coupons"), winter.replace("\"type\":\"F\"", "\"type\":\"P\"")
                .replace("winter200", "BIG")))
                .hasStatus(400); // a code of 3 characters
        assertThat(send(mvc.post().uri("/marketing/coupons"), winter.replace("\"type\":\"F\"", "\"type\":\"P\"")
                .replace("winter200", "BIGSALE")))
                .hasStatus(400); // 200 percent
    }

    private MvcTestResult send(MockMvcRequestBuilder request, String json) {
        return request.session(olena).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }
}
