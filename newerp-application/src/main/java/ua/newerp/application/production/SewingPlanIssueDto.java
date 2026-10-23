package ua.newerp.application.production;

import java.util.List;

/** A product of active orders that could not be counted in the plan (no model, or a size without a link), and in which orders it is. */
public record SewingPlanIssueDto(String productName, String reason, List<Integer> orderIds) {
}
