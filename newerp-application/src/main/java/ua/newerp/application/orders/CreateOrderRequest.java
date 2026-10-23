package ua.newerp.application.orders;

import java.math.BigDecimal;
import java.util.List;

/**
 * An order a manager creates by hand. managerId not given: whoever creates it becomes responsible.
 * shippingCost, discount and prepayment may be left out (0).
 */
public record CreateOrderRequest(String firstname, String lastname, String telephone, String email, String instagram,
        int orderSourceId, int statusId, Integer managerId, String shippingCode, String shippingMethod, String city,
        String address1, String address2, String house, String flat, String paymentCode, String paymentMethod,
        List<NewOrderLineRequest> lines, BigDecimal shippingCost, BigDecimal discount, BigDecimal prepayment, String comment) {
}
