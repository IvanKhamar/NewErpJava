package ua.newerp.application.marketing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import ua.newerp.application.common.LookupItem;
import ua.newerp.domain.marketing.GiftVoucher;

/** OpenCart's gift vouchers: dd_voucher, its spending in dd_voucher_history and the pictures in dd_voucher_theme. */
public interface VoucherStore {

    /** Newest first; search finds a code or a recipient or sender name part. */
    List<VoucherRow> list(String search);

    Optional<VoucherRow> find(int id);

    Optional<VoucherRow> findByCode(String code);

    /** Another voucher (not exceptId) already has this code. */
    boolean codeTaken(String code, Integer exceptId);

    int add(GiftVoucher voucher, LocalDateTime now);

    void update(int id, GiftVoucher voucher);

    void delete(int id);

    List<VoucherCardDto.HistoryRow> history(int id);

    List<LookupItem> themes();

    /** The order's total and prepayment, if there is such a placed order. */
    Optional<OrderSums> orderSums(int orderId);

    /** The voucher spent in this order and how much (positive), from dd_voucher_history. */
    Optional<SpentInOrder> spentInOrder(int orderId);

    /**
     * Spends amount of the voucher in the order as OpenCart does it: a history row with a minus, the "voucher" total line with a
     * minus, and the order's total smaller by the amount.
     */
    void redeem(int orderId, int voucherId, String code, BigDecimal amount, LocalDateTime now);

    /** Takes the spending back: the history row and the total line go, the order's total grows back. */
    void release(int orderId, int voucherId, BigDecimal amount);

    record OrderSums(BigDecimal total, BigDecimal prepayment) {
    }

    record SpentInOrder(int voucherId, BigDecimal amount) {
    }

    /**
     * @param spent         the sum of its history rows (negative: spent)
     * @param orderStatusId the status of the order it was bought in; null when orderId is 0
     */
    record VoucherRow(int id, int orderId, String code, String fromName, String fromEmail, String toName, String toEmail, int themeId,
            String themeName, String message, BigDecimal amount, boolean active, LocalDateTime createdAt, BigDecimal spent,
            Integer orderStatusId) {
    }
}
