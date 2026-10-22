package ua.newerp.application.sms;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.sms.SmsMessage;

/** A manual SMS to the order's customer (the manager writes the text), as on the old site. */
@Service
@Transactional
public class SmsService {

    private final SmsLog log;
    private final SmsSender sender;
    private final Clock clock;

    public SmsService(SmsLog log, SmsSender sender, Clock clock) {
        this.log = log;
        this.sender = sender;
        this.clock = clock;
    }

    public SendSmsResultDto sendToOrder(int orderId, SendSmsRequest request) {
        String rawPhone = log.getOrderPhone(orderId).orElseThrow(() -> new NotFoundException("Замовлення " + orderId + " не знайдено."));
        String text = SmsMessage.requireText(request.text());
        String phone = SmsMessage.normalizePhone(rawPhone);

        // As on the old site: a waybill number of another order in the text is almost surely a copy-paste mistake.
        if (!request.force()) {
            var foreign = log.findOrderIdsByTtn(SmsMessage.findTtnNumbers(text)).entrySet().stream()
                    .filter(e -> e.getValue() != orderId)
                    .findFirst();
            if (foreign.isPresent()) {
                Map.Entry<String, Integer> e = foreign.get();
                return new SendSmsResultDto(false, null, new TtnMismatchDto(e.getKey(), e.getValue()));
            }
        }

        SmsSendResult result = sender.send(phone, text);

        // The log is written always, even when the service refused: so it is visible the manager tried to reach the customer.
        var message = new SmsMessage(orderId, phone, text, result.response(), LocalDateTime.now(clock));
        log.add(message);
        return new SendSmsResultDto(result.sent(),
                new SmsDto(message.getId(), orderId, phone, text, message.getResponse(), message.getSentAt()), null);
    }
}
