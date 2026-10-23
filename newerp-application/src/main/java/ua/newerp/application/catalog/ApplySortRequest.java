package ua.newerp.application.catalog;

import java.util.List;

public record ApplySortRequest(int type, String key, List<Integer> productIds) {
}
