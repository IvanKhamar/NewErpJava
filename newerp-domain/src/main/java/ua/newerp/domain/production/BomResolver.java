package ua.newerp.domain.production;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Rule of the old module for BOMs: a row for a specific size overrides the general row (without size). */
public final class BomResolver {

    private BomResolver() {
    }

    /**
     * Effective rows for a size: one per key (consumable or component).
     * If the key has a row for exactly this size, that one wins, otherwise the general one.
     * {@code <T>} makes the method generic, like Resolve&lt;T&gt; in C#; Function&lt;T, Integer&gt; is Func&lt;T, int?&gt;.
     */
    public static <T> List<T> resolve(Iterable<T> rows, int sizeId, Function<T, Integer> sizeOf, Function<T, Integer> keyOf) {
        // LinkedHashMap keeps insertion order, like Dictionary values in .NET
        Map<Integer, T> effective = new LinkedHashMap<>();
        for (T row : rows) {
            Integer key = keyOf.apply(row);
            if (key == null) {
                continue;
            }
            Integer size = sizeOf.apply(row);
            if (Objects.equals(size, sizeId)) {
                effective.put(key, row);
            } else if (size == null) {
                effective.putIfAbsent(key, row);
            }
        }
        return new ArrayList<>(effective.values());
    }
}
