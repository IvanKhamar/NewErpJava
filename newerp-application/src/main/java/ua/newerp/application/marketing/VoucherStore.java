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

    /**
     * @param spent         the sum of its history rows (negative: spent)
     * @param orderStatusId the status of the order it was bought in; null when orderId is 0
     */
    record VoucherRow(int id, int orderId, String code, String fromName, String fromEmail, String toName, String toEmail, int themeId,
            String themeName, String message, BigDecimal amount, boolean active, LocalDateTime createdAt, BigDecimal spent,
            Integer orderStatusId) {
    }
}
