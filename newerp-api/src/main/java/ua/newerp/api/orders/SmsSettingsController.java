package ua.newerp.api.orders;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.sms.SmsSender;
import ua.newerp.application.sms.SmsSettingsDto;

@RestController
@Tag(name = "Замовлення")
class SmsSettingsController {

    private final SmsSender sender;

    SmsSettingsController(SmsSender sender) {
        this.sender = sender;
    }

    @GetMapping("/sms/settings")
    @Operation(summary = "Чи підключено справжній SMS-сервіс (isConfigured = false — працює заглушка)")
    SmsSettingsDto settings() {
        return new SmsSettingsDto(sender.isConfigured());
    }
}
