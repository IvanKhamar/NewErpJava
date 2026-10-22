package ua.newerp.application.orders;

import java.util.List;

/** warnings: messages from other modules (for example, not enough sewn pieces in stock). */
public record ChangeOrderStatusResult(List<String> warnings) {
}
