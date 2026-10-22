package ua.newerp.application.sms;

import java.time.LocalDateTime;

public record SmsDto(int id, int orderId, String phone, String text, String response, LocalDateTime sentAt) {
}
