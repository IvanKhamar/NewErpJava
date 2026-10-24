package ua.newerp.application.delivery;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.delivery.InternationalShipment;
import ua.newerp.domain.delivery.SeatPreset;
import ua.newerp.domain.delivery.ShipmentDocument;

/**
 * Waybills abroad through Nova Post International, as the old site's "Міжнародна" tab of the waybill dialog. The document is
 * stored with the domestic ones (dd_novaposhta_document) with carrier "international"; deleting goes through ShipmentService.
 */
@Service
@Transactional
public class InternationalShipmentService {

    /** erp_shipment_sender row of an international waybill: there is one sender, from the settings. */
    public static final String SENDER_ID = "international";

    private static final Locale UK = Locale.of("uk", "UA");

    private final ShipmentRepository repository;
    private final NovaPostInternationalClient client;
    private final Clock clock;

    public InternationalShipmentService(ShipmentRepository repository, NovaPostInternationalClient client, Clock clock) {
        this.repository = repository;
        this.client = client;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InternationalFormDto form(int orderId) {
        InternationalOrderInfo order = repository.getInternationalOrderInfo(orderId)
                .orElseThrow(() -> new NotFoundException("Замовлення " + orderId + " не знайдено."));
        // The order has the address in one line: it goes to the street, the building is typed in.
        var recipient = new InternationalShipment.Recipient(order.firstName(), order.lastName(), order.telephone(), order.email(),
                countryCode(order.country()), order.region(), order.city(), order.address(), "", "", order.postCode());
        List<InternationalShipment.Item> items = order.lines().stream()
                .map(l -> new InternationalShipment.Item(l.name(), "", "", 0, BigDecimal.valueOf(l.quantity()), l.total()))
                .toList();
        return new InternationalFormDto(recipient, InternationalShipment.Terms.defaults(), items, order.total(), order.currencyCode(),
                SeatPreset.ALL, client.canCreateDocuments(), client.canCreateDocuments() && !client.isConfigured());
    }

    /** All countries but Ukraine, by their Ukrainian names (the shop's database has no country table of its own). */
    public List<InternationalFormDto.Country> countries() {
        Collator ukrainian = Collator.getInstance(UK);
        return Arrays.stream(Locale.getISOCountries())
                .filter(code -> !code.equals("UA"))
                .map(code -> new InternationalFormDto.Country(code, Locale.of("", code).getDisplayCountry(UK)))
                .sorted(Comparator.comparing(InternationalFormDto.Country::name, ukrainian))
                .toList();
    }

    public List<HsCodeDto> hsCodes(String keyword, String countryCode) {
        if (keyword == null || keyword.strip().length() < 2) {
            return List.of();
        }
        return client.searchHsCodes(keyword.strip(), countryCode == null ? "" : countryCode.strip().toUpperCase());
    }

    public ShipmentDto create(int orderId, CreateInternationalShipmentRequest request, int userId) {
        if (!client.canCreateDocuments()) {
            throw new DomainException("Ключ API Nova Post International не задано, тож міжнародну накладну створити не можна.");
        }
        if (repository.getInternationalOrderInfo(orderId).isEmpty()) {
            throw new NotFoundException("Замовлення " + orderId + " не знайдено.");
        }
        if (repository.getStored(orderId).isPresent()) {
            throw new DomainException("За замовленням уже є ТТН. Щоб створити нову, спершу видаліть стару.");
        }
        InternationalShipment shipment = InternationalShipment.of(orderId, request.recipient(), request.terms(), request.items(),
                request.parcels(), request.declaredCost(), request.currency());
        CreatedShipment created = client.create(shipment);
        var document = new ShipmentDocument(orderId, created.number(), created.cost(), created.ref(), ShipmentDocument.INTERNATIONAL);
        repository.add(document, SENDER_ID, userId, LocalDateTime.now(clock));
        return repository.getForOrder(orderId).orElseThrow();
    }

    /** The ISO code of the order's country, matched by its Ukrainian or English name; empty if unknown or Ukraine. */
    static String countryCode(String country) {
        if (country == null || country.isBlank()) {
            return "";
        }
        String name = country.strip();
        return Arrays.stream(Locale.getISOCountries())
                .filter(code -> !code.equals("UA"))
                .filter(code -> code.equalsIgnoreCase(name) || Locale.of("", code).getDisplayCountry(UK).equalsIgnoreCase(name)
                        || Locale.of("", code).getDisplayCountry(Locale.ENGLISH).equalsIgnoreCase(name))
                .findFirst()
                .orElse("");
    }
}
