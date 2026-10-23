package ua.newerp.application.payments;

import org.springframework.stereotype.Service;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.application.orders.OrderDetails;
import ua.newerp.application.orders.OrderQueries;
import ua.newerp.domain.payments.LiqPayPayment;
import ua.newerp.domain.payments.LiqPayState;

/**
 * A link to pay an order by card through LiqPay, and the check whether it is paid.
 * The old site showed the customer the same form on the checkout page; here a manager can send the link at any time.
 */
@Service
public class LiqPayService {

    private final OrderQueries orders;
    private final LiqPayGateway gateway;

    public LiqPayService(OrderQueries orders, LiqPayGateway gateway) {
        this.orders = orders;
        this.gateway = gateway;
    }

    public PaymentLinkDto createLink(int orderId, CreatePaymentLinkRequest request) {
        OrderDetails order = require(orderId);
        var amount = LiqPayPayment.validateAmount(request.amount(), order.total());
        String currency = order.currencyCode() == null || order.currencyCode().isBlank() ? "UAH" : order.currencyCode();
        var checkout = new LiqPayCheckout(String.valueOf(orderId), amount, currency,
                LiqPayPayment.description(orderId, order.customer(), order.telephone()));
        return new PaymentLinkDto(gateway.checkoutUrl(checkout), amount, currency, gateway.isTest());
    }

    public LiqPayStatusDto status(int orderId) {
        require(orderId);
        LiqPayStatusReply reply = gateway.status(String.valueOf(orderId));
        LiqPayState state = LiqPayPayment.interpret(reply.status(), reply.errorCode());
        return new LiqPayStatusDto(state.ordinal(), LiqPayPayment.label(state), reply.amount(),
                state == LiqPayState.PAID ? reply.endDate() : null, gateway.isTest());
    }

    private OrderDetails require(int orderId) {
        return orders.get(orderId).orElseThrow(() -> new NotFoundException("Замовлення " + orderId + " не знайдено."));
    }
}
