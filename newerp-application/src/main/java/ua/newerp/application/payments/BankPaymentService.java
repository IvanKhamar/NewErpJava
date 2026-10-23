package ua.newerp.application.payments;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.application.common.PagedResult;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.payments.BankPayment;
import ua.newerp.domain.payments.PaymentMatcher;

/**
 * Payments to PrivatBank accounts and matching them with orders (the old site's sale/privatbank module):
 * statement import, order suggestions, linking and hiding payments.
 */
@Service
public class BankPaymentService {

    /** How many orders to suggest for a payment. */
    public static final int SUGGESTION_LIMIT = 10;

    /** Look for orders created no earlier than this many days before the payment. */
    public static final int SUGGESTION_DAYS = 60;

    /** The longest period of one import, so a statement for years is not pulled by accident. */
    public static final int MAX_IMPORT_DAYS = 31;

    private final BankPaymentRepository repository;
    private final BankStatementClient client;
    private final Clock clock;

    public BankPaymentService(BankPaymentRepository repository, BankStatementClient client, Clock clock) {
        this.repository = repository;
        this.client = client;
        this.clock = clock;
    }

    public PagedResult<BankPaymentDto> list(BankPaymentFilter filter) {
        return repository.list(filter);
    }

    public List<BankPaymentDto> forOrder(int orderId) {
        return repository.listForOrder(orderId);
    }

    /**
     * Loads the statement and adds new incoming payments. Like the old site, it skips outgoing ones and those credited by LiqPay
     * (LiqPay itself matches them with orders). Without a period: yesterday and today, like the old site's cron.
     * The bank is called outside a database transaction.
     */
    public BankImportResult importStatement(LocalDate from, LocalDate to) {
        if (!client.isConfigured()) {
            return new BankImportResult(false, 0, 0, 0);
        }

        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(1);
        if (start.isAfter(end)) {
            throw new DomainException("Початок періоду пізніше за кінець.");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_IMPORT_DAYS) {
            throw new DomainException("Завантажувати можна не більше ніж " + MAX_IMPORT_DAYS + " днів за раз.");
        }

        List<BankTransaction> transactions = client.fetch(start, end);
        Set<String> seen = new HashSet<>();
        List<BankTransaction> incoming = transactions.stream()
                .filter(t -> t.isIncoming() && !t.id().isEmpty() && !t.purpose().toUpperCase(Locale.ROOT).contains("LIQPAY"))
                .filter(t -> seen.add(t.id()))
                .toList();
        Set<String> existing = repository.existingAppCodes(incoming.stream().map(BankTransaction::id).toList());
        List<BankPayment> added = incoming.stream()
                .filter(t -> !existing.contains(t.id()))
                .map(t -> new BankPayment(t.id(), t.account(), t.amount(), t.balance(), describe(t), t.payer(), t.date(), t.time()))
                .toList();

        repository.addAll(added);
        return new BankImportResult(true, transactions.size(), added.size(), transactions.size() - added.size());
    }

    /**
     * Orders the payment probably belongs to. Candidates are orders with the same sum (full, prepayment or the rest),
     * with a number mentioned in the purpose, or with the customer from the search; the most likely first.
     */
    @Transactional(readOnly = true)
    public List<PaymentSuggestionDto> suggest(int paymentId, String customer) {
        BankPayment payment = require(paymentId);
        BigDecimal amount = payment.getAmount();
        List<BigDecimal> amounts = amount.compareTo(amount.setScale(0, RoundingMode.FLOOR)) == 0
                ? List.of(amount)
                : List.of(amount, amount.setScale(0, RoundingMode.FLOOR));
        var query = new CandidateQuery(
                amounts,
                PaymentMatcher.orderNumbersIn(payment.getDescription() + " " + payment.getTerminal()),
                customer == null || customer.isBlank() ? null : customer.strip(),
                payment.getTimestamp().minusDays(SUGGESTION_DAYS),
                payment.getTimestamp().plusDays(1),
                SUGGESTION_LIMIT * 5);

        return PaymentMatcher.rank(payment, repository.findCandidates(query)).stream()
                .limit(SUGGESTION_LIMIT)
                .map(s -> new PaymentSuggestionDto(s.order().orderId(), s.order().customer(), s.order().telephone(), s.order().total(),
                        s.order().prepayment(), s.order().dateAdded(), s.order().status(), s.order().linkedPaymentId(), s.reasons(),
                        s.score()))
                .toList();
    }

    /** Links the payment to an order (orderId = 0 unlinks). An order can have several payments (prepayment and the rest). */
    @Transactional
    public BankPaymentDto link(int paymentId, LinkPaymentRequest request) {
        BankPayment payment = require(paymentId);
        if (request.orderId() == 0) {
            payment.unlink();
        } else if (repository.orderExists(request.orderId())) {
            payment.linkTo(request.orderId());
        } else {
            throw new NotFoundException("Замовлення " + request.orderId() + " не знайдено.");
        }
        repository.save(payment);
        return repository.getDto(paymentId).orElseThrow();
    }

    @Transactional
    public void setVisibility(int paymentId, boolean visible) {
        BankPayment payment = require(paymentId);
        if (visible) {
            payment.show();
        } else {
            payment.hide();
        }
        repository.save(payment);
    }

    /** Like the old site: the payment purpose, and under it the payer's account, bank code and tax code. */
    private static String describe(BankTransaction t) {
        List<String> lines = new ArrayList<>();
        lines.add(t.purpose());
        if (!t.payerAccount().isEmpty()) {
            lines.add("Рахунок: " + t.payerAccount());
        }
        if (!t.payerBankCode().isEmpty()) {
            lines.add("МФО: " + t.payerBankCode());
        }
        if (!t.payerTaxCode().isEmpty()) {
            lines.add("ЄДРПОУ: " + t.payerTaxCode());
        }
        return String.join("\n", lines.stream().filter(l -> !l.isEmpty()).toList());
    }

    private BankPayment require(int id) {
        return repository.get(id).orElseThrow(() -> new NotFoundException("Платіж " + id + " не знайдено."));
    }
}
