package ua.newerp.api.marketing;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.marketing.BirthdayContactDto;
import ua.newerp.application.marketing.BirthdaySmsRequest;
import ua.newerp.application.marketing.BirthdaySmsResult;
import ua.newerp.application.marketing.BulkSmsRequest;
import ua.newerp.application.marketing.BulkSmsResult;
import ua.newerp.application.marketing.ContactExportRequest;
import ua.newerp.application.marketing.CouponCodeDto;
import ua.newerp.application.marketing.CustomerOrderDto;
import ua.newerp.application.marketing.MarketingService;
import ua.newerp.domain.common.EnumParam;
import ua.newerp.domain.marketing.BirthdayPeriod;
import ua.newerp.domain.marketing.ContactField;
import ua.newerp.domain.marketing.ContactSource;

/** Birthday greetings with coupons and the contact export (Admin and Manager, see SecurityConfig). */
@RestController
@RequestMapping("/marketing")
@Tag(name = "Маркетинг")
class MarketingController {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=utf-8");
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm");

    private final MarketingService service;
    private final Clock clock;

    MarketingController(MarketingService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/birthdays")
    @Operation(summary = "Іменинники періоду (Today, Tomorrow, Week, Month, All) з даними останнього замовлення й кількістю привітань цього року")
    List<BirthdayContactDto> birthdays(@RequestParam(required = false) String period) {
        return service.birthdays(EnumParam.parse(BirthdayPeriod.class, period, BirthdayPeriod.TODAY));
    }

    @PostMapping("/birthdays/coupon-code")
    @Operation(summary = "Новий код купона DN…, якого ще немає (сам купон створюється разом із SMS)")
    CouponCodeDto couponCode() {
        return service.newCouponCode();
    }

    @PostMapping("/birthdays/sms")
    @Operation(summary = "Привітання одному клієнту; з кодом — спершу купон зі знижкою на birthday-coupon-days днів")
    BirthdaySmsResult birthdaySms(@RequestBody BirthdaySmsRequest request) {
        return service.sendBirthdaySms(request);
    }

    @PostMapping("/birthdays/sms-bulk")
    @Operation(summary = "Однаковий текст багатьом номерам (до 500), без купонів")
    BulkSmsResult bulkSms(@RequestBody BulkSmsRequest request) {
        return service.sendBulk(request);
    }

    @GetMapping("/customers/orders")
    @Operation(summary = "Замовлення клієнта за телефоном")
    List<CustomerOrderDto> customerOrders(@RequestParam String phone) {
        return service.customerOrders(phone);
    }

    @GetMapping("/contacts.csv")
    @Operation(summary = "Унікальні телефони чи e-mail клієнтів (джерела: Orders, Questions, Subscribers) за період, CSV для Excel")
    ResponseEntity<byte[]> contacts(@RequestParam String field, @RequestParam(required = false) List<String> sources,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        ContactField contactField = EnumParam.parse(ContactField.class, field, ContactField.PHONE);
        var set = EnumSet.noneOf(ContactSource.class);
        if (sources != null) {
            sources.forEach(s -> set.add(EnumParam.parse(ContactSource.class, s, null)));
        }
        byte[] csv = service.exportCsv(new ContactExportRequest(contactField, set, from, to)).getBytes(StandardCharsets.UTF_8);
        // Excel opens a UTF-8 CSV correctly only with a BOM at the start.
        var body = new byte[UTF8_BOM.length + csv.length];
        System.arraycopy(UTF8_BOM, 0, body, 0, UTF8_BOM.length);
        System.arraycopy(csv, 0, body, UTF8_BOM.length, csv.length);
        String name = "contacts-" + (contactField == ContactField.PHONE ? "phones" : "emails") + "-" + LocalDateTime.now(clock).format(FILE_TIME)
                + ".csv";
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(body);
    }
}
