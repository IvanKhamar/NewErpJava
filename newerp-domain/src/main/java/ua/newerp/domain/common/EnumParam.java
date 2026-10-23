package ua.newerp.domain.common;

import java.util.Locale;

/** Reads an enum from a query string as .NET does: by name in any case ("CategoryColor" for CATEGORY_COLOR) or by number. */
public final class EnumParam {

    private EnumParam() {
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String value, E defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String text = value.strip();
        E[] constants = type.getEnumConstants();
        if (text.matches("\\d{1,4}")) {
            int ordinal = Integer.parseInt(text);
            if (ordinal < constants.length) {
                return constants[ordinal];
            }
        } else {
            String name = text.replace("_", "").toUpperCase(Locale.ROOT);
            for (E constant : constants) {
                if (constant.name().replace("_", "").equals(name)) {
                    return constant;
                }
            }
        }
        throw new DomainException("Невідоме значення «" + text + "».");
    }
}
