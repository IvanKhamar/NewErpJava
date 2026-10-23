package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.Spreadsheet;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.stock.StockItem;
import ua.newerp.domain.stock.StockMovement;
import ua.newerp.domain.stock.Stocktaking;

/** Stocktaking of consumables: a sheet to count by, a preview of the uploaded counts, and applying them with log entries. */
@Service
@Transactional
public class StocktakingService {

    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    private final StockRepository repository;
    private final Spreadsheet spreadsheet;
    private final Clock clock;

    public StocktakingService(StockRepository repository, Spreadsheet spreadsheet, Clock clock) {
        this.repository = repository;
        this.spreadsheet = spreadsheet;
        this.clock = clock;
    }

    /** Active items (of one category when given) with the booked quantity and an empty "Фактично" column. */
    @Transactional(readOnly = true)
    public byte[] sheet(Integer categoryId) {
        List<List<Object>> rows = repository.listItems(new StockItemFilter(categoryId, false)).stream()
                .map(i -> Arrays.<Object>asList(i.id(), i.name(), i.categoryName() == null ? "" : i.categoryName(), i.unit(),
                        i.quantity(), null))
                .toList();
        return spreadsheet.write("Інвентаризація", Stocktaking.COLUMNS, rows);
    }

    /** Reads an uploaded sheet; nothing is written. */
    @Transactional(readOnly = true)
    public StocktakingPreviewDto preview(byte[] file) {
        if (file.length > MAX_FILE_BYTES) {
            throw new DomainException("Файл більший за 5 МБ.");
        }
        Stocktaking.Parsed parsed = Stocktaking.parse(spreadsheet.read(file));
        Map<Integer, StockItemDto> items = activeItems();
        var errors = new ArrayList<>(parsed.errors());
        var lines = new ArrayList<StocktakingLineDto>();
        for (Stocktaking.Count count : parsed.counts()) {
            StockItemDto item = items.get(count.itemId());
            if (item == null) {
                errors.add("Позиції " + count.itemId() + " немає на складі.");
                continue;
            }
            lines.add(new StocktakingLineDto(item.id(), item.name(), item.unit(), item.quantity(), count.actual(),
                    count.actual().subtract(item.quantity())));
        }
        lines.sort(Comparator.comparing(StocktakingLineDto::name));
        return new StocktakingPreviewDto(lines, errors);
    }

    /**
     * Sets every counted item to its counted quantity in one transaction; each difference is a log entry with the comment
     * "Інвентаризація". If any count is wrong, nothing is written.
     */
    public StocktakingResult apply(ApplyStocktakingRequest request, int userId, String username) {
        if (request.counts().isEmpty()) {
            throw new DomainException("Немає жодної порахованої позиції.");
        }
        var seen = new HashSet<Integer>();
        request.counts().stream().filter(c -> !seen.add(c.itemId())).findFirst().ifPresent(dup -> {
            throw new DomainException("Позицію " + dup.itemId() + " указано двічі.");
        });
        String comment = request.comment() == null || request.comment().isBlank()
                ? "Інвентаризація"
                : "Інвентаризація: " + request.comment().strip();
        comment = comment.length() > 255 ? comment.substring(0, 255) : comment;

        // Sorted by id, so two stocktakings at once lock the rows in the same order and cannot deadlock
        List<StocktakingCountRequest> counts = request.counts().stream().sorted(Comparator.comparingInt(StocktakingCountRequest::itemId))
                .toList();
        LocalDateTime now = LocalDateTime.now(clock);
        int changed = 0;
        BigDecimal value = BigDecimal.ZERO;
        for (StocktakingCountRequest count : counts) {
            StockItem item = repository.findItem(count.itemId()).filter(StockItem::isActive)
                    .orElseThrow(() -> new DomainException("Позиції " + count.itemId() + " немає на складі."));
            StockMovement movement;
            try {
                movement = item.recount(count.actual(), comment, userId, username, now);
            } catch (DomainException e) {
                throw new DomainException(item.getName() + ": " + e.getMessage());
            }
            if (movement != null) {
                repository.add(movement);
                changed++;
                value = value.add(movement.getDelta().multiply(item.getPrice()));
            }
        }
        return new StocktakingResult(counts.size(), changed, value.setScale(2, RoundingMode.HALF_UP));
    }

    private Map<Integer, StockItemDto> activeItems() {
        return repository.listItems(new StockItemFilter(null, false)).stream()
                .collect(Collectors.toMap(StockItemDto::id, Function.identity()));
    }
}
