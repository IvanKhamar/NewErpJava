package ua.newerp.application.payments;

import java.time.LocalDate;
import java.util.List;

/**
 * PrivatBank statement (Autoclient for business accounts). Without keys a stub that requests nothing is registered.
 * The keys (id and token of each account) are known only to the implementation.
 */
public interface BankStatementClient {

    boolean isConfigured();

    /** All transactions of the period (incoming and outgoing) from all configured accounts. */
    List<BankTransaction> fetch(LocalDate from, LocalDate to);
}
