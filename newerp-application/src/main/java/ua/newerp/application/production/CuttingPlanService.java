package ua.newerp.application.production;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.stock.StockItemDto;
import ua.newerp.application.stock.StockItemFilter;
import ua.newerp.application.stock.StockRepository;
import ua.newerp.domain.production.CuttingPlan;
import ua.newerp.domain.production.ProductionComponent;
import ua.newerp.domain.production.ProductionModel;
import ua.newerp.domain.production.ProductionSize;

/**
 * The cutting plan: what to cut so that every active "clothing" model gets back to its minimum split by the size shares,
 * plus what active orders still miss (taken from the sewing plan), and whether the consumables on stock are enough for it.
 */
@Service
@Transactional(readOnly = true)
public class CuttingPlanService {

    private static final Collator NAMES = Collator.getInstance(Locale.forLanguageTag("uk-UA"));

    private final SewingPlanSource source;
    private final SewingPlanService sewingPlan;
    private final StockRepository stock;

    public CuttingPlanService(SewingPlanSource source, SewingPlanService sewingPlan, StockRepository stock) {
        this.source = source;
        this.sewingPlan = sewingPlan;
        this.stock = stock;
    }

    /** onlyToCut: leave out models where nothing has to be cut. */
    public CuttingPlanDto get(boolean onlyToCut) {
        List<ProductionModel> models = source.getActiveClothingModels().stream()
                .sorted(Comparator.comparingInt(ProductionModel::getSortOrder).thenComparing(ProductionModel::getName, NAMES))
                .toList();
        Map<Integer, BigDecimal> ordered = sewingPlan.get(false).rows().stream()
                .collect(Collectors.toMap(SewingPlanRowDto::sizeId, SewingPlanRowDto::missing, BigDecimal::add));
        Map<Integer, List<ProductionComponent>> components = source.getComponents(models.stream().map(ProductionModel::getId).toList())
                .stream().collect(Collectors.groupingBy(ProductionComponent::getProductionId));

        var modelDtos = new ArrayList<CuttingPlanModelDto>();
        Map<Integer, BigDecimal> need = new LinkedHashMap<>();
        Map<Integer, Set<String>> needBy = new HashMap<>();
        for (ProductionModel model : models) {
            List<ProductionSize> sizes = model.getSizes().stream()
                    .sorted(Comparator.comparingInt(ProductionSize::getSortOrder).thenComparing(ProductionSize::getId))
                    .toList();
            List<CuttingPlan.SizeLine> lines = CuttingPlan.forModel(model.getMinTotal(), sizes.stream()
                    .map(s -> new CuttingPlan.SizeInput(s.getId(), s.getPercent(), s.getCutStock(), s.getSewnStock(),
                            ordered.getOrDefault(s.getId(), BigDecimal.ZERO)))
                    .toList());

            var sizeDtos = new ArrayList<CuttingPlanSizeDto>(sizes.size());
            Map<Integer, BigDecimal> toCut = new LinkedHashMap<>();
            for (int i = 0; i < sizes.size(); i++) {
                ProductionSize size = sizes.get(i);
                CuttingPlan.SizeLine line = lines.get(i);
                sizeDtos.add(new CuttingPlanSizeDto(size.getId(), size.getName(), size.getPercent(), line.target(),
                        ordered.getOrDefault(size.getId(), BigDecimal.ZERO), size.getCutStock(), size.getSewnStock(), line.toCut()));
                toCut.put(size.getId(), line.toCut());
            }
            BigDecimal total = toCut.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (onlyToCut && total.signum() == 0) {
                continue;
            }
            modelDtos.add(new CuttingPlanModelDto(model.getId(), model.getName(), model.getMinTotal(), total, sizeDtos));

            CuttingPlan.consumables(components.getOrDefault(model.getId(), List.of()), toCut).forEach((itemId, quantity) -> {
                need.merge(itemId, quantity, BigDecimal::add);
                needBy.computeIfAbsent(itemId, k -> new LinkedHashSet<>()).add(model.getName());
            });
        }
        return new CuttingPlanDto(modelDtos, items(need, needBy));
    }

    /** Shortages first (the largest on top), then the rest by name. */
    private List<CuttingPlanItemDto> items(Map<Integer, BigDecimal> need, Map<Integer, Set<String>> needBy) {
        if (need.isEmpty()) {
            return List.of();
        }
        Map<Integer, StockItemDto> items = stock.listItems(new StockItemFilter(null, false)).stream()
                .collect(Collectors.toMap(StockItemDto::id, Function.identity()));
        return need.entrySet().stream()
                .map(e -> {
                    StockItemDto item = items.get(e.getKey());
                    BigDecimal onStock = item == null ? BigDecimal.ZERO : item.quantity().max(BigDecimal.ZERO);
                    return new CuttingPlanItemDto(e.getKey(), item == null ? "Розхідник " + e.getKey() + " (немає на складі)" : item.name(),
                            item == null ? "" : item.unit(), e.getValue(), item == null ? BigDecimal.ZERO : item.quantity(),
                            e.getValue().subtract(onStock).max(BigDecimal.ZERO), List.copyOf(needBy.get(e.getKey())));
                })
                .sorted(Comparator.comparing(CuttingPlanItemDto::shortage).reversed().thenComparing(CuttingPlanItemDto::name, NAMES))
                .toList();
    }
}
