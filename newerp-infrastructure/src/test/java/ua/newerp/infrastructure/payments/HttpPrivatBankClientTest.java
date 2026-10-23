package ua.newerp.infrastructure.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ua.newerp.application.payments.BankTransaction;
import ua.newerp.domain.common.DomainException;

/** The Autoclient statement format, as the old site read it (checkBusinessCards). The data is invented. */
class HttpPrivatBankClientTest {

    static final String STATEMENT = """
            {"StatementsResponse": {"statements": [
              {"UA213052990000026001000000001": [
                {"0": {"ID": "tx-1", "TRANTYPE": "C", "BPL_SUM_E": "1500.00", "BPL_DAT_KL": "15.10.2026", "BPL_TIM_P": "14:05",
                       "BPL_OSND": "Оплата зам. 2045", "BPL_A_NAM": "Іваненко Ірина", "BPL_A_ACC": "UA11", "BPL_A_MFO": "305299", "BPL_A_CRF": "1234567890", "BPL_C_BAL": "9000.50"}},
                {"1": {"ID": "tx-2", "TRANTYPE": "D", "BPL_SUM_E": "200", "BPL_DAT_KL": "15.10.2026", "BPL_TIM_P": "15:00", "BPL_OSND": "Комісія"}}
              ]}
            ]}}
            """;

    @Test
    void parseReadsIncomingAndDebit() {
        List<BankTransaction> transactions = HttpPrivatBankClient.parse(STATEMENT);

        assertThat(transactions).hasSize(2);
        BankTransaction t = transactions.get(0);
        assertThat(t.id()).isEqualTo("tx-1");
        assertThat(t.account()).isEqualTo("UA213052990000026001000000001");
        assertThat(t.isIncoming()).isTrue();
        assertThat(t.amount()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(t.balance()).isEqualByComparingTo(new BigDecimal("9000.5"));
        assertThat(t.date()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(t.time()).isEqualTo(LocalTime.of(14, 5));
        assertThat(t.purpose()).isEqualTo("Оплата зам. 2045");
        assertThat(t.payer()).isEqualTo("Іваненко Ірина");
        assertThat(t.payerBankCode()).isEqualTo("305299");
        assertThat(transactions.get(1).isIncoming()).isFalse();
    }

    @Test
    void fetchSendsIdAndTokenHeadersForEachAccountAndDecodesWindows1251() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new HttpPrivatBankClient(builder.build(),
                new PrivatBankProperties(null, List.of(new PrivatBankProperties.Account("client-id", "secret"))));
        server.expect(requestTo("https://acp.privatbank.ua/api/proxy/transactions?startDate=15-10-2026&endDate=16-10-2026"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("id", "client-id"))
                .andExpect(header("token", "secret"))
                .andRespond(withSuccess(STATEMENT.getBytes(Charset.forName("windows-1251")), MediaType.APPLICATION_JSON));

        List<BankTransaction> transactions = client.fetch(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16));

        assertThat(transactions.get(0).purpose()).isEqualTo("Оплата зам. 2045");
        server.verify();
    }

    @Test
    void unknownFormatIsADomainError() {
        assertThatThrownBy(() -> HttpPrivatBankClient.parse("{\"error\": \"Unauthorized\"}")).isInstanceOf(DomainException.class);
    }
}
