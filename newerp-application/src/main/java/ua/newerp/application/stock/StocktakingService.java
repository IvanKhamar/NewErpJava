package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.application.common.Spreadsheet;
import ua.newerp.application.locations.LocationStore;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.stock.StockItem;
import ua.newerp.domain.stock.StockMovement;
import ua.newerp.domain.stock.StockPlacement;
import ua.newerp.domain.stock.Stocktaking;

/** Stocktaking of consumables: a sheet to count by, a preview of the uploaded counts, and applying them with log entries. */
@Service
@Transactional
public class StocktakingService {

    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    private final StockRepository repository;
    private final StockLocationRepository locations;
    private final LocationStore warehouses;
    private final Spreadsheet spreadsheet;
    private final Clock clock;

    public StocktakingService(StockRepository repository, StockLocationRepository locations, LocationStore warehouses,
            Spreadsheet spreadsheet, Clock clock) {
        this.repository = repository;
        this.locations = locations;
        this.warehouses = warehouses;
        this.spreadsheet = spreadsheet;
        this.clock = clock;
    }

    /**
     * What the books say lies at the place for every active item: at a warehouse its balance, on the main stock (null) the
     * total minus all warehouses.
     */
    @Transactional(readOnly = true)
    public Map<Integer, BigDecimal> booked(Integer warehouseId) {
        requireWarehouse(warehouseId);
        Map<Integer, BigDecimal> located = new HashMap<>();
        locations.listBalances().stream()
                .filter(b -> warehouseId == null || b.warehouseId() == warehouseId)
                .forEach(b -> located.merge(b.itemId(), b.quantity(), BigDecimal::add));
        return repository.listItems(new StockItemFilter(null, false)).stream()
                .collect(Collectors.toMap(StockItemDto::id, i -> warehouseId == null
                        ? i.quantity().subtract(located.getOrDefault(i.id(), BigDecimal.ZERO))
                        : located.getOrDefault(i.id(), BigDecimal.ZERO)));
    }

    /** Active items (of one category when given) with the quantity booked at the place and an empty "Фактично" column. */
    @Transactional(readOnly = true)
    public byte[] sheet(Integer categoryId, Integer warehouseId) {
        Map<Integer, BigDecimal> booked = booked(warehouseId);
        List<List<Object>> rows = repository.listItems(new StockItemFilter(categoryId, false)).stream()
                .map(i -> Arrays.<Object>asList(i.id(), i.name(), i.categoryName() == null ? "" : i.categoryName(), i.unit(),
                        booked.get(i.id()), null))
                .toList();
        return spreadsheet.write("Інвентаризація", Stocktaking.COLUMNS, rows);
    }

    /** Reads an uploaded sheet counted at the place; nothing is written. */
    @Transactional(readOnly = true)
    public StocktakingPreviewDto preview(byte[] file, Integer warehouseId) {
        if (file.length > MAX_FILE_BYTES) {
            throw new DomainException("Файл більший за 5 МБ.");
        }
        Stocktaking.Parsed parsed = Stocktaking.parse(spreadsheet.read(file));
        Map<Integer, StockItemDto> items = activeItems();
        Map<Integer, BigDecimal> booked = booked(warehouseId);
        var errors = new ArrayList<>(parsed.errors());
        var lines = new ArrayList<StocktakingLineDto>();
        for (Stocktaking.Count count : parsed.counts()) {
            StockItemDto item = items.get(count.itemId());
            if (item == null) {
                errors.add("Позиції " + count.itemId() + " немає на складі.");
                continue;
            }
            BigDecimal before = booked.get(item.id());
            lines.add(new StocktakingLineDto(item.id(), item.name(), item.unit(), before, count.actual(),
                    count.actual().subtract(before)));
        }
        lines.sort(Comparator.comparing(StocktakingLineDto::name));
        return new StocktakingPreviewDto(lines, errors);
    }

    /**
     * Sets every counted item at the place to its counted quantity in one transaction; the item's total changes by the same
     * difference, a log entry with the comment "Інвентаризація". If any count is wrong, nothing is written.
     */
    public StocktakingResult apply(ApplyStocktakingRequest request, int userId, String username) {
        if (request.counts().isEmpty()) {
            throw new DomainException("Немає жодної порахованої позиції.");
        }
        var seen = new HashSet<Integer>();
        request.counts().stream().filter(c -> !seen.add(c.itemId())).findFirst().ifPresent(dup -> {
            throw new DomainException("Позицію " + dup.itemId() + " указано двічі.");
        });
        requireWarehouse(request.warehouseId());
        String comment = Stocktaking.comment(request.comment());

        // Sorted by id, so two stocktakings at once lock the rows in the same order and cannot deadlock
        List<StocktakingCountRequest> counts = request.counts().stream().sorted(Comparator.comparingInt(StocktakingCountRequest::itemId))
                .toList();
        LocalDateTime now = LocalDateTime.now(clock);
        int changed = 0;
        BigDecimal value = BigDecimal.ZERO;
        for (StocktakingCountRequest count : counts) {
            StockItem item = repository.findItem(count.itemId()).filter(StockItem::isActive)
                    .orElseThrow(() -> new DomainException("Позиції " + count.itemId() + " немає на складі."));
            var placement = new StockPlacement(item, locations.findBalances(item.getId()));
            StockMovement movement;
            try {
                movement = placement.recountAt(request.warehouseId(), count.actual(), comment, userId, username, now);
            } catch (DomainException e) {
                throw new DomainException(item.getName() + ": " + e.getMessage());
            }
            placement.created().forEach(locations::add);
            if (movement != null) {
                repository.add(movement);
                changed++;
                value = value.add(movement.getDelta().multiply(item.getPrice()));
            }
        }
        return new StocktakingResult(counts.size(), changed, value.setScale(2, RoundingMode.HALF_UP));
    }

    private void requireWarehouse(Integer warehouseId) {
        if (warehouseId != null && warehouses.get(warehouseId).isEmpty()) {
            throw new NotFoundException("Склад " + warehouseId + " не знайдено.");
        }
    }

    private Map<Integer, StockItemDto> activeItems() {
        return repository.listItems(new StockItemFilter(null, false)).stream()
                .collect(Collectors.toMap(StockItemDto::id, Function.identity()));
    }
}
