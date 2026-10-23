package ua.newerp.application.returns;

import java.time.LocalDateTime;

import ua.newerp.domain.returns.ReturnableLine;

/** What to write (all checks passed). */
public record NewReturnRecord(ReturnSourceOrder order, ReturnableLine line, CreateReturnRequest request, LocalDateTime now) {
}
