package ua.newerp.application.orders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import ua.newerp.domain.orders.CustomerOrder;
import ua.newerp.domain.orders.OrderDraft;

/** What creating an order by hand needs: products, customers, shipping and payment methods. */
public interface NewOrderStore {

    /** Enabled products whose name or model contains term (or with that id). */
    List<ProductPickDto> searchProducts(String term, int limit);

    List<ProductPickDto> getProducts(Collection<Integer> productIds);

    /** Customers from previous orders whose first name, last name, phone or email contains term. */
    List<CustomerPickDto> searchCustomers(String term, int limit);

    /** Orders since the given time that match the customer by phone (last 9 digits), by first and last name together, or by email. */
    List<CustomerOrder> findCustomerOrders(String phoneKey, String firstname, String lastname, String email, LocalDateTime since);

    /** Shipping and payment methods already used in orders (most frequent first). */
    OrderMethodsDto listMethods();

    boolean sourceExists(int sourceId);

    /** Writes the order without a status (order_status_id = 0), its products, options and totals. @return the new order's id */
    int create(NewOrderRecord order);

    Optional<OrderCompositionDto> getComposition(int orderId);

    /**
     * Replaces the order's products, options and totals, and updates the total and the prepayment. A gift voucher spent in the
     * order (its "voucher" total line) stays and is taken off the total.
     */
    void replaceLines(int orderId, OrderDraft draft, List<NewOrderLineRecord> lines, LocalDateTime now);

    /** What gift vouchers paid of the order (positive), from its "voucher" total lines. */
    BigDecimal voucherPaid(int orderId);
}
