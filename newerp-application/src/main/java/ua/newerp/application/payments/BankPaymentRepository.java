package ua.newerp.application.payments;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import ua.newerp.application.common.PagedResult;
import ua.newerp.domain.payments.BankPayment;
import ua.newerp.domain.payments.OrderCandidate;

public interface BankPaymentRepository {

    PagedResult<BankPaymentDto> list(BankPaymentFilter filter);

    Optional<BankPayment> get(int id);

    Optional<BankPaymentDto> getDto(int id);

    List<BankPaymentDto> listForOrder(int orderId);

    /** Which of these transaction ids are already in the database. */
    Set<String> existingAppCodes(Collection<String> appCodes);

    List<OrderCandidate> findCandidates(CandidateQuery query);

    boolean orderExists(int orderId);

    void addAll(List<BankPayment> payments);

    /** Saves the changes of a payment loaded by {@link #get}. */
    void save(BankPayment payment);
}
