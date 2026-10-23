package ua.newerp.domain.catalog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Calculates the order of products, as the old site's "Порядок товарів" module. */
public final class ProductSorter {

    private ProductSorter() {
    }

    /**
     * The automatic queue: products by sales descending, unsold ones at the end.
     * With equal sales the original order stays (a stable sort, as in PHP 8).
     */
    public static List<Integer> bySales(Collection<Integer> productIds, Map<Integer, Integer> sales) {
        return productIds.stream().distinct()
                .sorted(Comparator.comparingInt((Integer id) -> sales.getOrDefault(id, 0)).reversed())
                .toList();
    }

    /**
     * Merges the automatic queue with the pins: a pinned product takes its position, the rest fill the free places in turn.
     * A pin whose position is beyond the number of products goes right after them instead of getting lost.
     */
    public static List<Integer> merge(List<Integer> autoQueue, Collection<SortPin> pins) {
        // As in the old code: a position holds the last pin (the table allows no two), a pinned product is not repeated.
        var byPosition = new TreeMap<Integer, Integer>();
        for (SortPin pin : pins) {
            if (pin.position() >= 1) {
                byPosition.put(pin.position(), pin.productId());
            }
        }
        var pinned = new HashSet<>(byPosition.values());
        var queue = new ArrayDeque<Integer>(autoQueue.stream().filter(id -> !pinned.contains(id)).toList());

        var result = new ArrayList<Integer>();
        int total = queue.size() + byPosition.size();
        for (int position = 1; position <= total; position++) {
            Integer productId = byPosition.get(position);
            if (productId != null) {
                result.add(productId);
            } else if (!queue.isEmpty()) {
                result.add(queue.poll());
            }
        }
        result.addAll(queue);

        // The old code lost pins beyond the list (its loop ended earlier). They go to the end in the order of positions.
        result.addAll(byPosition.tailMap(total, false).values());
        return result.stream().distinct().toList();
    }
}
