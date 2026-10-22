package ua.newerp.domain.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class SmsMessageTest {

    // Like [Theory] + [InlineData] in xUnit: the test runs once per value
    @ParameterizedTest
    @ValueSource(strings = {"+38 (067) 123-45-67", "0671234567", "380671234567"})
    void normalizesUkrainianPhones(String phone) {
        assertThat(SmsMessage.normalizePhone(phone)).isEqualTo("380671234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345", "+48 601 234 567"})
    void rejectsOtherPhones(String phone) {
        assertThatThrownBy(() -> SmsMessage.normalizePhone(phone)).hasMessageContaining("не схожий на український мобільний");
    }

    @Test
    void textRules() {
        assertThat(SmsMessage.requireText("  Привіт ")).isEqualTo("Привіт");
        assertThatThrownBy(() -> SmsMessage.requireText(" ")).hasMessage("Текст SMS порожній.");
        assertThatThrownBy(() -> SmsMessage.requireText("x".repeat(671))).hasMessage("Текст SMS довший за 670 символів.");
    }

    @Test
    void findsTtnNumbers() {
        String text = "ТТН 20450000000001, ще раз 20450000000001, і 204500000000012 (15 цифр), і 20450000000002.";
        assertThat(SmsMessage.findTtnNumbers(text)).containsExactly("20450000000001", "20450000000002");
    }
}
