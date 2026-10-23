package ua.newerp.infrastructure.payments;

import ua.newerp.application.payments.LiqPayCheckout;
import ua.newerp.application.payments.LiqPayGateway;
import ua.newerp.application.payments.LiqPayStatusReply;

/**
 * The stub without keys: builds a link of the same format with a test key (LiqPay will not accept it)
 * and always answers that there is no payment yet. Never calls LiqPay.
 */
class StubLiqPayGateway implements LiqPayGateway {

    private static final LiqPayProperties TEST_OPTIONS = new LiqPayProperties(null, "sandbox_newerp_test", "sandbox_newerp_test");

    @Override
    public boolean isTest() {
        return true;
    }

    @Override
    public String checkoutUrl(LiqPayCheckout checkout) {
        return HttpLiqPayGateway.buildCheckoutUrl(TEST_OPTIONS, checkout);
    }

    @Override
    public LiqPayStatusReply status(String orderId) {
        return new LiqPayStatusReply(null, "payment_not_found", null, null);
    }
}
