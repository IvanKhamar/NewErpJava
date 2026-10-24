package ua.newerp.application.competitors;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.common.Text;
import ua.newerp.domain.competitors.CompetitorSite;
import ua.newerp.domain.competitors.PriceExtractor;

/**
 * Competitor price monitoring (the old CRM's "моніторинг цін конкурентів"): the pages of our products on competitors' sites,
 * a check that reads the price from each page, prices typed in by hand, and our price against theirs.
 */
@Service
@Transactional
public class CompetitorService {

    public static final String AUTO = "auto";
    public static final String MANUAL = "manual";

    private final CompetitorStore store;
    private final PageFetcher fetcher;
    private final Clock clock;

    public CompetitorService(CompetitorStore store, PageFetcher fetcher, Clock clock) {
        this.store = store;
        this.fetcher = fetcher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CompetitorDto> competitors() {
        return store.competitors();
    }

    public CompetitorDto createCompetitor(SaveCompetitorRequest request) {
        Valid v = validate(request);
        return requireCompetitor(store.addCompetitor(v.name(), v.siteUrl(), v.regex(), request.active(), LocalDateTime.now(clock)));
    }

    public CompetitorDto updateCompetitor(int id, SaveCompetitorRequest request) {
        requireCompetitor(id);
        Valid v = validate(request);
        store.updateCompetitor(id, v.name(), v.siteUrl(), v.regex(), request.active());
        return requireCompetitor(id);
    }

    @Transactional(readOnly = true)
    public List<CompetitorLinkDto> links(Integer productId) {
        return store.links(productId);
    }

    public CompetitorLinkDto addLink(AddCompetitorLinkRequest request) {
        CompetitorDto competitor = requireCompetitor(request.competitorId());
        if (!store.productExists(request.productId())) {
            throw new NotFoundException("Товар " + request.productId() + " не знайдено.");
        }
        URI page = CompetitorSite.requireUrl(request.url(), "Сторінка товару", 500);
        String site = CompetitorSite.host(URI.create(competitor.siteUrl()));
        if (!CompetitorSite.isOnSite(page, site)) {
            throw new DomainException("Сторінка має бути на сайті «" + competitor.name() + "» (" + site + ").");
        }
        if (store.linkExists(competitor.id(), request.productId())) {
            throw new DomainException("Для цього товару в «" + competitor.name() + "» сторінка вже є.");
        }
        return requireLink(store.addLink(competitor.id(), request.productId(), page.toString(), LocalDateTime.now(clock)));
    }

    public void deleteLink(int id) {
        requireLink(id);
        store.deleteLink(id);
    }

    public CompetitorLinkDto setPrice(int linkId, CompetitorPriceRequest request) {
        requireLink(linkId);
        BigDecimal price = request.price();
        if (price == null || price.signum() <= 0 || price.stripTrailingZeros().scale() > 2) {
            throw new DomainException("Ціна має бути більшою за нуль, не більше двох знаків після коми.");
        }
        store.saveCheck(linkId, price, MANUAL, "", LocalDateTime.now(clock));
        return requireLink(linkId);
    }

    @Transactional(readOnly = true)
    public List<CompetitorPriceDto> history(int linkId) {
        requireLink(linkId);
        return store.history(linkId);
    }

    /** Reads the price from one page now. The page is downloaded outside a database transaction. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public CompetitorLinkDto check(int linkId) {
        CompetitorLinkDto link = requireLink(linkId);
        CompetitorDto competitor = requireCompetitor(link.competitorId());
        check(link, competitor);
        return requireLink(linkId);
    }

    /** Reads the prices of all pages of the active competitors; one failing page does not stop the rest. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public CompetitorCheckResult checkAll() {
        Map<Integer, CompetitorDto> competitors = new LinkedHashMap<>();
        store.competitors().forEach(c -> competitors.put(c.id(), c));
        int changed = 0;
        int failed = 0;
        List<CompetitorLinkDto> links = store.activeLinks();
        for (CompetitorLinkDto link : links) {
            Outcome outcome = check(link, competitors.get(link.competitorId()));
            changed += outcome == Outcome.CHANGED ? 1 : 0;
            failed += outcome == Outcome.FAILED ? 1 : 0;
        }
        return new CompetitorCheckResult(links.size(), changed, failed);
    }

    @Transactional(readOnly = true)
    public CompetitorReportDto report() {
        List<CompetitorDto> active = store.competitors().stream().filter(CompetitorDto::active).toList();
        List<CompetitorLinkDto> links = store.links(null);
        Map<Integer, Map<Integer, BigDecimal>> byProduct = new LinkedHashMap<>();
        Map<Integer, String> names = new LinkedHashMap<>();
        for (CompetitorLinkDto link : links) {
            names.putIfAbsent(link.productId(), link.productName());
            Map<Integer, BigDecimal> prices = byProduct.computeIfAbsent(link.productId(), k -> new LinkedHashMap<>());
            if (link.lastPrice() != null) {
                prices.put(link.competitorId(), link.lastPrice());
            }
        }
        Map<Integer, BigDecimal> ours = store.ourPrices(new ArrayList<>(byProduct.keySet()));
        List<CompetitorReportDto.Row> rows = byProduct.entrySet().stream()
                .map(e -> {
                    List<BigDecimal> prices = active.stream().map(c -> e.getValue().get(c.id())).toList();
                    Optional<BigDecimal> lowest = prices.stream().filter(Objects::nonNull).min(Comparator.naturalOrder());
                    BigDecimal our = ours.getOrDefault(e.getKey(), BigDecimal.ZERO);
                    BigDecimal difference = lowest.filter(l -> our.signum() > 0)
                            .map(l -> our.subtract(l).multiply(BigDecimal.valueOf(100)).divide(l, 1, RoundingMode.HALF_UP))
                            .orElse(null);
                    return new CompetitorReportDto.Row(e.getKey(), names.get(e.getKey()), our, prices, lowest.orElse(null), difference);
                })
                .sorted(Comparator.comparing(CompetitorReportDto.Row::productName))
                .toList();
        return new CompetitorReportDto(active.stream().map(c -> new CompetitorReportDto.Column(c.id(), c.name())).toList(), rows);
    }

    private enum Outcome { SAME, CHANGED, FAILED }

    private Outcome check(CompetitorLinkDto link, CompetitorDto competitor) {
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            String html = fetcher.fetch(URI.create(link.url()), CompetitorSite.host(URI.create(competitor.siteUrl())));
            Optional<BigDecimal> price = PriceExtractor.extract(html, competitor.priceRegex())
                    .map(p -> p.setScale(2, RoundingMode.HALF_UP));
            if (price.isEmpty()) {
                store.saveCheck(link.id(), null, AUTO, "Ціну на сторінці не знайдено.", now);
                return Outcome.FAILED;
            }
            store.saveCheck(link.id(), price.get(), AUTO, "", now);
            return link.lastPrice() != null && link.lastPrice().compareTo(price.get()) == 0 ? Outcome.SAME : Outcome.CHANGED;
        } catch (PageFetcher.PageFetchException e) {
            store.saveCheck(link.id(), null, AUTO, Text.truncate(e.getMessage(), 255), now);
            return Outcome.FAILED;
        }
    }

    private record Valid(String name, String siteUrl, String regex) {
    }

    private static Valid validate(SaveCompetitorRequest r) {
        String name = Text.require(r.name(), "Назва конкурента", 100);
        URI site = CompetitorSite.requireUrl(r.siteUrl(), "Сайт", 255);
        return new Valid(name, site.toString(), PriceExtractor.requireRegex(r.priceRegex()));
    }

    private CompetitorDto requireCompetitor(int id) {
        return store.competitor(id).orElseThrow(() -> new NotFoundException("Конкурента " + id + " не знайдено."));
    }

    private CompetitorLinkDto requireLink(int id) {
        return store.link(id).orElseThrow(() -> new NotFoundException("Сторінку " + id + " не знайдено."));
    }
}
