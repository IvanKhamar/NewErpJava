package ua.newerp.infrastructure.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ua.newerp.application.delivery.NewShipment;
import ua.newerp.application.delivery.ShipmentPrintKind;
import ua.newerp.application.delivery.TrackingRequest;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.delivery.ShipmentSeat;
import ua.newerp.domain.delivery.TrackingInfo;

/** The client against a fake HTTP server (MockRestServiceServer): no request leaves the test. Keys are invented. */
class HttpNovaPoshtaClientTest {

    static final String API = "https://np.test/json/";

    MockRestServiceServer server;
    HttpNovaPoshtaClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new NovaPoshtaProperties(null, API, "https://print.test/", null, false,
                List.of(new NovaPoshtaProperties.Sender("main", "ФОП Тест", "sender-key", "cp-ref", "contact-ref", "380500000000", true)),
                List.of(new NovaPoshtaProperties.DropoffPoint("kyiv", "Київ №1", "city-sender", "address-sender", true)));
        client = new HttpNovaPoshtaClient(builder.build(), properties);
    }

    @Test
    void usesTheDefaultSendersKeyForTracking() {
        assertThat(client.isConfigured()).isTrue();
        assertThat(client.canCreateDocuments()).isTrue();

        server.expect(requestTo(API)).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"apiKey":"sender-key","modelName":"TrackingDocument","calledMethod":"getStatusDocuments",
                         "methodProperties":{"Documents":[{"DocumentNumber":"20450000000001","Phone":"380671112233"}]}}
                        """))
                .andRespond(withSuccess("""
                        {"success":true,"data":[{"Number":"20450000000001","Status":"Прибув у відділення","StatusCode":"7",
                          "RecipientAddress":"Київ, №2","Redelivery":1,"DateCreated":"18-10-2026 12:30:00",
                          "ScheduledDeliveryDate":"2026-10-20 13:00:00","DatePayedKeeping":"2026-10-27 00:00:00"}]}
                        """, MediaType.APPLICATION_JSON));

        List<TrackingInfo> infos = client.track(List.of(new TrackingRequest("20450000000001", "380671112233")));

        assertThat(infos).containsExactly(new TrackingInfo("20450000000001", "Прибув у відділення", 7, "Київ, №2", true,
                LocalDateTime.of(2026, 10, 18, 12, 30), LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 27)));
        server.verify();
    }

    @Test
    void errorsOfNovaPoshtaBecomeDomainErrors() {
        server.expect(requestTo(API)).andRespond(withSuccess("""
                {"success":false,"data":[],"errors":["API key expired","Second"]}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.track(List.of(new TrackingRequest("1", "2"))))
                .isInstanceOf(DomainException.class)
                .hasMessage("Нова Пошта відмовила: API key expired; Second");
    }

    @Test
    void createsRecipientThenWaybill() {
        server.expect(requestTo(API))
                .andExpect(content().json("""
                        {"apiKey":"sender-key","modelName":"Counterparty","calledMethod":"save",
                         "methodProperties":{"FirstName":"Ірина","LastName":"Шевчук","Phone":"380671112233","CityRef":"city-1"}}
                        """))
                .andRespond(withSuccess("""
                        {"success":true,"data":[{"Ref":"recipient-ref","ContactPerson":{"data":[{"Ref":"person-ref"}]}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(API))
                .andExpect(content().json("""
                        {"modelName":"InternetDocument","calledMethod":"save",
                         "methodProperties":{"PayerType":"Recipient","DateTime":"22.10.2026","Cost":"1500","SeatsAmount":"1",
                           "Weight":"1","OptionsSeat":[{"volumetricVolume":"0.006696","volumetricWidth":"31","weight":"1"}],
                           "InfoRegClientBarcodes":"1001","Sender":"cp-ref","CitySender":"city-sender","Recipient":"recipient-ref",
                           "ContactRecipient":"person-ref","RecipientAddress":"wh-1","AfterpaymentOnGoodsCost":"1500"}}
                        """))
                .andRespond(withSuccess("""
                        {"success":true,"data":[{"Ref":"doc-ref","IntDocNumber":"20450000000009","CostOnSite":85}]}
                        """, MediaType.APPLICATION_JSON));

        var created = client.create(new NewShipment("main", "kyiv", 1001, LocalDate.of(2026, 10, 22), "Ірина", "Шевчук", "",
                "380671112233", "city-1", "wh-1", new BigDecimal("1500"), new BigDecimal("1500"), false,
                List.of(new ShipmentSeat(BigDecimal.ONE, new BigDecimal(31), new BigDecimal(36), new BigDecimal(6)))));

        assertThat(created.ref()).isEqualTo("doc-ref");
        assertThat(created.number()).isEqualTo("20450000000009");
        assertThat(created.cost()).isEqualByComparingTo("85");
        server.verify();
    }

    @Test
    void printAcceptsOnlyPdf() {
        server.expect(requestTo("https://print.test/orders/printMarkings/orders%5B%5D/doc-ref/type/pdf/zebra/zebra/apiKey/sender-key"))
                .andRespond(withSuccess("%PDF-1.4 ...", MediaType.APPLICATION_PDF));
        server.expect(requestTo("https://print.test/orders/printDocument/orders%5B%5D/doc-ref/type/pdf/zebra/zebra/apiKey/sender-key"))
                .andRespond(withSuccess("<html>error</html>", MediaType.TEXT_HTML));

        assertThat(client.print("main", "doc-ref", ShipmentPrintKind.MARKINGS)).startsWith('%', 'P', 'D', 'F');
        assertThatThrownBy(() -> client.print("main", "doc-ref", ShipmentPrintKind.DOCUMENT)).isInstanceOf(DomainException.class);
    }

    @Test
    void fetchesWarehousesPageByPage() {
        server.expect(requestTo(API)).andExpect(content().json("{\"methodProperties\":{\"Page\":1,\"Limit\":500}}"))
                .andRespond(withSuccess("""
                        {"success":true,"data":[
                          {"Ref":"w1","Description":"Відділення №12: вул. Січова","CategoryOfWarehouse":"Branch","CityRef":"c1",
                           "CityDescription":"Київ ","TotalMaxWeightAllowed":"30","WarehouseStatus":"Working"},
                          {"Ref":"w2","Description":"Пункт №3","CategoryOfWarehouse":"Cargo","CityRef":"c1",
                           "CityDescription":"Київ","TotalMaxWeightAllowed":0,"WarehouseStatus":"Working"}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(API)).andExpect(content().json("{\"methodProperties\":{\"Page\":2}}"))
                .andRespond(withSuccess("{\"success\":true,\"data\":[]}", MediaType.APPLICATION_JSON));

        var warehouses = client.fetchWarehouses();

        assertThat(warehouses).hasSize(2);
        assertThat(warehouses.get(0).getSortOrder()).isEqualTo(12);
        assertThat(warehouses.get(0).getCityDescription()).isEqualTo("Київ");
        assertThat(warehouses.get(0).getMaxWeight()).isEqualTo(30);
        assertThat(warehouses.get(0).isStatus()).isTrue();
        assertThat(warehouses.get(1).isStatus()).isFalse();
        server.verify();
    }
}
