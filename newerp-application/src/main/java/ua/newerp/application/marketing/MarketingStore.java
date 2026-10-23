package ua.newerp.application.marketing;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import ua.newerp.domain.marketing.OrderContact;

public interface MarketingStore {

    /** All orders where the birthday day and month are given. */
    List<BirthdayOrder> birthdayOrders();

    /** Numbers from the SMS log that got a greeting since the given time. */
    List<String> birthdaySmsPhones(LocalDateTime since);

    boolean couponExists(String code);

    int addCoupon(CouponDraft coupon);

    /** The id and phone of every placed order (to find a customer's orders by number). */
    List<OrderPhone> orderPhones();

    List<CustomerOrderDto> orders(Collection<Integer> orderIds);

    /** Non-empty values of the field from the chosen sources in the period (raw, not cleaned yet). */
    List<String> contactValues(ContactExportRequest request);

    /** Contact data of all placed orders. */
    List<OrderContact> orderContacts();
}
