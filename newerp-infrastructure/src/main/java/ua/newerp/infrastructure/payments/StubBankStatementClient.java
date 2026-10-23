package ua.newerp.infrastructure.payments;

import java.time.LocalDate;
import java.util.List;

import ua.newerp.application.payments.BankStatementClient;
import ua.newerp.application.payments.BankTransaction;

/** Without bank keys: requests nothing (isConfigured = false). */
class StubBankStatementClient implements BankStatementClient {

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public List<BankTransaction> fetch(LocalDate from, LocalDate to) {
        return List.of();
    }
}
