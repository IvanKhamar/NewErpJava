package ua.newerp.infrastructure.payments;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import ua.newerp.application.payments.BankStatementClient;
import ua.newerp.application.payments.LiqPayGateway;

/** PrivatBank and LiqPay: the real client only with keys, otherwise a stub that calls nobody. */
@Configuration
@EnableConfigurationProperties({PrivatBankProperties.class, LiqPayProperties.class})
class PaymentsConfig {

    @Bean
    BankStatementClient bankStatementClient(PrivatBankProperties properties) {
        return properties.isConfigured() ? new HttpPrivatBankClient(restClient(Duration.ofSeconds(60)), properties) : new StubBankStatementClient();
    }

    @Bean
    LiqPayGateway liqPayGateway(LiqPayProperties properties) {
        return properties.isConfigured() ? new HttpLiqPayGateway(restClient(Duration.ofSeconds(30)), properties) : new StubLiqPayGateway();
    }

    private static RestClient restClient(Duration timeout) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(factory).build();
    }
}
