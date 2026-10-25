package ua.newerp.application.orders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.orders.CustomerRisk;
import ua.newerp.domain.orders.OrderDraft;
import ua.newerp.domain.orders.OrderDraftLine;

/**
 * A manager creates an order quickly (an order by phone or from social networks), like the old site's "Quick order" form,
 * and checks whether the customer cancelled previous orders.
 */
@Service
@Transactional
public class NewOrderService {

    private static final int SEARCH_LIMIT = 10;

    private final NewOrderStore store;
    private final OrderStore orders;
    private final OrderService orderService;
    private final Clock clock;

    public NewOrderService(NewOrderStore store, OrderStore orders, OrderService orderService, Clock clock) {
        this.store = store;
        this.orders = orders;
        this.orderService = orderService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProductPickDto> searchProducts(String term) {
        return term == null || term.isBlank() ? List.of() : store.searchProducts(term.strip(), SEARCH_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<CustomerPickDto> searchCustomers(String term) {
        return term == null || term.strip().length() < 3 ? List.of() : store.searchCustomers(term.strip(), SEARCH_LIMIT);
    }

    @Transactional(readOnly = true)
    public OrderMethodsDto listMethods() {
        return store.listMethods();
    }

    @Transactional(readOnly = true)
    public CustomerRiskDto checkRisk(CustomerRiskQuery query) {
        String phoneKey = CustomerRisk.phoneKey(query.telephone());
        String firstname = strip(query.firstname());
        String lastname = strip(query.lastname());
        boolean hasName = !firstname.isEmpty() && !lastname.isEmpty();
        String email = strip(query.email());

        if (phoneKey == null && !hasName && email.isEmpty()) {
            return CustomerRiskDto.of(CustomerRisk.from(List.of()));
        }
        LocalDateTime since = now().minusYears(CustomerRisk.LOOKBACK_YEARS);
        var found = store.findCustomerOrders(phoneKey, hasName ? firstname : null, hasName ? lastname : null,
                email.isEmpty() ? null : email, since);
        return CustomerRiskDto.of(CustomerRisk.from(found));
    }

    public CreateOrderResult create(CreateOrderRequest request, int userId) {
        List<OrderDraftLine> lines = draftLines(request.lines());
        OrderDraft draft = OrderDraft.create(request.firstname(), request.telephone(), request.shippingMethod(), request.paymentMethod(),
                lines, request.shippingCost(), request.discount(), request.prepayment());

        if (!orders.statusExists(request.statusId())) {
            throw new DomainException("Статусу " + request.statusId() + " не існує.");
        }
        if (request.orderSourceId() != 0 && !store.sourceExists(request.orderSourceId())) {
            throw new DomainException("Джерела замовлення " + request.orderSourceId() + " не існує.");
        }
        int managerId = request.managerId() == null ? userId : request.managerId();
        requireActiveUser(managerId);

        List<NewOrderLineRecord> records = records(lines);

        // The risk is counted before saving, so the new order does not count itself
        CustomerRiskDto risk = checkRisk(new CustomerRiskQuery(request.telephone(), request.firstname(), request.lastname(), request.email()));

        // As on the old site: first the order without a status, then the status through the usual status change,
        // so the log and other modules' handlers (sewn stock write-off and so on) run
        LocalDateTime now = now();
        int orderId = store.create(new NewOrderRecord(request, draft, records, now));
        orders.saveManager(orderId, managerId, userId, now);
        var status = orderService.changeStatus(orderId, new ChangeOrderStatusRequest(request.statusId()), userId);

        return new CreateOrderResult(orderId, status.warnings(), risk);
    }

    @Transactional(readOnly = true)
    public OrderCompositionDto getComposition(int orderId) {
        return store.getComposition(orderId).orElseThrow(() -> new NotFoundException("Замовлення " + orderId + " не знайдено."));
    }

    /**
     * New products, sizes, quantities and sums of an order (the old site's "Quick order" form for an existing order).
     * Sewn pieces are recounted by the new lines if the order's status writes them off.
     */
    public UpdateOrderCompositionResult updateComposition(int orderId, UpdateOrderCompositionRequest request, int userId) {
        OrderCompositionDto current = getComposition(orderId);
        List<OrderDraftLine> lines = draftLines(request.lines());
        OrderDraft draft = OrderDraft.forLines(lines, request.shippingCost(), request.discount(), request.prepayment());
        // A gift voucher spent in the order stays: the new sum must still cover it and the prepayment.
        BigDecimal voucher = store.voucherPaid(orderId);
        BigDecimal total = draft.total().subtract(voucher);
        if (total.signum() < 0) {
            throw new DomainException("Сума замовлення стала меншою за оплату сертифікатом (" + voucher.stripTrailingZeros().toPlainString()
                    + " грн). Спершу зніміть сертифікат.");
        }
        if (draft.prepayment().compareTo(total) > 0) {
            throw new DomainException("Сума завдатку не може перевищувати суму замовлення.");
        }
        requireActiveUser(userId);

        List<NewOrderLineRecord> records = records(lines);
        List<String> warnings = orderService.reapplyStatus(orderId, current.statusId(),
                () -> store.replaceLines(orderId, draft, records, now()), userId);
        return new UpdateOrderCompositionResult(total, warnings);
    }

    private List<NewOrderLineRecord> records(List<OrderDraftLine> lines) {
        Map<Integer, ProductPickDto> products = store.getProducts(lines.stream().map(OrderDraftLine::productId).distinct().toList())
                .stream().collect(Collectors.toMap(ProductPickDto::id, Function.identity()));
        return lines.stream().map(line -> record(line, products)).toList();
    }

    private static NewOrderLineRecord record(OrderDraftLine line, Map<Integer, ProductPickDto> products) {
        ProductPickDto product = products.get(line.productId());
        if (product == null) {
            throw new DomainException("Товару " + line.productId() + " не знайдено.");
        }

        ProductOptionValueDto option = null;
        if (line.productOptionValueId() != null) {
            option = product.options().stream()
                    .filter(o -> o.productOptionValueId() == line.productOptionValueId())
                    .findFirst()
                    .orElseThrow(() -> new DomainException("Товар «" + product.name() + "» не має такого розміру чи опції ("
                            + line.productOptionValueId() + ")."));
        } else if (!product.options().isEmpty()) {
            throw new DomainException("Оберіть розмір чи опцію для товару «" + product.name() + "».");
        }
        return new NewOrderLineRecord(line, product.name(), product.model(), option);
    }

    private static List<OrderDraftLine> draftLines(List<NewOrderLineRequest> lines) {
        return lines == null
                ? List.of()
                : lines.stream().map(l -> new OrderDraftLine(l.productId(), l.productOptionValueId(), l.quantity(), l.price())).toList();
    }

    private void requireActiveUser(int userId) {
        if (!orders.isActiveUser(userId)) {
            throw new DomainException("Користувача " + userId + " не знайдено або його вимкнено.");
        }
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
