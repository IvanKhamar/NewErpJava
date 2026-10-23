package ua.newerp.application.production;

import static ua.newerp.domain.common.Decimals.less;
import static ua.newerp.domain.common.Decimals.text;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.production.BomResolver;
import ua.newerp.domain.production.ComponentBomLine;
import ua.newerp.domain.production.ComponentStock;
import ua.newerp.domain.production.ProductionComponent;
import ua.newerp.domain.production.ProductionLogEntry;
import ua.newerp.domain.production.ProductionModel;
import ua.newerp.domain.production.ProductionSize;
import ua.newerp.domain.production.SewnBomLine;
import ua.newerp.domain.stock.StockItem;
import ua.newerp.domain.stock.StockMovement;

/**
 * Production use cases (based on the old site's StockTrack module): models, sizes, cut components, BOMs,
 * cutting and sewing operations that write off consumables.
 */
@Service
@Transactional
public class ProductionService {

    private final ProductionRepository repository;
    private final StorefrontStock storefront;
    private final Clock clock;

    public ProductionService(ProductionRepository repository, StorefrontStock storefront, Clock clock) {
        this.repository = repository;
        this.storefront = storefront;
        this.clock = clock;
    }

    /**
     * Runs an operation for several sizes at once. Cutting and sewing write off consumables and components by the
     * BOMs: if something is missing, what there is gets written off and the shortage goes to warnings.
     * Everything is saved in one transaction.
     */
    public ProductionOperationResult record(int productionId, ProductionOperation operation, ProductionOperationRequest request,
            int userId, String username) {
        ProductionModel model = requireModel(productionId);
        List<SizeQuantity> rows = request.quantities();
        if (rows.isEmpty()) {
            throw new DomainException("Вкажіть кількість хоча б для одного розміру.");
        }
        var seen = new HashSet<Integer>();
        rows.stream().filter(r -> !seen.add(r.sizeId())).findFirst().ifPresent(dup -> {
            throw new DomainException("Розмір " + dup.sizeId() + " указано двічі.");
        });

        LocalDateTime now = LocalDateTime.now(clock);
        Consumption consumption = operation == ProductionOperation.CUT || operation == ProductionOperation.SEWN
                ? loadConsumption(model)
                : null;
        var changes = new ArrayList<SizeChangeDto>();

        for (SizeQuantity row : rows) {
            int sizeId = row.sizeId();
            BigDecimal quantity = row.quantity();
            // switch expression over an enum: like a C# switch expression, the compiler checks every value is covered
            ProductionLogEntry entry = switch (operation) {
                case CUT -> model.recordCut(sizeId, quantity, request.comment(), userId, username, now);
                case SEND_TO_SEWING -> model.sendToSewing(sizeId, quantity, request.comment(), userId, username, now);
                case SEWN -> model.recordSewn(sizeId, quantity, request.comment(), userId, username, now);
                case SHIP -> model.ship(sizeId, quantity, request.comment(), userId, username, now);
            };
            repository.add(entry);
            changes.add(new SizeChangeDto(sizeId, entry.getSizeName(), entry.getStockBefore(), entry.getStockAfter()));

            if (operation == ProductionOperation.CUT) {
                consumption.forCut(entry.getSizeName(), sizeId, quantity, userId, username, now);
            } else if (operation == ProductionOperation.SEWN) {
                consumption.forSewing(entry.getSizeName(), sizeId, quantity, userId, username, now);
            }
        }

        // As in the old module: after the sewn stock changes it goes to the storefront at once
        if (operation == ProductionOperation.SEWN || operation == ProductionOperation.SHIP) {
            storefront.push(List.of(productionId));
        }
        return new ProductionOperationResult(changes, consumption == null ? List.of() : consumption.warnings);
    }

    /**
     * Stocktaking of a model: the cut and sewn stock of each counted size and each counted component stock become what was
     * counted. Size differences go into the production log with the comment "Інвентаризація". One transaction: a wrong
     * count writes nothing.
     */
    public ProductionStocktakingResult recount(int productionId, ProductionStocktakingRequest request, int userId, String username) {
        ProductionModel model = requireModel(productionId);
        if (request.sizes().isEmpty() && request.components().isEmpty()) {
            throw new DomainException("Немає жодної порахованої кількості.");
        }
        var seenSizes = new HashSet<Integer>();
        request.sizes().stream().filter(r -> !seenSizes.add(r.sizeId())).findFirst().ifPresent(dup -> {
            throw new DomainException("Розмір " + dup.sizeId() + " указано двічі.");
        });
        var seenComponents = new HashSet<List<Integer>>();
        request.components().stream().filter(r -> !seenComponents.add(List.of(r.componentId(), r.sizeId()))).findFirst()
                .ifPresent(dup -> {
                    throw new DomainException("Деталь " + dup.componentId() + " розміру " + dup.sizeId() + " указано двічі.");
                });
        String comment = request.comment() == null || request.comment().isBlank()
                ? "Інвентаризація"
                : "Інвентаризація: " + request.comment().strip();
        comment = comment.length() > 255 ? comment.substring(0, 255) : comment;

        LocalDateTime now = LocalDateTime.now(clock);
        var cut = new ArrayList<SizeChangeDto>();
        var sewn = new ArrayList<SizeChangeDto>();
        for (SizeCountRequest row : request.sizes()) {
            recountStage(model, row.sizeId(), ProductionLogEntry.CUT, row.cut(), comment, userId, username, now, cut);
            recountStage(model, row.sizeId(), ProductionLogEntry.SEWN, row.sewn(), comment, userId, username, now, sewn);
        }

        int components = 0;
        if (!request.components().isEmpty()) {
            List<Integer> own = repository.componentIds(productionId);
            Map<List<Integer>, ComponentStock> stocks = new HashMap<>();
            repository.findComponentStocks(own).forEach(s -> stocks.put(List.of(s.getComponentId(), s.getSizeId()), s));
            for (ComponentCountRequest row : request.components()) {
                if (!own.contains(row.componentId())) {
                    throw new DomainException("Деталь " + row.componentId() + " не належить моделі \"" + model.getName() + "\".");
                }
                model.requireSize(row.sizeId());
                ComponentStock stock = stocks.get(List.of(row.componentId(), row.sizeId()));
                if (stock == null) {
                    stock = new ComponentStock(row.componentId(), row.sizeId());
                    if (!stock.recount(row.actual())) {
                        continue;
                    }
                    repository.add(stock);
                    components++;
                } else if (stock.recount(row.actual())) {
                    components++;
                }
            }
        }

        if (!sewn.isEmpty()) {
            storefront.push(List.of(productionId));
        }
        return new ProductionStocktakingResult(cut, sewn, components);
    }

    private void recountStage(ProductionModel model, int sizeId, String stage, BigDecimal actual, String comment, int userId,
            String username, LocalDateTime now, List<SizeChangeDto> changes) {
        if (actual == null) {
            return;
        }
        ProductionLogEntry entry = model.recount(sizeId, stage, actual, comment, userId, username, now);
        if (entry != null) {
            repository.add(entry);
            changes.add(new SizeChangeDto(sizeId, entry.getSizeName(), entry.getStockBefore(), entry.getStockAfter()));
        }
    }

    /** Passes the sewn stock of all active models to the storefront (for example, after changes on the old site). */
    public StorefrontSyncResult pushStorefront() {
        return storefront.push(null);
    }

    private Consumption loadConsumption(ProductionModel model) {
        List<ProductionComponent> components = repository.findComponents(model.getId());
        List<Integer> itemIds = Stream.concat(
                components.stream().flatMap(c -> c.getBom().stream()).map(ComponentBomLine::getItemId),
                model.getSewnBom().stream().map(SewnBomLine::getItemId).filter(Objects::nonNull))
                .distinct().toList();
        List<StockItem> items = repository.findStockItems(itemIds);
        List<ComponentStock> stocks = repository.findComponentStocks(components.stream().map(ProductionComponent::getId).toList());
        return new Consumption(model, components, items, stocks);
    }

    /**
     * Write-off of consumables and components for one operation (state shared by all sizes of the request).
     * A non-static inner class: like a nested class in C# that also sees the outer object's fields (repository).
     */
    private final class Consumption {

        private final ProductionModel model;
        private final List<ProductionComponent> components;
        private final Map<Integer, StockItem> items;
        private final Map<List<Integer>, ComponentStock> stocks;
        final List<String> warnings = new ArrayList<>();

        Consumption(ProductionModel model, List<ProductionComponent> components, List<StockItem> items, List<ComponentStock> stocks) {
            this.model = model;
            this.components = components;
            this.items = items.stream().collect(Collectors.toMap(StockItem::getId, Function.identity()));
            this.stocks = new HashMap<>();
            stocks.forEach(s -> this.stocks.put(List.of(s.getComponentId(), s.getSizeId()), s));
        }

        /** +Cut: each component gets +quantity in stock, and its consumables are written off by the cutting BOM. */
        void forCut(String sizeName, int sizeId, BigDecimal quantity, int userId, String username, LocalDateTime now) {
            for (ProductionComponent component : components) {
                for (ComponentBomLine line : BomResolver.resolve(component.getBom(), sizeId, ComponentBomLine::getSizeId,
                        ComponentBomLine::getItemId)) {
                    takeItem(line.getItemId(), line.getQtyPerUnit().multiply(quantity),
                            "Крій: " + model.getName() + ", " + component.getName() + ", " + sizeName + " x" + text(quantity),
                            userId, username, now);
                }
                stockOf(component.getId(), sizeId).add(quantity);
            }
        }

        /** +Sewn: cut components come from their stock, direct consumables (threads, labels) from the stock module. */
        void forSewing(String sizeName, int sizeId, BigDecimal quantity, int userId, String username, LocalDateTime now) {
            for (SewnBomLine line : BomResolver.resolve(model.getSewnBom(), sizeId, SewnBomLine::getSizeId, SewnBomLine::getComponentId)) {
                BigDecimal need = line.getQtyPerUnit().multiply(quantity);
                BigDecimal taken = stockOf(line.getComponentId(), sizeId).takeUpTo(need);
                if (less(taken, need)) {
                    String name = components.stream()
                            .filter(c -> c.getId().equals(line.getComponentId()))
                            .map(ProductionComponent::getName)
                            .findFirst()
                            .orElse("деталь " + line.getComponentId());
                    warnings.add("Бракує розкроєних деталей «" + name + "» розміру " + sizeName + ": "
                            + text(need.subtract(taken)) + " шт. Взято " + text(taken) + " з " + text(need) + ".");
                }
            }

            for (SewnBomLine line : BomResolver.resolve(model.getSewnBom(), sizeId, SewnBomLine::getSizeId, SewnBomLine::getItemId)) {
                takeItem(line.getItemId(), line.getQtyPerUnit().multiply(quantity),
                        "Пошив: " + model.getName() + ", " + sizeName + " x" + text(quantity), userId, username, now);
            }
        }

        private void takeItem(int itemId, BigDecimal need, String comment, int userId, String username, LocalDateTime now) {
            StockItem item = items.get(itemId);
            if (item == null) {
                warnings.add("Розхідника " + itemId + " зі специфікації немає на складі, його не списано.");
                return;
            }

            // Long model and component names may not fit into the log comment (255 characters).
            StockMovement movement = item.takeUpTo(need, comment.length() > 255 ? comment.substring(0, 255) : comment,
                    userId, username, now);
            if (movement != null) {
                repository.add(movement);
            }
            BigDecimal taken = movement == null ? BigDecimal.ZERO : movement.getQuantity();
            if (less(taken, need)) {
                warnings.add("Бракує " + text(need.subtract(taken)) + " " + item.getUnit() + ": " + item.getName()
                        + ". Списано " + text(taken) + " з " + text(need) + ".");
            }
        }

        private ComponentStock stockOf(int componentId, int sizeId) {
            // computeIfAbsent = GetValueOrDefault + add in one call
            return stocks.computeIfAbsent(List.of(componentId, sizeId), key -> {
                var stock = new ComponentStock(componentId, sizeId);
                repository.add(stock);
                return stock;
            });
        }
    }

    public int createModel(SaveProductionModelRequest request) {
        var model = new ProductionModel(request.name(), request.type(), request.minTotal(), request.sortOrder());
        model.setSizes(toSizes(request));
        repository.add(model);
        return model.getId();
    }

    public void updateModel(int productionId, SaveProductionModelRequest request) {
        ProductionModel model = requireModel(productionId);
        model.update(request.name(), request.type(), request.minTotal(), request.sortOrder());

        List<Integer> removed = model.setSizes(toSizes(request));
        if (!removed.isEmpty() && repository.sizesInUse(removed)) {
            throw new DomainException("Прибраний розмір використовується в специфікації крою або має розкроєні деталі.");
        }
    }

    /** As in the old module: the model is not deleted but marked inactive. */
    public void deactivateModel(int productionId) {
        requireModel(productionId).deactivate();
    }

    public void setLinks(int productionId, SetProductionLinksRequest request) {
        ProductionModel model = requireModel(productionId);
        model.setLinks(request.productIds(), request.categories().stream()
                .map(c -> new ProductionModel.CategoryLinkInput(c.categoryId(), c.nameFilter(), c.matchExact()))
                .toList());
        model.setSizeOptions(request.sizeOptions().stream()
                .map(o -> new ProductionModel.SizeOption(o.sizeId(), o.optionValueId()))
                .toList());
        storefront.push(List.of(productionId));
    }

    public int addComponent(int productionId, SaveComponentRequest request) {
        requireModel(productionId);
        var component = new ProductionComponent(productionId, request.name(), request.sortOrder());
        repository.add(component);
        return component.getId();
    }

    public void updateComponent(int componentId, SaveComponentRequest request) {
        requireComponent(componentId).rename(request.name(), request.sortOrder());
    }

    /**
     * Deletes a component with its cutting BOM, stock rows and sewing BOM rows.
     * Unlike the old module, a component with non-zero stock cannot be deleted, so nothing cut gets lost.
     */
    public void deleteComponent(int componentId) {
        ProductionComponent component = requireComponent(componentId);
        List<ComponentStock> stock = repository.findComponentStock(componentId);
        if (stock.stream().anyMatch(s -> s.getStock().signum() != 0)) {
            throw new DomainException("Деталь \"" + component.getName() + "\" має розкроєний запас, її не можна видалити.");
        }

        requireModel(component.getProductionId()).removeComponentFromSewnBom(componentId);
        stock.forEach(repository::remove);
        repository.remove(component);
    }

    public void setComponentBom(int componentId, SetComponentBomRequest request) {
        ProductionComponent component = requireComponent(componentId);
        ProductionModel model = requireModel(component.getProductionId());
        List<ProductionComponent.BomRow> rows = request.rows().stream()
                .map(r -> new ProductionComponent.BomRow(r.sizeId(), r.itemId(), r.qtyPerUnit()))
                .toList();
        requireItems(rows.stream().map(ProductionComponent.BomRow::itemId).toList());

        component.setBom(rows, model.getSizes().stream().map(ProductionSize::getId).toList());
    }

    public void setSewnBom(int productionId, SetSewnBomRequest request) {
        ProductionModel model = requireModel(productionId);
        List<ProductionModel.SewnBomRow> rows = request.rows().stream()
                .map(r -> new ProductionModel.SewnBomRow(r.sizeId(), r.componentId(), r.itemId(), r.qtyPerUnit()))
                .toList();
        requireItems(rows.stream().map(ProductionModel.SewnBomRow::itemId).filter(Objects::nonNull).toList());

        model.setSewnBom(rows, repository.componentIds(productionId));
    }

    private static List<ProductionModel.SizeInput> toSizes(SaveProductionModelRequest request) {
        return request.sizes().stream().map(s -> new ProductionModel.SizeInput(s.name(), s.percent())).toList();
    }

    private void requireItems(List<Integer> itemIds) {
        List<Integer> missing = repository.missingStockItemIds(itemIds.stream().distinct().toList());
        if (!missing.isEmpty()) {
            throw new DomainException("Розхідників " + missing.stream().map(String::valueOf).collect(Collectors.joining(", "))
                    + " немає на складі.");
        }
    }

    private ProductionModel requireModel(int productionId) {
        return repository.findModel(productionId)
                .filter(ProductionModel::isActive)
                .orElseThrow(() -> new NotFoundException("Модель " + productionId + " не знайдено."));
    }

    private ProductionComponent requireComponent(int componentId) {
        return repository.findComponent(componentId)
                .orElseThrow(() -> new NotFoundException("Деталь " + componentId + " не знайдено."));
    }
}
