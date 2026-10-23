package ua.newerp.infrastructure.cashbox;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import ua.newerp.application.cashbox.CheckboxClient;

@Configuration
@EnableConfigurationProperties(CheckboxProperties.class)
class CashboxConfig {

    /** The real Checkbox client only when some cashier has keys; otherwise the stub, so development never fiscalizes a real sale. */
    @Bean
    CheckboxClient checkboxClient(CheckboxProperties properties) {
        if (!properties.anyKeys()) {
            return new StubCheckboxClient(properties.fakeReceipts());
        }
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        return new HttpCheckboxClient(RestClient.builder().requestFactory(factory).build(), properties, Duration.ofSeconds(1));
    }
}
