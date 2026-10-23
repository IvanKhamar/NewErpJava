package ua.newerp.application.production;

import java.util.List;

public record SewingPlanDto(int orderCount, List<SewingPlanRowDto> rows, List<SewingPlanIssueDto> issues) {
}
