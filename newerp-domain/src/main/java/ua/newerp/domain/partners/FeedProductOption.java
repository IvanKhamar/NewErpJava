package ua.newerp.domain.partners;

import java.math.BigDecimal;

/** A value of a product option (mostly a size). price is the size's full price, as the old feed took it. */
public record FeedProductOption(int optionValueId, String name, BigDecimal price) {
}
