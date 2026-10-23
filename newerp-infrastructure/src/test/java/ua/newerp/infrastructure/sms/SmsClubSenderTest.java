package ua.newerp.infrastructure.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ua.newerp.application.sms.SmsSendResult;

/** The sender against a fake HTTP server. Token, message id and numbers are invented. */
class SmsClubSenderTest {

    // A reply of SMSClub API v2 to a successful send.
    static final String ACCEPTED = "{\"success_request\":{\"info\":{\"1092\":\"380670000001\"}}}";

    @Test
    void postsJsonWithBearerTokenAndReadsTheAcceptedNumber() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://im.smsclub.mobi/sms/send")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(jsonPath("$.phone[0]").value("380670000001"))
                .andExpect(jsonPath("$.message").value("Ваше замовлення прийнято"))
                .andExpect(jsonPath("$.src_addr").value("Vidro.ua"))
                .andRespond(withSuccess(ACCEPTED, MediaType.APPLICATION_JSON));

        SmsSendResult result = new SmsClubSender(builder.build(), options("test-token", List.of()))
                .send("380670000001", "Ваше замовлення прийнято");

        assertThat(result).isEqualTo(new SmsSendResult(true, ACCEPTED));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"success_request\":{\"info\":[],\"add_info\":{\"380670000001\":\"Неправильний номер\"}}}",
        "{\"error\":\"Unauthorized\"}",
        "not json"})
    void aRejectedOrUnknownReplyIsNotSentButKeepsTheResponse(String reply) {
        var builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build().expect(requestTo("https://im.smsclub.mobi/sms/send"))
                .andRespond(withSuccess(reply, MediaType.APPLICATION_JSON));

        SmsSendResult result = new SmsClubSender(builder.build(), options("t", List.of())).send("380670000001", "Текст");

        assertThat(result).isEqualTo(new SmsSendResult(false, reply));
    }

    @Test
    void aPhoneOutsideOnlyPhonesIsNotRequested() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();

        SmsSendResult result = new SmsClubSender(builder.build(), options("t", List.of("380990000000"))).send("380670000001", "Текст");

        assertThat(result).isEqualTo(new SmsSendResult(false, SmsClubSender.NOT_ALLOWED_RESPONSE));
        server.verify();
    }

    @Test
    void anUnreachableServiceIsNotSent() {
        var builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build().expect(requestTo("https://im.smsclub.mobi/sms/send"))
                .andRespond(request -> {
                    throw new IOException("connection refused");
                });

        SmsSendResult result = new SmsClubSender(builder.build(), options("t", List.of())).send("380670000001", "Текст");

        assertThat(result.sent()).isFalse();
        assertThat(result.response()).startsWith("НЕ НАДІСЛАНО: SMSClub недоступний (");
    }

    private static SmsClubProperties options(String token, List<String> onlyPhones) {
        return new SmsClubProperties(null, token, null, onlyPhones);
    }
}
