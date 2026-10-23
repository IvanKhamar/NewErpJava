package ua.newerp.application.marketing;

import java.util.List;

public record BulkSmsRequest(List<String> phones, String text) {
}
