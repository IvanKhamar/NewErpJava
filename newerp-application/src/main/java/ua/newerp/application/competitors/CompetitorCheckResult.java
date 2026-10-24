package ua.newerp.application.competitors;

/** checked pages, how many prices changed, how many pages failed (their lastError says why). */
public record CompetitorCheckResult(int checked, int changed, int failed) {
}
