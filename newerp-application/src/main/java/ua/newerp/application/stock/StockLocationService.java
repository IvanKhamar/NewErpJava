package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.application.locations.LocationDto;
import ua.newerp.application.locations.LocationStore;
import ua.newerp.domain.common.Text;
import ua.newerp.domain.stock.StockBalance;
import ua.newerp.domain.stock.StockItem;
import ua.newerp.domain.stock.StockMovement;
import ua.newerp.domain.stock.StockPlacement;
import ua.newerp.domain.stock.StockTransfer;

/**
 * Consumables at several warehouses (the warehouses of "Склади й пункти видачі"). The item's total stays in dd_stock_items, so
 * the old site and every other operation keep working; whatever lies at no warehouse is the main stock.
 */
@Service
@Transactional
public class StockLocationService {

    public static final String MAIN_NAME = "Основний склад";

    private static final Collator NAMES = Collator.getInstance(Locale.forLanguageTag("uk-UA"));

    private final StockRepository stock;
    private final StockLocationRepository locations;
    private final LocationStore warehouses;
    private final Clock clock;

    public StockLocationService(StockRepository stock, StockLocationRepository locations, LocationStore warehouses, Clock clock) {
        this.stock = stock;
        this.locations = locations;
        this.warehouses = warehouses;
        this.clock = clock;
    }

    /** Reads without locking the item, unlike the changes below. */
    @Transactional(readOnly = true)
    public ItemPlacementDto get(int itemId) {
        StockItemDto item = stock.getItemDto(itemId)
                .orElseThrow(() -> new NotFoundException("Позицію складу " + itemId + " не знайдено."));
        List<StockBalance> balances = locations.findBalances(itemId);
        BigDecimal located = balances.stream().map(StockBalance::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        return dto(item.id(), item.name(), item.unit(), item.quantity(), item.quantity().subtract(located), balances, names());
    }

    /** Moves stock between two places; the total stays and the move is logged. Returns where the item lies now. */
    public ItemPlacementDto transfer(int itemId, TransferStockRequest request, int userId, String username) {
        Map<Integer, String> names = names();
        requireWarehouse(request.fromWarehouseId(), names);
        requireWarehouse(request.toWarehouseId(), names);
        StockPlacement placement = placement(itemId);
        StockTransfer transfer = placement.move(request.fromWarehouseId(), request.toWarehouseId(), request.quantity(),
                request.comment(), userId, username, LocalDateTime.now(clock));
        placement.created().forEach(locations::add);
        locations.add(transfer);
        return dto(placement, names);
    }

    /** Receipt (+) or use (−) at a warehouse; it goes into the movement log with the warehouse's name. */
    public StockMovementDto adjust(int itemId, int warehouseId, AdjustStockRequest request, int userId, String username) {
        Map<Integer, String> names = names();
        requireWarehouse(warehouseId, names);
        StockPlacement placement = placement(itemId);
        // a blank reason goes on as it is, so the domain refuses it with its usual message
        String reason = request.reason() == null || request.reason().isBlank() ? request.reason()
                : Text.truncate(request.reason().strip() + " · " + names.get(warehouseId), 255);
        StockMovement m = placement.adjustAt(warehouseId, request.delta(), reason, userId, username, LocalDateTime.now(clock));
        placement.created().forEach(locations::add);
        stock.add(m);
        return StockService.dto(m);
    }

    @Transactional(readOnly = true)
    public List<StockTransferDto> transfers(int itemId) {
        Map<Integer, String> names = names();
        return locations.listTransfers(itemId).stream()
                .map(t -> new StockTransferDto(t.getId(), t.getFromWarehouseId(), name(t.getFromWarehouseId(), names),
                        t.getToWarehouseId(), name(t.getToWarehouseId(), names), t.getQuantity(), t.getComment(), t.getUsername(),
                        t.getCreatedAt()))
                .toList();
    }

    /** Every active item with its stock at each place; only warehouses that hold something get a column. */
    @Transactional(readOnly = true)
    public StockByWarehouseDto byWarehouse() {
        Map<Integer, String> names = names();
        List<StockItemDto> items = stock.listItems(new StockItemFilter(null, false));
        Map<Integer, Map<Integer, BigDecimal>> balances = new HashMap<>();
        locations.listBalances().forEach(b -> balances.computeIfAbsent(b.itemId(), k -> new HashMap<>()).put(b.warehouseId(), b.quantity()));
        List<Integer> columns = balances.values().stream().flatMap(m -> m.keySet().stream()).distinct()
                .sorted(Comparator.comparing((Integer id) -> name(id, names), NAMES))
                .toList();

        var rows = new ArrayList<StockByWarehouseDto.Row>(items.size());
        BigDecimal[] values = new BigDecimal[columns.size() + 1]; // 0 = the main stock
        Arrays.fill(values, BigDecimal.ZERO);
        for (StockItemDto item : items) {
            Map<Integer, BigDecimal> at = balances.getOrDefault(item.id(), Map.of());
            var quantities = new ArrayList<BigDecimal>(columns.size() + 1);
            quantities.add(item.quantity().subtract(at.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
            columns.forEach(id -> quantities.add(at.getOrDefault(id, BigDecimal.ZERO)));
            for (int i = 0; i < quantities.size(); i++) {
                values[i] = values[i].add(quantities.get(i).max(BigDecimal.ZERO).multiply(item.price()));
            }
            rows.add(new StockByWarehouseDto.Row(item.id(), item.name(), item.unit(), item.categoryName(), item.quantity(), quantities));
        }

        var places = new ArrayList<StockByWarehouseDto.Place>(columns.size() + 1);
        places.add(new StockByWarehouseDto.Place(null, MAIN_NAME, money(values[0])));
        for (int i = 0; i < columns.size(); i++) {
            places.add(new StockByWarehouseDto.Place(columns.get(i), name(columns.get(i), names), money(values[i + 1])));
        }
        return new StockByWarehouseDto(places, rows);
    }

    private StockPlacement placement(int itemId) {
        StockItem item = stock.findItem(itemId)
                .filter(StockItem::isActive)
                .orElseThrow(() -> new NotFoundException("Позицію складу " + itemId + " не знайдено."));
        return new StockPlacement(item, locations.findBalances(itemId));
    }

    private static ItemPlacementDto dto(StockPlacement placement, Map<Integer, String> names) {
        StockItem item = placement.item();
        return dto(item.getId(), item.getName(), item.getUnit(), item.getQuantity(), placement.main(), placement.balances(), names);
    }

    private static ItemPlacementDto dto(int itemId, String name, String unit, BigDecimal total, BigDecimal main,
            Collection<StockBalance> balances, Map<Integer, String> names) {
        var places = new ArrayList<PlaceDto>();
        places.add(new PlaceDto(null, MAIN_NAME, main));
        balances.stream()
                .filter(b -> b.getQuantity().signum() != 0)
                .sorted(Comparator.comparing((StockBalance b) -> name(b.getWarehouseId(), names), NAMES))
                .forEach(b -> places.add(new PlaceDto(b.getWarehouseId(), name(b.getWarehouseId(), names), b.getQuantity())));
        return new ItemPlacementDto(itemId, name, unit, total, places);
    }

    private Map<Integer, String> names() {
        return warehouses.list(null).stream().collect(Collectors.toMap(LocationDto::id, LocationDto::name));
    }

    private static void requireWarehouse(Integer warehouseId, Map<Integer, String> names) {
        if (warehouseId != null && !names.containsKey(warehouseId)) {
            throw new NotFoundException("Склад " + warehouseId + " не знайдено.");
        }
    }

    private static String name(Integer warehouseId, Map<Integer, String> names) {
        return warehouseId == null ? MAIN_NAME : names.getOrDefault(warehouseId, "Склад " + warehouseId);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
