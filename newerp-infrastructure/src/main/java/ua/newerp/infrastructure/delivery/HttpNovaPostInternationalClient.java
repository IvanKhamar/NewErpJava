package ua.newerp.infrastructure.delivery;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import ua.newerp.application.delivery.CreatedShipment;
import ua.newerp.application.delivery.HsCodeDto;
import ua.newerp.application.delivery.NovaPostInternationalClient;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.delivery.InternationalShipment;

/**
 * Nova Post International REST API (api.novapost.com/v.1.0), as the old site's international_client.php called it. The API key
 * is exchanged for a JWT (GET /clients/authorization), which then goes as the Authorization header as is, without "Bearer".
 * The JWT is kept and taken again once when the API answers 401 (it expired).
 */
class HttpNovaPostInternationalClient implements NovaPostInternationalClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int HS_CODE_SUGGESTIONS = 10;

    private final RestClient http;
    private final NovaPostInternationalProperties options;
    private volatile String jwt;

    HttpNovaPostInternationalClient(RestClient http, NovaPostInternationalProperties options) {
        this.http = http;
        this.options = options;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public boolean canCreateDocuments() {
        return true;
    }

    @Override
    public List<HsCodeDto> searchHsCodes(String keyword, String countryCode) {
        URI uri = UriComponentsBuilder.fromUriString(options.baseUrl() + "/dictionary/classifier")
                .queryParam("country-code", countryCode)
                .queryParam("keyword", keyword)
                .queryParam("fuzzy", "true")
                .queryParam("locale", "uk")
                .queryParam("size", HS_CODE_SUGGESTIONS)
                .encode().build().toUri();
        JsonNode root = authorized(token -> http.get().uri(uri).header("Authorization", token).retrieve().body(String.class));
        List<HsCodeDto> codes = new ArrayList<>();
        for (JsonNode item : elements(root.path("items"))) {
            String local = text(item.path("product"), "currentLocal");
            String english = text(item.path("product"), "en");
            String subCategory = text(item.path("subCategory"), "currentLocal");
            String product = local.isEmpty() ? english : local;
            String label = subCategory.isEmpty() ? product : product.isEmpty() ? subCategory : product + " — " + subCategory;
            codes.add(new HsCodeDto(text(item, "hsCode"), label, english));
        }
        return codes;
    }

    @Override
    public CreatedShipment create(InternationalShipment shipment) {
        String body = JSON.writeValueAsString(payload(shipment));
        JsonNode created = authorized(token -> http.post()
                .uri(URI.create(options.baseUrl() + "/shipments"))
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class));
        String id = text(created, "id");
        String number = text(created, "number");
        if (number.isEmpty()) {
            throw new DomainException("Nova Post International не повернула номер накладної.");
        }
        BigDecimal cost = created.path("cost").isNumber() ? created.path("cost").decimalValue() : shipment.declaredCost();
        return new CreatedShipment(id.isEmpty() ? number : id, number, cost);
    }

    @Override
    public void delete(String shipmentId) {
        authorized(token -> http.delete()
                .uri(UriComponentsBuilder.fromUriString(options.baseUrl() + "/shipments/{id}").buildAndExpand(shipmentId).encode().toUri())
                .header("Authorization", token)
                .retrieve()
                .body(String.class));
    }

    /** The old site's request body, field for field. */
    Map<String, Object> payload(InternationalShipment s) {
        NovaPostInternationalProperties.Sender from = options.sender();
        Map<String, Object> senderAddress = new LinkedHashMap<>();
        senderAddress.put("city", from.city());
        senderAddress.put("street", from.street());
        senderAddress.put("postCode", from.postCode());
        senderAddress.put("building", from.building());
        senderAddress.put("flat", from.flat());
        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("companyTin", from.companyTin());
        sender.put("companyName", from.companyName());
        sender.put("phone", from.phone());
        sender.put("email", from.email());
        sender.put("name", from.name());
        sender.put("countryCode", "UA");
        sender.put("addressParts", senderAddress);
        // Confirmed by Nova Post support to the old site: a company sender also needs registrationAddressParts.
        if (!from.companyTin().isEmpty()) {
            Map<String, Object> registration = new LinkedHashMap<>(senderAddress);
            registration.put("region", "");
            registration.put("note", "");
            sender.put("registrationAddressParts", registration);
        }

        InternationalShipment.Recipient to = s.recipient();
        Map<String, Object> recipientAddress = new LinkedHashMap<>();
        recipientAddress.put("city", to.city());
        recipientAddress.put("region", to.region());
        recipientAddress.put("street", to.street());
        recipientAddress.put("postCode", to.postCode());
        recipientAddress.put("building", to.building());
        recipientAddress.put("flat", to.flat());
        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put("phone", to.phone());
        recipient.put("email", to.email());
        recipient.put("name", (to.firstName() + " " + to.lastName()).strip());
        recipient.put("countryCode", to.countryCode());
        recipient.put("addressParts", recipientAddress);

        List<Map<String, Object>> items = s.items().stream().map(item -> {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("hsCode", item.hsCode());
            line.put("name", item.name());
            line.put("nameEng", item.nameEng());
            line.put("actualWeight", item.weightGrams());
            line.put("measurementCode", "pieces");
            line.put("amount", item.amount());
            line.put("cost", item.cost());
            return line;
        }).toList();
        Map<String, Object> invoice = new LinkedHashMap<>();
        invoice.put("incoterm", s.terms().incoterm());
        invoice.put("exportReason", s.terms().exportReason());
        invoice.put("cost", s.declaredCost());
        invoice.put("currency", s.currency());
        invoice.put("payerFeesCustoms", s.terms().payerFeesCustoms());
        invoice.put("items", items);

        List<Map<String, Object>> parcels = s.parcels().stream().map(p -> {
            Map<String, Object> parcel = new LinkedHashMap<>();
            parcel.put("cargoCategory", s.terms().cargoCategory());
            parcel.put("parcelDescription", s.terms().parcelDescription());
            parcel.put("insuranceCost", p.insuranceCost());
            parcel.put("rowNumber", p.rowNumber());
            parcel.put("width", p.widthMm());
            parcel.put("length", p.lengthMm());
            parcel.put("height", p.heightMm());
            parcel.put("actualWeight", p.weightGrams());
            return parcel;
        }).toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ReadyToShip");
        payload.put("clientOrder", String.valueOf(s.orderId()));
        payload.put("note", "Vidro order #" + s.orderId());
        payload.put("payerType", "Sender");
        payload.put("invoice", invoice);
        payload.put("parcels", parcels);
        payload.put("sender", sender);
        payload.put("recipient", recipient);
        if (s.terms().deliveryType() != null) {
            payload.put("deliveryType", s.terms().deliveryType());
        }
        return payload;
    }

    /** Runs a call with the JWT; on 401 takes a new one and tries once more. Other API errors become a readable DomainException. */
    private JsonNode authorized(Function<String, String> call) {
        try {
            try {
                return parse(call.apply(token(false)));
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) {
                    throw e;
                }
                return parse(call.apply(token(true)));
            }
        } catch (HttpStatusCodeException e) {
            throw new DomainException("Nova Post International відмовила (HTTP " + e.getStatusCode().value() + "): "
                    + errors(e.getResponseBodyAsString()));
        }
    }

    private String token(boolean renew) {
        String current = jwt;
        if (current != null && !renew) {
            return current;
        }
        URI uri = UriComponentsBuilder.fromUriString(options.baseUrl() + "/clients/authorization")
                .queryParam("apiKey", options.apiKey()).encode().build().toUri();
        String body;
        try {
            body = http.get().uri(uri).retrieve().body(String.class);
        } catch (HttpStatusCodeException e) {
            throw new DomainException("Nova Post International не прийняла ключ API (HTTP " + e.getStatusCode().value() + ").");
        }
        String fresh = text(parse(body), "jwt");
        if (fresh.isEmpty()) {
            throw new DomainException("Nova Post International не видала токен: перевірте ключ API.");
        }
        jwt = fresh;
        return fresh;
    }

    private static JsonNode parse(String body) {
        return JSON.readTree(body == null || body.isBlank() ? "{}" : body);
    }

    /** The API's errors: {"errors": {"field": "message"}} or {"error"/"message": "..."}. */
    static String errors(String body) {
        JsonNode root;
        try {
            root = parse(body);
        } catch (RuntimeException e) {
            return body == null || body.isBlank() ? "без пояснення" : body.substring(0, Math.min(body.length(), 300));
        }
        JsonNode errors = root.path("errors");
        if (errors.isObject() && !errors.isEmpty()) {
            return errors.properties().stream()
                    .map(e -> (e.getKey().isEmpty() ? "" : e.getKey() + ": ") + (e.getValue().isString() ? e.getValue().stringValue() : e.getValue().toString()))
                    .collect(Collectors.joining("; "));
        }
        if (errors.isArray() && !errors.isEmpty()) {
            return elements(errors).stream().map(e -> e.isString() ? e.stringValue() : e.toString()).collect(Collectors.joining("; "));
        }
        String message = text(root, "error");
        if (message.isEmpty()) {
            message = text(root, "message");
        }
        return message.isEmpty() ? "без пояснення" : message;
    }

    private static List<JsonNode> elements(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
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
}
