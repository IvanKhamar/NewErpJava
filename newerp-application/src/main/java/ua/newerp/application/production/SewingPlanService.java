package ua.newerp.application.production;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.domain.production.ProductionModel;
import ua.newerp.domain.production.ProductionSize;
import ua.newerp.domain.production.SewingDemand;
import ua.newerp.domain.production.SewingPlan;
import ua.newerp.domain.production.SewingPlanLine;

/** The sewing plan: how many of each model size to sew and cut for active orders. */
@Service
@Transactional(readOnly = true)
public class SewingPlanService {

    /** Sorts names as people expect (Ukrainian letters in the alphabet's order), like StringComparer.CurrentCulture. */
    private static final Collator NAMES = Collator.getInstance(Locale.forLanguageTag("uk-UA"));

    private record Issue(String product, String reason, int orderId) {
    }

    private record IssueKey(String product, String reason) {
    }

    private final SewingPlanSource source;
    private final ProductionProperties properties;
    private final Clock clock;

    public SewingPlanService(SewingPlanSource source, ProductionProperties properties, Clock clock) {
        this.source = source;
        this.properties = properties;
        this.clock = clock;
    }

    public SewingPlanDto get(boolean onlyToSew) {
        List<PlanOrder> orders = source.getPlanOrders(properties.planStatusIds());
        var issues = new ArrayList<Issue>();

        Map<Integer, ProductionModel> models = source.getPlanModels(orders.stream()
                        .flatMap(o -> o.lines().stream())
                        .map(OrderSizeLine::productionId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(ProductionModel::getId, Function.identity()));
        Map<Integer, Integer> days = source.getProductionDays(orders.stream()
                .flatMap(o -> o.lines().stream()).map(OrderSizeLine::productId).distinct().toList());

        // As in the stock handler: a line's size is the model size linked to one of the product's options in the order
        var demands = new ArrayList<SewingDemand>();
        for (PlanOrder order : orders) {
            for (OrderSizeLine line : order.lines()) {
                ProductionModel model = line.productionId() == null ? null : models.get(line.productionId());
                if (model == null) {
                    issues.add(new Issue(line.productName(), "Не прив'язано до моделі виробництва.", order.orderId()));
                    continue;
                }
                ProductionSize size = model.getSizes().stream()
                        .filter(s -> s.getOptionValueId() != null && line.optionValueIds().contains(s.getOptionValueId()))
                        .findFirst().orElse(null);
                if (size == null) {
                    issues.add(new Issue(line.productName(), "Розмір із замовлення не зв'язаний з розмірами моделі «" + model.getName() + "».",
                            order.orderId()));
                    continue;
                }
                LocalDate deadline = SewingPlan.deadline(order.dateAdded(), days.getOrDefault(line.productId(), 0));
                demands.add(new SewingDemand(order.orderId(), size.getId(), BigDecimal.valueOf(line.quantity()), BigDecimal.ZERO, deadline));
            }
        }

        // What an order holds is counted per size, not per line: it is spread over the lines of that size in turn
        List<SewingDemand> spread = spreadHeld(demands, orders);

        LocalDate today = LocalDate.now(clock);
        var rows = new ArrayList<SewingPlanRowDto>();
        for (ProductionModel model : models.values()) {
            List<ProductionSize> sizes = model.getSizes().stream()
                    .sorted(Comparator.comparingInt(ProductionSize::getSortOrder).thenComparing(ProductionSize::getId))
                    .toList();
            for (ProductionSize size : sizes) {
                if (spread.stream().noneMatch(d -> d.sizeId() == size.getId())) {
                    continue;
                }
                SewingPlanLine line = SewingPlan.forSize(size.getId(), size.getSewnStock(), size.getCutStock(), spread);
                if (onlyToSew && line.toSew().signum() <= 0) {
                    continue;
                }
                boolean overdue = line.toSew().signum() > 0 && line.deadline() != null && line.deadline().isBefore(today);
                rows.add(new SewingPlanRowDto(model.getId(), model.getName(), size.getId(), size.getName(), line.ordered(), line.missing(),
                        size.getSewnStock(), size.getCutStock(), line.toSew(), line.fromCut(), line.toCut(), line.deadline(), overdue,
                        line.orderIds()));
            }
        }

        rows.sort(Comparator.comparing(SewingPlanRowDto::deadline, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(r -> models.get(r.productionId()).getSortOrder())
                .thenComparing(SewingPlanRowDto::modelName, NAMES));

        // Products we do not sew (without a model) are many in active orders, so they are grouped by product
        var grouped = new LinkedHashMap<IssueKey, List<Integer>>();
        issues.forEach(i -> grouped.computeIfAbsent(new IssueKey(i.product(), i.reason()), k -> new ArrayList<>()).add(i.orderId()));
        List<SewingPlanIssueDto> issueDtos = grouped.entrySet().stream()
                .map(e -> new SewingPlanIssueDto(e.getKey().product(), e.getKey().reason(), e.getValue().stream().distinct().sorted().toList()))
                .sorted(Comparator.comparing(SewingPlanIssueDto::productName, NAMES))
                .toList();
        return new SewingPlanDto(orders.size(), rows, issueDtos);
    }

    private static List<SewingDemand> spreadHeld(List<SewingDemand> demands, List<PlanOrder> orders) {
        Map<Integer, Map<Integer, BigDecimal>> left = new HashMap<>();
        orders.forEach(o -> left.put(o.orderId(), new HashMap<>(o.held())));
        var result = new ArrayList<SewingDemand>(demands.size());
        for (SewingDemand d : demands) {
            Map<Integer, BigDecimal> sizes = left.get(d.orderId());
            BigDecimal available = sizes == null ? BigDecimal.ZERO : sizes.getOrDefault(d.sizeId(), BigDecimal.ZERO);
            BigDecimal take = available.max(BigDecimal.ZERO).min(d.ordered());
            if (sizes != null && take.signum() > 0) {
                sizes.put(d.sizeId(), available.subtract(take));
            }
            result.add(d.withHeld(take));
        }
        return result;
    }
}
