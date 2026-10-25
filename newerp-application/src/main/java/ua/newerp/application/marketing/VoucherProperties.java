package ua.newerp.application.marketing;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gift vouchers ("vouchers" in application.yml).
 *
 * @param paidStatusIds a voucher bought in an order is usable only once the order is in one of these statuses (OpenCart's
 *                      config_complete_status)
 * @param smsText       the SMS to the recipient; {amount} and {code} are filled in
 */
@ConfigurationProperties("vouchers")
public record VoucherProperties(List<Integer> paidStatusIds, String smsText) {

    public VoucherProperties {
        paidStatusIds = paidStatusIds == null ? List.of(22, 20) : List.copyOf(paidStatusIds);
        smsText = smsText == null || smsText.isBlank()
                ? "Вам подарунковий сертифікат Vidro на {amount} грн. Код: {code}. Назвіть його менеджеру або введіть на сайті."
                : smsText;
    }
}
