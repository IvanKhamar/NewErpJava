package ua.newerp.infrastructure.sms;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import ua.newerp.application.sms.SmsSender;

/** SMSClub only with a token, otherwise the stub that sends nothing. */
@Configuration
@EnableConfigurationProperties(SmsClubProperties.class)
class SmsConfig {

    @Bean
    SmsSender smsSender(SmsClubProperties properties) {
        if (!properties.isConfigured()) {
            return new StubSmsSender();
        }
        var timeout = Duration.ofSeconds(30);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return new SmsClubSender(RestClient.builder().requestFactory(factory).build(), properties);
    }
}
