package ua.newerp.infrastructure.delivery;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import ua.newerp.application.delivery.NovaPoshtaClient;

@Configuration
@EnableConfigurationProperties(NovaPoshtaProperties.class)
class DeliveryConfig {

    /**
     * The real Nova Poshta client only when a key is set (in application-local.yml or an environment variable).
     * A @Bean method is the Java version of services.AddSingleton&lt;INovaPoshtaClient&gt;(...) with a factory.
     */
    @Bean
    NovaPoshtaClient novaPoshtaClient(NovaPoshtaProperties properties) {
        String key = properties.trackingKey();
        if (key == null || key.isBlank()) {
            return new StubNovaPoshtaClient(properties.fakeDocuments());
        }
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        return new HttpNovaPoshtaClient(RestClient.builder().requestFactory(factory).build(), properties);
    }
}
