package ua.newerp.application.delivery;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.delivery.NovaPoshtaWarehouse;
import ua.newerp.domain.delivery.SeatPreset;
import ua.newerp.domain.delivery.ShipmentDocument;
import ua.newerp.domain.delivery.ShipmentSeat;
import ua.newerp.domain.sms.SmsMessage;

/**
 * Nova Poshta waybills: create, delete, print, and the branch directory (based on the old site's module).
 * Only delivery to a branch or a parcel locker for now; courier delivery to an address is a separate step.
 * No transaction around a whole method: they call an external API, and a database transaction should not wait for it.
 */
@Service
public class ShipmentService {

    /** How many suggestions to show in city or branch autocomplete. */
    public static final int SUGGESTION_LIMIT = 20;

    /** OpenCart payment code for cash on receipt: for it we suggest cash on delivery. */
    public static final String CASH_ON_DELIVERY_PAYMENT_CODE = "cod";

    private final ShipmentRepository repository;
    private final WarehouseDirectory directory;
    private final NovaPoshtaClient client;
    private final Clock clock;

    public ShipmentService(ShipmentRepository repository, WarehouseDirectory directory, NovaPoshtaClient client, Clock clock) {
        this.repository = repository;
        this.directory = directory;
        this.client = client;
        this.clock = clock;
    }

    public ShipmentFormDto form(int orderId) {
        OrderShippingInfo order = repository.getOrderShippingInfo(orderId)
                .orElseThrow(() -> new NotFoundException("Замовлення " + orderId + " не знайдено."));

        // City and branch are stored as text in the order; if the directory has them, fill them in right away.
        CityDto city = order.city().isBlank() ? null : directory.findCity(order.city().strip()).orElse(null);
        WarehouseDto warehouse = city == null || order.warehouse().isBlank()
                ? null
                : directory.findWarehouse(city.ref(), order.warehouse().strip()).orElse(null);

        return new ShipmentFormDto(
                order.firstName(), order.lastName(), order.telephone(), order.city(), city, order.warehouse(), warehouse,
                order.total(),
                // Cash on delivery, as on the old site, is the total minus the prepayment.
                CASH_ON_DELIVERY_PAYMENT_CODE.equals(order.paymentCode())
                        ? order.total().subtract(order.prepayment()).max(BigDecimal.ZERO)
                        : BigDecimal.ZERO,
                client.senders(), client.dropoffPoints(), SeatPreset.ALL, client.canCreateDocuments(),
                client.canCreateDocuments() && !client.isConfigured());
    }

    public ShipmentDto create(int orderId, CreateShipmentRequest request, int userId) {
        if (!client.canCreateDocuments()) {
            throw new DomainException("Ключ API Нової Пошти не задано, тож ТТН створити не можна.");
        }
        if (repository.getOrderShippingInfo(orderId).isEmpty()) {
            throw new NotFoundException("Замовлення " + orderId + " не знайдено.");
        }
        if (repository.getStored(orderId).isPresent()) {
            throw new DomainException("За замовленням уже є ТТН. Щоб створити нову, спершу видаліть стару.");
        }

        NewShipment shipment = validate(orderId, request);
        CreatedShipment created = client.create(shipment);

        var document = new ShipmentDocument(orderId, created.number(), created.cost(), created.ref(), ShipmentDocument.NOVA_POSHTA);
        repository.add(document, shipment.senderId(), userId, LocalDateTime.now(clock));
        return repository.getForOrder(orderId).orElseThrow();
    }

    /** Deletes the waybill in Nova Poshta first, then here. If Nova Poshta refused, our row stays. */
    public void delete(int orderId) {
        StoredShipment stored = requireStored(orderId);
        if (stored.document().isFinal()) {
            throw new DomainException("Посилку вже отримано чи повернено, таку ТТН видалити не можна.");
        }

        client.delete(senderOf(stored), stored.document().getRef());
        repository.delete(stored.document().getId());
    }

    public PrintedShipment print(int orderId, ShipmentPrintKind kind) {
        StoredShipment stored = requireStored(orderId);
        byte[] pdf = client.print(senderOf(stored), stored.document().getRef(), kind);
        String prefix = kind == ShipmentPrintKind.DOCUMENT ? "ttn" : "marking";
        return new PrintedShipment(pdf, prefix + "-" + stored.document().getNumber() + ".pdf");
    }

    public List<CityDto> searchCities(String search) {
        return search == null || search.isBlank() ? List.of() : directory.searchCities(search.strip(), SUGGESTION_LIMIT);
    }

    public List<WarehouseDto> searchWarehouses(String cityRef, String search) {
        return directory.searchWarehouses(cityRef, search == null ? "" : search.strip(), SUGGESTION_LIMIT);
    }

    /** Loads a fresh branch directory from the API and replaces the local one (and the old site's shop) with it. */
    public WarehouseRefreshResult refreshWarehouses() {
        if (!client.isConfigured()) {
            return new WarehouseRefreshResult(false, 0);
        }

        List<NovaPoshtaWarehouse> warehouses = client.fetchWarehouses();
        // An empty answer is almost surely a failure, not "all branches closed": the directory stays as it is.
        if (warehouses.isEmpty()) {
            throw new DomainException("Нова Пошта повернула порожній довідник відділень, локальний довідник не змінено.");
        }
        directory.replaceAll(warehouses);
        return new WarehouseRefreshResult(true, warehouses.size());
    }

    private NewShipment validate(int orderId, CreateShipmentRequest request) {
        String firstName = name(request.firstName(), "Ім'я");
        String lastName = name(request.lastName(), "Прізвище");
        String phone = SmsMessage.normalizePhone(request.phone());

        if (isBlank(request.cityRef()) || isBlank(request.warehouseRef())) {
            throw new DomainException("Оберіть місто й відділення з довідника.");
        }
        if (!directory.warehouseExists(request.cityRef(), request.warehouseRef())) {
            throw new DomainException("Такого відділення в цьому місті немає в довіднику.");
        }
        if (request.declaredCost().signum() <= 0) {
            throw new DomainException("Оголошена вартість має бути більшою за нуль.");
        }
        if (request.cashOnDelivery().signum() < 0) {
            throw new DomainException("Накладений платіж не може бути від'ємним.");
        }
        if (request.seats().isEmpty()) {
            throw new DomainException("Додайте хоча б одне місце (коробку).");
        }
        if (request.seats().stream().anyMatch(ShipmentService::invalidSeat)) {
            throw new DomainException("Вага місця має бути більшою за нуль, а розміри — не від'ємними.");
        }

        SenderOption sender = pick(client.senders(), request.senderId(), SenderOption::id, SenderOption::isDefault, "відправника");
        DropoffPointOption point = pick(client.dropoffPoints(), request.dropoffPointId(), DropoffPointOption::id,
                DropoffPointOption::isDefault, "місце відправки");

        return new NewShipment(
                sender.id(), point.id(), orderId, LocalDate.now(clock),
                firstName, lastName, request.middleName() == null ? "" : request.middleName().strip(), phone,
                request.cityRef(), request.warehouseRef(), request.declaredCost(), request.cashOnDelivery(), request.senderPays(),
                request.seats());
    }

    private static boolean invalidSeat(ShipmentSeat s) {
        return s.weight() == null || s.width() == null || s.length() == null || s.height() == null
                || s.weight().signum() <= 0 || s.width().signum() < 0 || s.length().signum() < 0 || s.height().signum() < 0;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** As on the old site: Nova Poshta does not accept the apostrophe "`", so it becomes "'". */
    private static String name(String value, String field) {
        if (isBlank(value)) {
            throw new DomainException("Поле «" + field + "» обов'язкове.");
        }
        return value.strip().replace('`', '\'');
    }

    private static <T> T pick(List<T> options, String id, Function<T, String> idOf, Predicate<T> isDefault, String what) {
        if (id != null) {
            return options.stream().filter(o -> idOf.apply(o).equals(id)).findFirst()
                    .orElseThrow(() -> new DomainException("Невідомо " + what + ": " + id + "."));
        }
        return options.stream().filter(isDefault).findFirst()
                .or(() -> options.stream().findFirst())
                .orElseThrow(() -> new DomainException("У налаштуваннях Нової Пошти не задано " + what + "."));
    }

    private StoredShipment requireStored(int orderId) {
        return repository.getStored(orderId).orElseThrow(() -> new NotFoundException("У замовлення " + orderId + " немає ТТН."));
    }

    /** Waybills created on the old site have no sender row: the default sender is used for them. */
    private String senderOf(StoredShipment stored) {
        return stored.senderId() != null
                ? stored.senderId()
                : pick(client.senders(), null, SenderOption::id, SenderOption::isDefault, "відправника").id();
    }
}
