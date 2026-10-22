package ua.newerp.application.sms;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ua.newerp.domain.sms.SmsMessage;

/** SMS log (dd_sms_log) and the order's phone. */
public interface SmsLog {

    /** @return the order's phone, or empty when there is no such order */
    Optional<String> getOrderPhone(int orderId);

    List<SmsDto> listForOrder(int orderId);

    /** Which order each waybill number belongs to (number → order id); unknown numbers are not in the result. */
    Map<String, Integer> findOrderIdsByTtn(Collection<String> numbers);

    void add(SmsMessage message);
}
