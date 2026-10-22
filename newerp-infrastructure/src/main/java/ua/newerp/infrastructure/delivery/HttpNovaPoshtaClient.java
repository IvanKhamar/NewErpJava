package ua.newerp.infrastructure.delivery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import ua.newerp.application.delivery.CreatedShipment;
import ua.newerp.application.delivery.DropoffPointOption;
import ua.newerp.application.delivery.NewShipment;
import ua.newerp.application.delivery.NovaPoshtaClient;
import ua.newerp.application.delivery.SenderOption;
import ua.newerp.application.delivery.ShipmentPrintKind;
import ua.newerp.application.delivery.TrackingRequest;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.delivery.NovaPoshtaWarehouse;
import ua.newerp.domain.delivery.TrackingInfo;

/**
 * The real Nova Poshta API client (JSON API 2.0). Checked against sample answers in the documented format;
 * not run against a real key yet. Requests are not logged, so the keys (they are in request bodies and in
 * the print URL) never get into the log.
 */
class HttpNovaPoshtaClient implements NovaPoshtaClient {

    private static final int WAREHOUSES_PAGE_SIZE = 500;
    private static final int MAX_WAREHOUSE_PAGES = 1000; // a guard against an endless loop

    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    private static final DateTimeFormatter SHIP_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient http;
    private final NovaPoshtaProperties options;
    private final List<SenderOption> senders;
    private final List<DropoffPointOption> dropoffPoints;

    HttpNovaPoshtaClient(RestClient http, NovaPoshtaProperties options) {
        this.http = http;
        this.options = options;
        for (NovaPoshtaProperties.Sender sender : options.senders()) {
            if (anyBlank(sender.id(), sender.apiKey(), sender.counterpartyRef(), sender.contactRef(), sender.phone())) {
                throw new IllegalStateException("Відправник Нової Пошти \"" + sender.id()
                        + "\": потрібні id, api-key, counterparty-ref, contact-ref і phone.");
            }
        }
        if (options.dropoffPoints().stream().anyMatch(p -> anyBlank(p.id(), p.cityRef(), p.addressRef()))) {
            throw new IllegalStateException("Місце відправки Нової Пошти: потрібні id, city-ref і address-ref.");
        }
        senders = options.senders().stream().map(s -> new SenderOption(s.id(), s.name(), s.isDefault())).toList();
        dropoffPoints = options.dropoffPoints().stream().map(p -> new DropoffPointOption(p.id(), p.name(), p.isDefault())).toList();
    }

    @Override
    public boolean isConfigured() {
        return !anyBlank(options.trackingKey());
    }

    @Override
    public boolean canCreateDocuments() {
        return !senders.isEmpty() && !dropoffPoints.isEmpty();
    }

    @Override
    public List<SenderOption> senders() {
        return senders;
    }

    @Override
    public List<DropoffPointOption> dropoffPoints() {
        return dropoffPoints;
    }

    @Override
    public List<TrackingInfo> track(List<TrackingRequest> documents) {
        if (documents.isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> list = documents.stream()
                .map(d -> Map.of("DocumentNumber", d.number(), "Phone", d.phone()))
                .toList();
        JsonNode data = call(options.trackingKey(), "TrackingDocument", "getStatusDocuments", Map.of("Documents", list));
        return elements(data).stream().map(HttpNovaPoshtaClient::parse).toList();
    }

    @Override
    public CreatedShipment create(NewShipment shipment) {
        NovaPoshtaProperties.Sender sender = senderOptions(shipment.senderId());
        NovaPoshtaProperties.DropoffPoint point = options.dropoffPoints().stream()
                .filter(p -> p.id().equals(shipment.dropoffPointId()))
                .findFirst()
                .orElseThrow(() -> new DomainException("Невідоме місце відправки: " + shipment.dropoffPointId() + "."));

        // 1. The recipient, a private person (if one exists, Nova Poshta returns it), and their contact person.
        Map<String, Object> counterparty = new LinkedHashMap<>();
        counterparty.put("FirstName", shipment.firstName());
        counterparty.put("MiddleName", shipment.middleName());
        counterparty.put("LastName", shipment.lastName());
        counterparty.put("Phone", shipment.phone());
        counterparty.put("Email", "");
        counterparty.put("CounterpartyType", "PrivatePerson");
        counterparty.put("CounterpartyProperty", "Recipient");
        counterparty.put("CityRef", shipment.cityRef());
        JsonNode recipient = call(sender.apiKey(), "Counterparty", "save", counterparty).path(0);
        String recipientRef = text(recipient, "Ref");
        JsonNode contacts = recipient.path("ContactPerson").path("data");
        String contactRef = contacts.isArray() && contacts.size() > 0 ? text(contacts.get(0), "Ref") : "";
        if (recipientRef.isEmpty() || contactRef.isEmpty()) {
            throw new DomainException("Нова Пошта не повернула отримувача чи його контактну особу.");
        }

        // 2. The waybill itself: branch to branch, paid in cash, as on the old site.
        BigDecimal weight = shipment.seats().stream().map(s -> s.weight()).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("PayerType", shipment.senderPays() ? "Sender" : "Recipient");
        properties.put("PaymentMethod", "Cash");
        properties.put("DateTime", shipment.shipDate().format(SHIP_DATE));
        properties.put("CargoType", "Cargo");
        properties.put("ServiceType", "WarehouseWarehouse");
        properties.put("Description", options.cargoDescription());
        properties.put("Cost", number(shipment.declaredCost()));
        properties.put("SeatsAmount", String.valueOf(shipment.seats().size()));
        properties.put("Weight", number(weight));
        properties.put("OptionsSeat", shipment.seats().stream()
                .map(s -> {
                    Map<String, String> seat = new LinkedHashMap<>();
                    seat.put("volumetricVolume", number(s.volume()));
                    seat.put("volumetricWidth", number(s.width()));
                    seat.put("volumetricLength", number(s.length()));
                    seat.put("volumetricHeight", number(s.height()));
                    seat.put("weight", number(s.weight()));
                    return seat;
                })
                .toList());
        properties.put("InfoRegClientBarcodes", String.valueOf(shipment.orderId()));
        properties.put("Sender", sender.counterpartyRef());
        properties.put("ContactSender", sender.contactRef());
        properties.put("SendersPhone", sender.phone());
        properties.put("CitySender", point.cityRef());
        properties.put("SenderAddress", point.addressRef());
        properties.put("Recipient", recipientRef);
        properties.put("ContactRecipient", contactRef);
        properties.put("RecipientsPhone", shipment.phone());
        properties.put("CityRecipient", shipment.cityRef());
        properties.put("RecipientAddress", shipment.warehouseRef());
        // Cash on delivery through "payment control", as on the old site.
        if (shipment.cashOnDelivery().signum() > 0) {
            properties.put("AfterpaymentOnGoodsCost", number(shipment.cashOnDelivery()));
        }

        JsonNode document = call(sender.apiKey(), "InternetDocument", "save", properties).path(0);
        String number = text(document, "IntDocNumber");
        if (number.isEmpty()) {
            throw new DomainException("Нова Пошта не повернула номер ТТН.");
        }
        return new CreatedShipment(text(document, "Ref"), number, decimal(text(document, "CostOnSite")));
    }

    @Override
    public void delete(String senderId, String documentRef) {
        call(senderOptions(senderId).apiKey(), "InternetDocument", "delete", Map.of("DocumentRefs", List.of(documentRef)));
    }

    @Override
    public byte[] print(String senderId, String documentRef, ShipmentPrintKind kind) {
        String method = kind == ShipmentPrintKind.DOCUMENT ? "printDocument" : "printMarkings";
        URI url = URI.create(options.printBaseUrl() + "orders/" + method + "/orders%5B%5D/"
                + URLEncoder.encode(documentRef, StandardCharsets.UTF_8) + "/type/pdf/zebra/zebra/apiKey/"
                + senderOptions(senderId).apiKey());
        // "orders[]" is written as %5B%5D: URI does not allow raw brackets in a path, the server decodes them back.
        // exchange: we look at the status and body ourselves instead of an exception on 4xx/5xx
        byte[] pdf = http.get().uri(url).exchange((request, response) ->
                response.getStatusCode().is2xxSuccessful() ? response.getBody().readAllBytes() : new byte[0]);
        // On an error Nova Poshta returns a page or JSON instead of a PDF; a real PDF starts with "%PDF".
        if (pdf == null || pdf.length < 4 || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F') {
            throw new DomainException("Нова Пошта не віддала PDF для друку. Перевірте, чи ТТН не видалена.");
        }
        return pdf;
    }

    @Override
    public List<NovaPoshtaWarehouse> fetchWarehouses() {
        var result = new ArrayList<NovaPoshtaWarehouse>();
        for (int page = 1; page <= MAX_WAREHOUSE_PAGES; page++) {
            JsonNode data = call(options.trackingKey(), "Address", "getWarehouses", Map.of("Page", page, "Limit", WAREHOUSES_PAGE_SIZE));
            if (data.size() == 0) {
                break;
            }
            elements(data).forEach(item -> result.add(parseWarehouse(item)));
        }
        return result;
    }

    /** Parses one tracking record. Fields come sometimes as strings, sometimes as numbers, and dates in several formats. */
    static TrackingInfo parse(JsonNode item) {
        LocalDateTime scheduled = dateTime(text(item, "ScheduledDeliveryDate"));
        LocalDateTime keeping = dateTime(text(item, "DatePayedKeeping"));
        String redelivery = text(item, "Redelivery");
        return new TrackingInfo(
                text(item, "Number"),
                text(item, "Status"),
                integer(text(item, "StatusCode")),
                text(item, "RecipientAddress"),
                redelivery.equals("1") || redelivery.equals("true"),
                dateTime(text(item, "DateCreated")),
                scheduled == null ? null : scheduled.toLocalDate(),
                keeping == null ? null : keeping.toLocalDate());
    }

    static NovaPoshtaWarehouse parseWarehouse(JsonNode item) {
        return new NovaPoshtaWarehouse(
                text(item, "Ref"),
                text(item, "Description"),
                text(item, "CategoryOfWarehouse"),
                text(item, "CityRef"),
                text(item, "CityDescription"),
                decimal(text(item, "TotalMaxWeightAllowed")).intValue(),
                text(item, "WarehouseStatus").equals("Working"));
    }

    private NovaPoshtaProperties.Sender senderOptions(String senderId) {
        return options.senders().stream()
                .filter(s -> s.id().equals(senderId))
                .findFirst()
                .orElseThrow(() -> new DomainException("Відправника \"" + senderId + "\" немає в налаштуваннях Нової Пошти."));
    }

    /** One API call. Nova Poshta errors become DomainException, so a person sees their text (400). */
    private JsonNode call(String apiKey, String model, String method, Object properties) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("apiKey", apiKey);
        request.put("modelName", model);
        request.put("calledMethod", method);
        request.put("methodProperties", properties);

        String body = http.post()
                .uri(URI.create(options.baseUrl()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(JSON.writeValueAsString(request))
                .retrieve()
                .body(String.class);
        JsonNode root = JSON.readTree(body == null ? "{}" : body);
        if (!root.path("success").asBoolean(false)) {
            throw new DomainException("Нова Пошта відмовила: " + errors(root));
        }
        return root.path("data");
    }

    private static List<JsonNode> elements(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
    }

    // Six digits, so the volume in m³ is not lost (31×36×6 cm = 0.006696).
    private static String number(BigDecimal value) {
        BigDecimal rounded = value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.signum() == 0 ? "0" : rounded.toPlainString();
    }

    private static String text(JsonNode item, String name) {
        JsonNode value = item.path(name);
        if (value.isString()) {
            return value.stringValue();
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.toString();
        }
        return "";
    }

    private static int integer(String value) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value.strip());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static LocalDateTime dateTime(String value) {
        for (DateTimeFormatter format : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(value, format);
            } catch (DateTimeParseException e) {
                // try the next format
            }
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format).atStartOfDay();
            } catch (DateTimeParseException e) {
                // try the next format
            }
        }
        return null;
    }

    private static String errors(JsonNode root) {
        JsonNode errors = root.path("errors");
        return errors.isArray() && errors.size() > 0
                ? elements(errors).stream().map(JsonNode::asString).collect(Collectors.joining("; "))
                : "без пояснення";
    }

    private static boolean anyBlank(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                return true;
            }
        }
        return false;
    }
}
