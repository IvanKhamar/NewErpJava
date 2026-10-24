package ua.newerp.domain.delivery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ua.newerp.domain.common.DomainException;

/**
 * A Nova Post International waybill as the old site's "Міжнародна" form built it: a recipient abroad, the customs invoice
 * (an HS code, an English name and a weight for every line) and the parcels. Weights of items are in grams, parcel sizes
 * in cm and kg (as {@link ShipmentSeat}); {@link #parcels()} turns them into the API's mm and grams.
 */
public record InternationalShipment(int orderId, Recipient recipient, Terms terms, List<Item> items, List<ShipmentSeat> seats,
        BigDecimal declaredCost, String currency) {

    /** For these countries the API needs the state or region. */
    public static final Set<String> REGION_REQUIRED = Set.of("US", "IE", "CA");

    public record Recipient(String firstName, String lastName, String phone, String email, String countryCode, String region,
            String city, String street, String building, String flat, String postCode) {
    }

    /**
     * The customs and delivery terms; a blank one takes the old site's default.
     *
     * @param deliveryType optional, sent only when chosen
     */
    public record Terms(String incoterm, String exportReason, String payerFeesCustoms, String cargoCategory, String deliveryType,
            String parcelDescription) {

        public Terms {
            incoterm = orDefault(incoterm, "DAP");
            exportReason = orDefault(exportReason, "Selling");
            payerFeesCustoms = orDefault(payerFeesCustoms, "Recipient");
            cargoCategory = orDefault(cargoCategory, "parcel");
            deliveryType = blank(deliveryType) ? null : deliveryType.strip();
            parcelDescription = orDefault(parcelDescription, "Printed apparel / merchandise");
        }

        public static Terms defaults() {
            return new Terms(null, null, null, null, null, null);
        }
    }

    /**
     * A line of the customs invoice.
     *
     * @param weightGrams the weight of the whole line, as the old form asked for it
     */
    public record Item(String name, String nameEng, String hsCode, int weightGrams, BigDecimal amount, BigDecimal cost) {
    }

    /** A parcel as the API wants it: sizes in mm, weight in grams, its share of the declared cost as insurance. */
    public record Parcel(int rowNumber, int widthMm, int lengthMm, int heightMm, int weightGrams, BigDecimal insuranceCost) {
    }

    /** Checks everything the old form checked and lists all that is missing in one message. */
    public static InternationalShipment of(int orderId, Recipient recipient, Terms terms, List<Item> items, List<ShipmentSeat> seats,
            BigDecimal declaredCost, String currency) {
        List<String> missing = new ArrayList<>();
        Recipient r = recipient == null ? new Recipient(null, null, null, null, null, null, null, null, null, null, null) : recipient;
        require(missing, r.lastName(), "Прізвище");
        require(missing, r.firstName(), "Ім'я");
        require(missing, r.phone(), "Телефон");
        require(missing, r.city(), "Населений пункт");
        require(missing, r.street(), "Вулиця");
        require(missing, r.building(), "Будинок");
        require(missing, r.postCode(), "Індекс");
        String country = blank(r.countryCode()) ? "" : r.countryCode().strip().toUpperCase();
        if (!country.matches("[A-Z]{2}") || country.equals("UA")) {
            missing.add("Країна отримувача (не Україна)");
            country = "";
        }
        if (REGION_REQUIRED.contains(country) && blank(r.region())) {
            missing.add("Штат/область (обов'язково для США, Ірландії, Канади)");
        }
        List<Item> lines = items == null ? List.of() : items;
        if (lines.isEmpty()) {
            missing.add("товари в замовленні");
        }
        List<Item> cleanLines = new ArrayList<>();
        for (Item item : lines) {
            String label = blank(item.name()) ? "товар" : item.name();
            String hsCode = item.hsCode() == null ? "" : item.hsCode().replaceAll("\\D", "");
            if (hsCode.isEmpty()) {
                missing.add("код УКТЗЕД (" + label + ")");
            }
            if (blank(item.nameEng())) {
                missing.add("англійська назва товару (" + label + ")");
            }
            if (item.weightGrams() <= 0) {
                missing.add("вага товару в грамах (" + label + ")");
            }
            BigDecimal amount = item.amount() == null || item.amount().signum() <= 0 ? BigDecimal.ONE : item.amount();
            BigDecimal cost = item.cost() == null ? BigDecimal.ZERO : item.cost();
            cleanLines.add(new Item(blank(item.name()) ? "" : item.name().strip(), blank(item.nameEng()) ? "" : item.nameEng().strip(),
                    hsCode, item.weightGrams(), amount, cost));
        }
        if (declaredCost == null || declaredCost.signum() <= 0) {
            missing.add("декларована вартість");
        }
        if (seats == null || seats.isEmpty()) {
            missing.add("параметри місця відправлення (розміри/вага)");
        }
        if (!missing.isEmpty()) {
            throw new DomainException("Не заповнені або некоректні поля: " + String.join(", ", missing) + ".");
        }
        var cleanRecipient = new Recipient(r.firstName().strip(), r.lastName().strip(), r.phone().replaceAll("\\D", ""),
                blank(r.email()) ? "" : r.email().strip(), country, blank(r.region()) ? "" : r.region().strip(), r.city().strip(),
                r.street().strip(), r.building().strip(), blank(r.flat()) ? "" : r.flat().strip(), r.postCode().strip());
        return new InternationalShipment(orderId, cleanRecipient, terms == null ? Terms.defaults() : terms, List.copyOf(cleanLines),
                List.copyOf(seats), declaredCost, blank(currency) ? "UAH" : currency.strip().toUpperCase());
    }

    public int itemsWeightGrams() {
        return items.stream().mapToInt(Item::weightGrams).sum();
    }

    /**
     * The parcels for the API, as the old site counted them: cm × 10 = mm, the declared cost split evenly as insurance, and a
     * single parcel weighs what its items weigh (several parcels: each its own weight in kg × 1000).
     */
    public List<Parcel> parcels() {
        BigDecimal insurance = declaredCost.divide(BigDecimal.valueOf(seats.size()), 2, RoundingMode.HALF_UP);
        List<Parcel> parcels = new ArrayList<>();
        for (int i = 0; i < seats.size(); i++) {
            ShipmentSeat seat = seats.get(i);
            int weight = seats.size() == 1 ? itemsWeightGrams() : grams(seat.weight());
            parcels.add(new Parcel(i + 1, mm(seat.width()), mm(seat.length()), mm(seat.height()), weight, insurance));
        }
        return parcels;
    }

    private static int mm(BigDecimal cm) {
        BigDecimal value = cm == null || cm.signum() <= 0 ? BigDecimal.ONE : cm;
        return value.multiply(BigDecimal.TEN).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private static int grams(BigDecimal kg) {
        return kg == null ? 0 : kg.multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private static void require(List<String> missing, String value, String label) {
        if (blank(value)) {
            missing.add(label);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String orDefault(String value, String fallback) {
        return blank(value) ? fallback : value.strip();
    }
}
