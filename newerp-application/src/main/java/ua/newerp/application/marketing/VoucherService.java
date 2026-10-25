package ua.newerp.application.marketing;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.LookupItem;
import ua.newerp.application.common.NotFoundException;
import ua.newerp.application.sms.SmsLog;
import ua.newerp.application.sms.SmsSendResult;
import ua.newerp.application.sms.SmsSender;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.marketing.GiftVoucher;
import ua.newerp.domain.sms.SmsMessage;

/**
 * Gift vouchers as the old site's "Продажі → Подарункові сертифікати", in OpenCart's own tables so the site keeps redeeming
 * them. The old admin emailed a voucher; here it goes by SMS to a phone typed in when sending (dd_voucher has no phone).
 */
@Service
@Transactional
public class VoucherService {

    private static final int CODE_ATTEMPTS = 20;

    private final VoucherStore store;
    private final SmsSender sender;
    private final SmsLog smsLog;
    private final VoucherProperties options;
    private final Clock clock;
    private final RandomGenerator random = RandomGenerator.getDefault();

    public VoucherService(VoucherStore store, SmsSender sender, SmsLog smsLog, VoucherProperties options, Clock clock) {
        this.store = store;
        this.sender = sender;
        this.smsLog = smsLog;
        this.options = options;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<VoucherCardDto> list(String search) {
        return store.list(search == null ? "" : search.strip()).stream().map(r -> card(r, List.of())).toList();
    }

    @Transactional(readOnly = true)
    public VoucherCardDto get(int id) {
        return card(require(id), store.history(id));
    }

    @Transactional(readOnly = true)
    public List<LookupItem> themes() {
        return store.themes();
    }

    public VoucherCardDto create(SaveVoucherRequest request) {
        String code = request.code() == null || request.code().isBlank() ? freeCode() : request.code();
        GiftVoucher voucher = validate(request, code, null);
        return get(store.add(voucher, LocalDateTime.now(clock)));
    }

    /** The amount may change after spending too (the balance follows); the history stays. */
    public VoucherCardDto update(int id, SaveVoucherRequest request) {
        require(id);
        store.update(id, validate(request, request.code(), id));
        return get(id);
    }

    /** Only a voucher nobody spent: the old site deleted the history with it, which loses the record of money. */
    public void delete(int id) {
        require(id);
        if (!store.history(id).isEmpty()) {
            throw new DomainException("Сертифікатом уже платили. Вимкніть його замість видалення, щоб лишилась історія.");
        }
        store.delete(id);
    }

    public SmsSendResult sendSms(int id, String phone) {
        VoucherCardDto voucher = get(id);
        if (!voucher.usable() || voucher.balance().signum() <= 0) {
            throw new DomainException("Сертифікат зараз не можна використати (вимкнений, не оплачений або витрачений), тож SMS не надсилаємо.");
        }
        String number = SmsMessage.normalizePhone(phone);
        String text = SmsMessage.requireText(options.smsText()
                .replace("{amount}", voucher.balance().stripTrailingZeros().toPlainString())
                .replace("{code}", voucher.code()));
        SmsSendResult result = sender.send(number, text);
        smsLog.add(new SmsMessage(voucher.orderId(), number, text, result.response(), LocalDateTime.now(clock)));
        return result;
    }

    private GiftVoucher validate(SaveVoucherRequest r, String code, Integer exceptId) {
        GiftVoucher voucher = GiftVoucher.of(code, r.fromName(), r.fromEmail(), r.toName(), r.toEmail(), r.themeId(), r.message(), r.amount(),
                r.active());
        if (store.codeTaken(voucher.code(), exceptId)) {
            throw new DomainException("Сертифікат з кодом " + voucher.code() + " уже є.");
        }
        return voucher;
    }

    private String freeCode() {
        for (int i = 0; i < CODE_ATTEMPTS; i++) {
            String code = GiftVoucher.newCode(random);
            if (!store.codeTaken(code, null)) {
                return code;
            }
        }
        throw new IllegalStateException("Не вдалося підібрати вільний код сертифіката.");
    }

    private VoucherStore.VoucherRow require(int id) {
        return store.find(id).orElseThrow(() -> new NotFoundException("Сертифікат " + id + " не знайдено."));
    }

    private VoucherCardDto card(VoucherStore.VoucherRow r, List<VoucherCardDto.HistoryRow> history) {
        boolean paid = r.orderStatusId() != null && options.paidStatusIds().contains(r.orderStatusId());
        BigDecimal balance = GiftVoucher.balance(r.amount(), r.spent());
        return new VoucherCardDto(r.id(), r.orderId(), r.code(), r.fromName(), r.fromEmail(), r.toName(), r.toEmail(), r.themeId(), r.themeName(),
                r.message(), r.amount(), balance, r.active(), GiftVoucher.usable(r.active(), r.orderId(), paid), r.createdAt(), history);
    }
}
