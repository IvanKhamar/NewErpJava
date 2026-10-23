package ua.newerp.application.marketing;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.sms.SmsLog;
import ua.newerp.application.sms.SmsSendResult;
import ua.newerp.application.sms.SmsSender;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.marketing.BirthdayCoupon;
import ua.newerp.domain.marketing.BirthdayPeriod;
import ua.newerp.domain.marketing.Birthdays;
import ua.newerp.domain.marketing.ContactExport;
import ua.newerp.domain.marketing.ContactRow;
import ua.newerp.domain.sms.SmsMessage;

/**
 * Marketing, as on the old site: birthday people with a coupon and an SMS greeting (marketing/birthday)
 * and exporting customers' contacts for mailings (marketing/export).
 */
@Service
@Transactional
public class MarketingService {

    private static final int MAX_BULK_PHONES = 500;
    private static final DateTimeFormatter COUPON_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final MarketingStore store;
    private final SmsSender sender;
    private final SmsLog smsLog;
    private final MarketingProperties options;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public MarketingService(MarketingStore store, SmsSender sender, SmsLog smsLog, MarketingProperties options, Clock clock) {
        this.store = store;
        this.sender = sender;
        this.smsLog = smsLog;
        this.options = options;
        this.clock = clock;
    }

    /** The period's birthday people; each number once, with the data of its newest order with a birthday. */
    @Transactional(readOnly = true)
    public List<BirthdayContactDto> birthdays(BirthdayPeriod period) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        Map<String, Long> sent = store.birthdaySmsPhones(today.withDayOfYear(1).atStartOfDay()).stream()
                .map(ContactExport::cleanPhone).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        Map<String, BirthdayOrder> newest = store.birthdayOrders().stream()
                .filter(o -> ContactExport.cleanPhone(o.phone()) != null)
                .collect(Collectors.toMap(o -> ContactExport.cleanPhone(o.phone()), Function.identity(),
                        (a, b) -> b.dateAdded().isAfter(a.dateAdded()) ? b : a));

        Comparator<BirthdayContactDto> order = period == BirthdayPeriod.WEEK
                ? Comparator.comparing(BirthdayContactDto::nextBirthday)
                : Comparator.comparingInt(c -> c.month() * 100 + c.day());
        return newest.entrySet().stream()
                .filter(e -> Birthdays.inPeriod(e.getValue().day(), e.getValue().month(), period, today))
                .map(e -> {
                    BirthdayOrder o = e.getValue();
                    return new BirthdayContactDto(e.getKey(), o.firstName().strip(), o.lastName().strip(), o.email(), city(o), o.instagram(),
                            o.day(), o.month(), Birthdays.next(o.day(), o.month(), today), sent.getOrDefault(e.getKey(), 0L).intValue());
                })
                .sorted(order.thenComparing(BirthdayContactDto::lastName).thenComparing(BirthdayContactDto::firstName))
                .toList();
    }

    /** A new coupon code that is not in dd_coupon yet. */
    @Transactional(readOnly = true)
    public CouponCodeDto newCouponCode() {
        String code;
        do {
            code = BirthdayCoupon.newCode(random);
        } while (store.couponExists(code));
        return new CouponCodeDto(code);
    }

    /** A greeting to one customer: with a code the coupon is made first, then the SMS goes and is logged. */
    public BirthdaySmsResult sendBirthdaySms(BirthdaySmsRequest request) {
        String phone = SmsMessage.normalizePhone(request.phone());
        String text = SmsMessage.requireText(request.text());
        LocalDateTime now = LocalDateTime.now(clock);

        Integer couponId = null;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            String code = BirthdayCoupon.requireCode(request.couponCode());
            if (store.couponExists(code)) {
                throw new DomainException("Купон " + code + " уже є, згенеруй новий код.");
            }
            LocalDate start = now.toLocalDate();
            couponId = store.addCoupon(new CouponDraft(code, "ДН " + phone + " " + now.format(COUPON_DATE), options.birthdayDiscountPercent(),
                    start, start.plusDays(options.birthdayCouponDays()), now));
        }

        SmsSendResult result = send(phone, text, now);
        return new BirthdaySmsResult(result.sent(), couponId, result.response());
    }

    /** The same text to many birthday people (no coupons), with a pause between the SMS. */
    public BulkSmsResult sendBulk(BulkSmsRequest request) {
        String text = SmsMessage.requireText(request.text());
        List<String> phones = request.phones() == null ? List.of() : request.phones();
        if (phones.isEmpty()) {
            throw new DomainException("Немає одержувачів.");
        }
        if (phones.size() > MAX_BULK_PHONES) {
            throw new DomainException("За раз — не більше " + MAX_BULK_PHONES + " одержувачів.");
        }

        int sent = 0;
        int notSent = 0;
        for (String phone : phones.stream().map(ContactExport::cleanPhone).filter(Objects::nonNull).distinct().toList()) {
            if (sent + notSent > 0) {
                pause();
            }
            if (send(phone, text, LocalDateTime.now(clock)).sent()) {
                sent++;
            } else {
                notSent++;
            }
        }
        return new BulkSmsResult(sent, notSent, phones.stream().filter(p -> ContactExport.cleanPhone(p) == null).toList());
    }

    /** A customer's orders by phone number (numbers are compared after bringing them to 380XXXXXXXXX). */
    @Transactional(readOnly = true)
    public List<CustomerOrderDto> customerOrders(String phone) {
        String clean = SmsMessage.normalizePhone(phone);
        List<Integer> ids = store.orderPhones().stream().filter(o -> clean.equals(ContactExport.cleanPhone(o.phone()))).map(OrderPhone::orderId).toList();
        return ids.isEmpty() ? List.of() : store.orders(ids);
    }

    /** A CSV with unique contacts for a mailing. */
    @Transactional(readOnly = true)
    public String exportCsv(ContactExportRequest request) {
        if (request.sources().isEmpty()) {
            throw new DomainException("Вибери, звідки брати контакти.");
        }
        if (request.from() != null && request.to() != null && request.from().isAfter(request.to())) {
            throw new DomainException("Початок періоду пізніший за кінець.");
        }
        List<ContactRow> rows = ContactExport.build(request.field(), store.contactValues(request), store.orderContacts());
        return ContactExport.toCsv(request.field(), rows);
    }

    private SmsSendResult send(String phone, String text, LocalDateTime now) {
        SmsSendResult result = sender.send(phone, text);
        smsLog.add(SmsMessage.birthday(phone, text, result.response(), now));
        return result;
    }

    private void pause() {
        if (options.bulkSmsDelayMilliseconds() > 0) {
            try {
                Thread.sleep(options.bulkSmsDelayMilliseconds());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Розсилку перервано.", e);
            }
        }
    }

    private String city(BirthdayOrder order) {
        if (!order.shippingCity().isBlank()) {
            return order.shippingCity().strip();
        }
        String marker = options.pickupMethodMarker().toLowerCase(Locale.ROOT);
        return order.shippingMethod().toLowerCase(Locale.ROOT).contains(marker) ? options.pickupCity() : "";
    }
}
