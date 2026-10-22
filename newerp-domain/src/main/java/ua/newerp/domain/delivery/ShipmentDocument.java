package ua.newerp.domain.delivery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import ua.newerp.domain.common.Text;

/** Waybill (TTN) of an order (dd_novaposhta_document of the old site). */
public class ShipmentDocument {

    /** Codes after which a waybill no longer changes: received, refused, returned. Tracking skips them. */
    public static final Set<Integer> FINAL_STATUS_CODES = Set.of(9, 11, 102, 103, 104, 105, 107, 108);

    /** The parcel waits at the branch: paid storage start is counted only for these. */
    public static final Set<Integer> WAITING_AT_BRANCH_CODES = Set.of(7, 8);

    public static final String NOVA_POSHTA = "novaposhta";

    private Integer id;
    private int orderId;
    private String carrier = "";
    private String ref = "";
    private String number = "";
    private BigDecimal cost = BigDecimal.ZERO;
    private String status = "";
    private int statusCode;
    private String recipientAddress = "";
    private boolean redelivery;
    private LocalDateTime dateCreated;
    private LocalDate dateScheduled;
    private LocalDate datePayedKeeping;

    protected ShipmentDocument() {
    }

    public ShipmentDocument(int orderId, String number, BigDecimal cost) {
        this(orderId, number, cost, null, NOVA_POSHTA);
    }

    /**
     * @param documentRef Ref of the document in the Nova Poshta API (needed to print and delete); the TTN number if unknown
     */
    public ShipmentDocument(int orderId, String number, BigDecimal cost, String documentRef, String carrier) {
        this.orderId = orderId;
        this.number = number;
        this.ref = documentRef != null ? documentRef : number;
        this.cost = cost;
        this.carrier = carrier;
    }

    public boolean isFinal() {
        return FINAL_STATUS_CODES.contains(statusCode);
    }

    public ShipmentTone getTone() {
        // Switch expression with arrows: no fall-through, returns a value (like a C# switch expression)
        return switch (statusCode) {
            case 9 -> redelivery ? ShipmentTone.INFO : ShipmentTone.SUCCESS;
            case 11 -> redelivery ? ShipmentTone.SUCCESS : ShipmentTone.INFO;
            case 106 -> ShipmentTone.SUCCESS;
            case 3, 107 -> ShipmentTone.ERROR;
            case 102, 103, 105, 108 -> ShipmentTone.WARNING;
            default -> ShipmentTone.INFO;
        };
    }

    /**
     * Applies a tracking response. Like the old site, it updates the record only when the status code changed.
     *
     * @return true if the status changed
     */
    public boolean applyTracking(TrackingInfo info) {
        if (info.statusCode() == statusCode) {
            return false;
        }
        status = Text.truncate(info.status(), 255);
        statusCode = info.statusCode();
        recipientAddress = Text.truncate(info.recipientAddress(), 255);
        redelivery = info.redelivery();
        if (info.dateCreated() != null) {
            dateCreated = info.dateCreated();
        }
        dateScheduled = info.dateScheduled();
        datePayedKeeping = info.datePayedKeeping();
        return true;
    }

    public Integer getId() {
        return id;
    }

    public int getOrderId() {
        return orderId;
    }

    /** Empty or novaposhta = Nova Poshta; international = Nova Post International. */
    public String getCarrier() {
        return carrier;
    }

    public String getRef() {
        return ref;
    }

    /** Waybill number (14 digits at Nova Poshta). */
    public String getNumber() {
        return number;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public String getStatus() {
        return status;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getRecipientAddress() {
        return recipientAddress;
    }

    /** There is a return delivery (e.g. a money transfer for cash on delivery). */
    public boolean isRedelivery() {
        return redelivery;
    }

    public LocalDateTime getDateCreated() {
        return dateCreated;
    }

    public LocalDate getDateScheduled() {
        return dateScheduled;
    }

    public LocalDate getDatePayedKeeping() {
        return datePayedKeeping;
    }
}
