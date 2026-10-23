package ua.newerp.application.returns;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import ua.newerp.domain.returns.ReturnableLine;

/** The order a return is made from: the customer and the products, with how many of each were already returned. */
public record ReturnSourceOrder(int orderId, String firstname, String lastname, String telephone, String email, int customerId,
        LocalDateTime dateAdded, List<ReturnableLine> lines, Map<Integer, Integer> returned) {
}
