package ua.newerp.api.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

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
import ua.newerp.domain.orders.CustomerRiskLevel;

@Sql({"/sql/users.sql", "/sql/stock.sql", "/sql/production.sql", "/sql/orders.sql", "/sql/new-order.sql"})
class NewOrderControllerTest extends IntegrationTest {

    @Autowired
    JdbcClient jdbc;

    MockHttpSession olena;

    @BeforeEach
    void logIn() {
        olena = login("olena");
    }

    @Test
    void createWritesTheOrderLinesOptionsAndStatus() {
        MvcTestResult created = post("/orders", order("+380501112233", 1, 2));
        assertThat(created).hasStatus(201).bodyJson().isLenientlyEqualTo("{\"warnings\":[],\"risk\":{\"level\":%d}}"
                .formatted(CustomerRiskLevel.NEW.ordinal()));
        int id = orderId(created);
        assertThat(created).headers().hasValue("Location", "/orders/" + id);

        assertThat(mvc.get().uri("/orders/" + id).session(olena)).bodyJson().isLenientlyEqualTo("""
                {"total":1700.0000,"statusId":1,"customer":"Ганна Нова","sourceName":"Instagram","managerId":7,
                 "products":[{"name":"Сорочка лляна оверсайз","model":"T-2","quantity":2,"total":1800.0000}],
                 "statusHistory":[{"oldStatusId":0,"newStatusId":1}]}
                """);

        // The new order is in the customer search at once
        assertThat(mvc.get().uri("/orders/new/customers?search=0501112233").session(olena)).bodyJson()
                .isLenientlyEqualTo("[{\"firstname\":\"Ганна\",\"instagram\":\"@hanna\",\"lastOrderId\":%d}]".formatted(id));
    }

    @Test
    void anInvalidOrderIs400() {
        assertThat(post("/orders", order("+380501112244", 1, 0))).hasStatus(400);
    }

    @Test
    void productSearchGivesThePriceAndSizes() {
        assertThat(mvc.get().uri("/orders/new/products?search=лляна").session(olena)).bodyJson().isLenientlyEqualTo("""
                [{"id":60,"price":900.0000,"options":[{"productOptionValueId":602,"optionName":"Розмір","value":"M"}]}]
                """);
    }

    @Test
    void customerRiskFindsACancelledOrderByPhoneInAnyFormat() {
        assertThat(mvc.get().uri("/orders/customer-risk?telephone=0935554433").session(olena)).bodyJson().isLenientlyEqualTo(
                "{\"level\":%d,\"totalOrders\":1,\"cancelledOrders\":1,\"cancelledOrderIds\":[1005]}".formatted(CustomerRiskLevel.RED.ordinal()));
    }

    @Test
    void methodsComeFromExistingOrders() {
        assertThat(mvc.get().uri("/orders/new/methods").session(olena)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.payment[?(@.code == 'cod')].name").asArray().containsExactly("Накладений платіж");
            assertThat(json).extractingPath("$.shipping[?(@.code == 'novaposhta.warehouse')].name").asArray().containsExactly("Нова Пошта");
        });
    }

    @Test
    void aStorekeeperCannotCreateOrders() {
        MockHttpSession keeper = login("keeper");
        assertThat(mvc.post().uri("/orders").session(keeper).contentType(MediaType.APPLICATION_JSON).content(order("+380501112255", 1, 2)))
                .hasStatus(403);
    }

    @Test
    void aNewCompositionReplacesTheLinesAndTotalsAndRecountsSewnStock() {
        // A shipped order (status 20 writes sewn pieces off) takes one shirt of size M
        int id = orderId(post("/orders", order("+380501112266", 20, 1)));
        assertThat(sewnM()).isEqualByComparingTo("4");

        assertThat(mvc.get().uri("/orders/%d/composition".formatted(id)).session(olena)).bodyJson().isLenientlyEqualTo("""
                {"statusId":20,"lines":[{"productId":60,"productOptionValueId":602,"option":"M","quantity":1,"price":900.0000,
                 "options":[{"productOptionValueId":602}]}],"shippingCost":0.0000,"discount":100.0000,"prepayment":200.0000,"total":800.0000}
                """);

        // The customer wants 3 shirts and paid shipping: the order gives 1 back and takes 3
        assertThat(put("/orders/%d/composition".formatted(id), """
                {"lines":[{"productId":60,"productOptionValueId":602,"quantity":3,"price":900}],
                 "shippingCost":70,"discount":100,"prepayment":500}
                """)).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"total\":2670,\"warnings\":[]}");
        assertThat(sewnM()).isEqualByComparingTo("2");

        assertThat(mvc.get().uri("/orders/%d/composition".formatted(id)).session(olena)).bodyJson().isLenientlyEqualTo("""
                {"lines":[{"quantity":3}],"shippingCost":70.0000,"discount":100.0000,"prepayment":500.0000,"total":2670.0000}
                """);
        assertThat(mvc.get().uri("/orders/" + id).session(olena)).bodyJson()
                .isLenientlyEqualTo("{\"total\":2670.0000,\"statusId\":20,\"statusHistory\":[{\"newStatusId\":20}]}");

        assertThat(put("/orders/%d/composition".formatted(id), "{\"lines\":[{\"productId\":60,\"productOptionValueId\":602,\"quantity\":0,\"price\":900}]}"))
                .hasStatus(400);
        // A product with sizes needs one
        assertThat(put("/orders/%d/composition".formatted(id), "{\"lines\":[{\"productId\":60,\"quantity\":1,\"price\":900}]}"))
                .hasStatus(400).bodyText().contains("Оберіть розмір");
        assertThat(mvc.get().uri("/orders/9999/composition").session(olena)).hasStatus(404);
        assertThat(mvc.get().uri("/orders/%d/composition".formatted(id)).session(login("keeper"))).hasStatus(403);
    }

    private static String order(String phone, int statusId, int quantity) {
        return """
                {"firstname":"Ганна","lastname":"Нова","telephone":"%s","email":"","instagram":"@hanna","orderSourceId":2,
                 "statusId":%d,"managerId":7,"shippingCode":"novaposhta.warehouse","shippingMethod":"Нова Пошта","city":"Київ",
                 "address1":"","address2":"Відділення №1","house":"","flat":"","paymentCode":"cod","paymentMethod":"Накладений платіж",
                 "lines":[{"productId":60,"productOptionValueId":602,"quantity":%d,"price":900}],
                 "discount":100,"prepayment":200,"comment":"Дзвінок"}
                """.formatted(phone, statusId, quantity);
    }

    private BigDecimal sewnM() {
        return jdbc.sql("SELECT sewn_stock FROM dd_stock_production_sizes WHERE size_id = 2").query(BigDecimal.class).single();
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).session(olena).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult put(String uri, String json) {
        return mvc.put().uri(uri).session(olena).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static int orderId(MvcTestResult result) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.orderId");
        } catch (UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
