package ua.newerp.api.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ua.newerp.IntegrationTest;
import ua.newerp.application.delivery.ShipmentRepository;

/** Without an API key the stub client is used; fake-documents lets it "create" test waybills, as in local development. */
@TestPropertySource(properties = "nova-post-international.fake-documents=true")
@Sql({"/sql/users.sql", "/sql/orders.sql", "/sql/delivery.sql"})
class InternationalShipmentControllerTest extends IntegrationTest {

    static final String CREATE = """
            {"recipient":{"firstName":"Anna","lastName":"Kowalska","phone":"+48 600 100 200","email":"anna@example.com",
                          "countryCode":"PL","city":"Kraków","street":"Floriańska","building":"5","postCode":"31-019"},
             "items":[{"name":"Сукня лляна","nameEng":"Linen dress","hsCode":"6204.49.10","weightGrams":450,"amount":1,"cost":1500}],
             "parcels":[{"weight":1,"width":31,"length":36,"height":6}],
             "declaredCost":1500,"currency":"UAH"}
            """;

    @Autowired
    ShipmentRepository repository;

    MockHttpSession olena;

    @BeforeEach
    void logIn() {
        olena = login("olena");
    }

    @Test
    void formCountriesAndHsCodes() {
        assertThat(mvc.get().uri("/orders/1001/shipment/international/form").session(olena)).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("""
                        {"recipient":{"firstName":"Ірина","lastName":"Шевчук","phone":"+380671112233"},
                         "terms":{"incoterm":"DAP","payerFeesCustoms":"Recipient"},"canCreate":true,"test":true}""");
        assertThat(mvc.get().uri("/orders/999/shipment/international/form").session(olena)).hasStatus(404);

        MvcTestResult countries = mvc.get().uri("/delivery/international/countries").session(olena).exchange();
        assertThat(countries).bodyJson().extractingPath("$[?(@.code == 'PL')].name").asArray().containsExactly("Польща");
        assertThat(countries).bodyJson().extractingPath("$[?(@.code == 'UA')]").asArray().isEmpty();

        assertThat(mvc.get().uri("/delivery/international/hs-codes?search=су&country=PL").session(olena)).bodyJson()
                .extractingPath("$[0].hsCode").isEqualTo("6204491000");
        assertThat(mvc.get().uri("/delivery/international/hs-codes?search=с").session(olena)).bodyJson().extractingPath("$.length()").isEqualTo(0);
    }

    @Test
    void createsATestWaybillThatTrackingSkipsAndDeletesIt() {
        MvcTestResult created = create(1001, CREATE);
        assertThat(created).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"orderId\":1001,\"carrier\":\"international\",\"cost\":1500.0000}");
        assertThat(created).bodyJson().extractingPath("$.number").asString().startsWith("TEST");
        assertThat(mvc.get().uri("/orders/1001/shipment").session(olena)).bodyJson().extractingPath("$.carrier").isEqualTo("international");

        // The domestic tracking job must not ask the domestic API about it.
        assertThat(repository.listOpenForTracking()).noneMatch(s -> s.document().getOrderId() == 1001);

        assertThat(create(1001, CREATE)).hasStatus(400); // one waybill per order
        assertThat(mvc.delete().uri("/orders/1001/shipment").session(olena)).hasStatus(204);
        assertThat(mvc.get().uri("/orders/1001/shipment").session(olena)).hasStatus(204);
    }

    @Test
    void refusesAnIncompleteFormAndOutsiders() {
        assertThat(create(1001, "{\"recipient\":{\"countryCode\":\"UA\"},\"items\":[],\"parcels\":[]}")).hasStatus(400).bodyJson()
                .extractingPath("$.detail").asString().startsWith("Не заповнені або некоректні поля: Прізвище");
        assertThat(create(999, CREATE)).hasStatus(404);
        assertThat(mvc.post().uri("/orders/1001/shipment/international").session(login("keeper")).contentType(MediaType.APPLICATION_JSON)
                .content(CREATE)).hasStatus(403);
    }

    private MvcTestResult create(int orderId, String json) {
        return mvc.post().uri("/orders/" + orderId + "/shipment/international").session(olena).contentType(MediaType.APPLICATION_JSON)
                .content(json).exchange();
    }
}
