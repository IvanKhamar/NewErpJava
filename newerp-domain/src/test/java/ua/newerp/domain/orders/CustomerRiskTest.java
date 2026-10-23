package ua.newerp.domain.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CustomerRiskTest {

    @ParameterizedTest
    @CsvSource(value = {"+38 (067) 123-45-67, 671234567", "0671234567, 671234567", "12345, NULL", "NULL, NULL"}, nullValues = "NULL")
    void phoneKeyIsTheLastNineDigits(String phone, String key) {
        assertThat(CustomerRisk.phoneKey(phone)).isEqualTo(key);
    }

    @Test
    void noOrdersIsANewCustomer() {
        assertThat(CustomerRisk.from(List.of()).level()).isEqualTo(CustomerRiskLevel.NEW);
    }

    @ParameterizedTest
    @CsvSource({"22 22, GREEN, 0", "22 22 19, YELLOW, 33.33", "22 19, RED, 50"})
    void theLevelDependsOnTheCancelledShare(String statusList, CustomerRiskLevel level, String rate) {
        int[] statuses = Arrays.stream(statusList.split(" ")).mapToInt(Integer::parseInt).toArray();
        var risk = CustomerRisk.from(IntStream.range(0, statuses.length).mapToObj(i -> new CustomerOrder(100 + i, statuses[i])).toList());

        assertThat(risk.level()).isEqualTo(level);
        assertThat(risk.suspicionRate()).isEqualByComparingTo(rate);
        assertThat(risk.totalOrders()).isEqualTo(statuses.length);
        assertThat(risk.cancelledOrderIds()).allSatisfy(id -> assertThat(statuses[id - 100]).isEqualTo(CustomerRisk.CANCELLED_STATUS_ID));
    }
}
