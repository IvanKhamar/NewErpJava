package ua.newerp.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ua.newerp.domain.common.DomainException;

class PreOrderTest {

    @ParameterizedTest
    @CsvSource({"3, 0, true, IN_STOCK", "0, 5, true, PRE_ORDER", "-2, 5, true, PRE_ORDER", "0, 0, true, SOLD_OUT", "0, 0, false, IN_STOCK"})
    void availabilityFollowsStockThenQuota(int quantity, int quota, boolean subtract, SizeAvailability expected) {
        assertThat(PreOrder.availability(quantity, quota, subtract)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "-2, 5, 0, 3",   // 2 of 5 sold ahead
        "-7, 5, -2, 0",  // oversold past the quota: the quota is 0, the rest stays negative
        "4, 5, 4, 5",    // there is stock: nothing is taken
        "-3, 0, -3, 0"})
    void settleMovesWhatWasSoldAheadOffTheQuota(int quantity, int quota, int expectedQuantity, int expectedQuota) {
        assertThat(PreOrder.settle(quantity, quota)).isEqualTo(new PreOrder.Settled(expectedQuantity, expectedQuota));
    }

    @Test
    void validateQuotaRejectsNegativeAndHuge() {
        PreOrder.validateQuota(0);
        assertThatThrownBy(() -> PreOrder.validateQuota(-1)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> PreOrder.validateQuota(PreOrder.MAX_QUOTA + 1)).isInstanceOf(DomainException.class);
    }
}
