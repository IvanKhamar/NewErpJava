package ua.newerp.application.production;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import ua.newerp.application.stock.PlaceDto;
import ua.newerp.application.stock.StockLocationService;
import ua.newerp.domain.production.ProductionModel;
import ua.newerp.domain.production.SewnBalance;
import ua.newerp.domain.production.SewnPlacement;
import ua.newerp.domain.production.SewnTransfer;

/**
 * Sewn pieces at several warehouses. The size's sewn stock stays the total (old site, orders, storefront); whatever lies at no
 * warehouse is the main stock.
 */
@Service
@Transactional
public class ProductionLocationService {

    private static final Collator NAMES = Collator.getInstance(Locale.forLanguageTag("uk-UA"));

    private final ProductionRepository production;
    private final SewnLocationRepository locations;
    private final LocationStore warehouses;
    private final Clock clock;

    public ProductionLocationService(ProductionRepository production, SewnLocationRepository locations, LocationStore warehouses,
            Clock clock) {
        this.production = production;
        this.locations = locations;
        this.warehouses = warehouses;
        this.clock = clock;
    }

    /** Each size of the model with its sewn pieces per place; reads without locking the model. */
    @Transactional(readOnly = true)
    public List<SizePlacesDto> get(int productionId) {
        ProductionModelDto model = production.getDto(productionId)
                .orElseThrow(() -> new NotFoundException("Модель " + productionId + " не знайдено."));
        Map<Integer, List<SewnBalance>> bySize = locations.findBalances(productionId).stream()
                .collect(Collectors.groupingBy(SewnBalance::getSizeId));
        Map<Integer, String> names = names();
        return model.sizes().stream()
                .map(s -> sizePlaces(s.id(), s.name(), s.sewnStock(), bySize.getOrDefault(s.id(), List.of()), names))
                .toList();
    }

    /** Moves sewn pieces of a size between two places; the sewn stock and the storefront stay. */
    public List<SizePlacesDto> transfer(int productionId, SewnTransferRequest request, int userId, String username) {
        Map<Integer, String> names = names();
        requireWarehouse(request.fromWarehouseId(), names);
        requireWarehouse(request.toWarehouseId(), names);
        SewnPlacement placement = placement(productionId);
        SewnTransfer transfer = placement.move(request.sizeId(), request.fromWarehouseId(), request.toWarehouseId(), request.quantity(),
                request.comment(), userId, username, LocalDateTime.now(clock));
        placement.created().forEach(locations::add);
        locations.add(transfer);
        return placement.model().getSizes().stream()
                .sorted(Comparator.comparingInt(s -> s.getSortOrder()))
                .map(s -> sizePlaces(s.getId(), s.getName(), s.getSewnStock(), placement.balances(s.getId()), names))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SewnTransferDto> transfers(int productionId) {
        Map<Integer, String> names = names();
        Map<Integer, String> sizes = production.getDto(productionId)
                .map(m -> m.sizes().stream().collect(Collectors.toMap(ProductionSizeDto::id, ProductionSizeDto::name)))
                .orElse(Map.of());
        return locations.listTransfers(productionId).stream()
                .map(t -> new SewnTransferDto(t.getId(), t.getSizeId(), sizes.getOrDefault(t.getSizeId(), ""), t.getFromWarehouseId(),
                        name(t.getFromWarehouseId(), names), t.getToWarehouseId(), name(t.getToWarehouseId(), names), t.getQuantity(),
                        t.getComment(), t.getUsername(), t.getCreatedAt()))
                .toList();
    }

    /** Every size of the active models with its pieces at each place; only warehouses that hold something get a column. */
    @Transactional(readOnly = true)
    public SewnByWarehouseDto byWarehouse() {
        Map<Integer, String> names = names();
        Map<Integer, Map<Integer, BigDecimal>> bySize = new HashMap<>();
        locations.listBalances().forEach(b -> bySize.computeIfAbsent(b.sizeId(), k -> new HashMap<>()).put(b.warehouseId(), b.quantity()));
        List<Integer> columns = bySize.values().stream().flatMap(m -> m.keySet().stream()).distinct()
                .sorted(Comparator.comparing((Integer id) -> name(id, names), NAMES))
                .toList();

        var places = new ArrayList<SewnByWarehouseDto.Place>();
        places.add(new SewnByWarehouseDto.Place(null, StockLocationService.MAIN_NAME));
        columns.forEach(id -> places.add(new SewnByWarehouseDto.Place(id, name(id, names))));
        List<SewnByWarehouseDto.Row> rows = locations.listActiveSizes().stream()
                .map(s -> {
                    Map<Integer, BigDecimal> at = bySize.getOrDefault(s.sizeId(), Map.of());
                    var quantities = new ArrayList<BigDecimal>(columns.size() + 1);
                    quantities.add(s.sewn().subtract(at.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
                    columns.forEach(id -> quantities.add(at.getOrDefault(id, BigDecimal.ZERO)));
                    return new SewnByWarehouseDto.Row(s.productionId(), s.modelName(), s.sizeId(), s.sizeName(), s.sewn(), quantities);
                })
                .toList();
        return new SewnByWarehouseDto(places, rows);
    }

    private SewnPlacement placement(int productionId) {
        ProductionModel model = production.findModel(productionId)
                .orElseThrow(() -> new NotFoundException("Модель " + productionId + " не знайдено."));
        return new SewnPlacement(model, locations.findBalances(productionId));
    }

    private static SizePlacesDto sizePlaces(int sizeId, String sizeName, BigDecimal sewn, Collection<SewnBalance> balances,
            Map<Integer, String> names) {
        BigDecimal located = balances.stream().map(SewnBalance::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        var places = new ArrayList<PlaceDto>();
        places.add(new PlaceDto(null, StockLocationService.MAIN_NAME, sewn.subtract(located)));
        balances.stream()
                .filter(b -> b.getQuantity().signum() != 0)
                .sorted(Comparator.comparing((SewnBalance b) -> name(b.getWarehouseId(), names), NAMES))
                .forEach(b -> places.add(new PlaceDto(b.getWarehouseId(), name(b.getWarehouseId(), names), b.getQuantity())));
        return new SizePlacesDto(sizeId, sizeName, sewn, places);
    }

    private Map<Integer, String> names() {
        return warehouses.list(null).stream().collect(Collectors.toMap(LocationDto::id, LocationDto::name));
    }

    static void requireWarehouse(Integer warehouseId, Map<Integer, String> names) {
        if (warehouseId != null && !names.containsKey(warehouseId)) {
            throw new NotFoundException("Склад " + warehouseId + " не знайдено.");
        }
    }

    private static String name(Integer warehouseId, Map<Integer, String> names) {
        return warehouseId == null ? StockLocationService.MAIN_NAME : names.getOrDefault(warehouseId, "Склад " + warehouseId);
    }
}
