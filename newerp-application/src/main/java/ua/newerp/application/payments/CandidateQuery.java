package ua.newerp.application.payments;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Where to take candidates from: orders with such a sum, with such numbers, or with this customer, within a period. */
public record CandidateQuery(List<BigDecimal> amounts, List<Integer> orderIds, String customer, LocalDateTime from, LocalDateTime to,
        int limit) {
}
