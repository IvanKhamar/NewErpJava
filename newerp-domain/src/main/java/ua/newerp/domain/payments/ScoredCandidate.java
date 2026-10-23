package ua.newerp.domain.payments;

/** @param reasons {@link MatchReason} flags */
public record ScoredCandidate(OrderCandidate order, int reasons, int score) {
}
