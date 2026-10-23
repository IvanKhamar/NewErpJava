package ua.newerp.domain.production;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * The cutting plan: how many of each size to cut so that the model's stock gets back to its minimum (min_total) split by the
 * size shares, plus what active orders still miss. The old module kept the shares and the minimum, but the storekeeper
 * counted the cut by hand.
 */
public final class CuttingPlan {

    /** One size: its share in %, its stock, and what active orders still miss (not yet written off from the sewn stock). */
    public record SizeInput(int sizeId, BigDecimal percent, BigDecimal cutStock, BigDecimal sewnStock, BigDecimal ordered) {
    }

    /** target: this size's part of the minimum; toCut: a whole number of pieces, 0 when the stock is enough. */
    public record SizeLine(int sizeId, int target, BigDecimal toCut) {
    }

    private CuttingPlan() {
    }

    /**
     * toCut = target + ordered − cut − sewn, rounded up, never below 0. Cut sets count as stock, because they get sewn first;
     * negative stock (sold ahead) is not subtracted twice, as it is already part of what orders miss.
     */
    public static List<SizeLine> forModel(int minTotal, List<SizeInput> sizes) {
        int[] targets = split(minTotal, sizes.stream().map(SizeInput::percent).toList());
        var lines = new ArrayList<SizeLine>(sizes.size());
        for (int i = 0; i < sizes.size(); i++) {
            SizeInput size = sizes.get(i);
            BigDecimal toCut = BigDecimal.valueOf(targets[i]).add(size.ordered().max(BigDecimal.ZERO))
                    .subtract(size.cutStock().max(BigDecimal.ZERO))
                    .subtract(size.sewnStock().max(BigDecimal.ZERO))
                    .max(BigDecimal.ZERO)
                    .setScale(0, RoundingMode.CEILING);
            lines.add(new SizeLine(size.sizeId(), targets[i], toCut));
        }
        return lines;
    }

    /**
     * Splits a total into whole parts by shares with the largest remainder method, so the parts always add up to the total.
     * Shares need not add up to 100; when all are 0 the total is split evenly.
     */
    public static int[] split(int total, List<BigDecimal> shares) {
        int n = shares.size();
        if (n == 0) {
            return new int[0];
        }
        List<BigDecimal> weights = shares.stream().allMatch(s -> s == null || s.signum() <= 0)
                ? Collections.nCopies(n, BigDecimal.ONE)
                : shares.stream().map(s -> s == null ? BigDecimal.ZERO : s.max(BigDecimal.ZERO)).toList();
        BigDecimal sum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        int[] parts = new int[n];
        BigDecimal[] remainders = new BigDecimal[n];
        int given = 0;
        for (int i = 0; i < n; i++) {
            BigDecimal exact = BigDecimal.valueOf(total).multiply(weights.get(i)).divide(sum, 6, RoundingMode.HALF_UP);
            parts[i] = exact.setScale(0, RoundingMode.FLOOR).intValueExact();
            remainders[i] = exact.subtract(BigDecimal.valueOf(parts[i]));
            given += parts[i];
        }
        // The pieces left after rounding down go to the sizes with the largest remainders (the first size wins a tie)
        int[] order = IntStream.range(0, n).boxed()
                .sorted(Comparator.comparing((Integer i) -> remainders[i]).reversed().thenComparing(i -> i))
                .mapToInt(Integer::intValue).toArray();
        for (int k = 0; k < total - given; k++) {
            parts[order[k % n]]++;
        }
        return parts;
    }

    /**
     * Consumables the cut takes, by item id: for each size and each component, the component's cutting BOM rows for that size
     * times the pieces to cut, as the "+Cut" operation writes them off.
     */
    public static Map<Integer, BigDecimal> consumables(List<ProductionComponent> components, Map<Integer, BigDecimal> toCutBySize) {
        Map<Integer, BigDecimal> need = new LinkedHashMap<>();
        toCutBySize.forEach((sizeId, quantity) -> {
            if (quantity.signum() <= 0) {
                return;
            }
            for (ProductionComponent component : components) {
                for (ComponentBomLine line : BomResolver.resolve(component.getBom(), sizeId, ComponentBomLine::getSizeId,
                        ComponentBomLine::getItemId)) {
                    need.merge(line.getItemId(), line.getQtyPerUnit().multiply(quantity), BigDecimal::add);
                }
            }
        });
        return need;
    }
}
