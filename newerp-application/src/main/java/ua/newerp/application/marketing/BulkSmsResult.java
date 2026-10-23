package ua.newerp.application.marketing;

import java.util.List;

/** @param skipped numbers that do not look like a Ukrainian mobile */
public record BulkSmsResult(int sent, int notSent, List<String> skipped) {
}
