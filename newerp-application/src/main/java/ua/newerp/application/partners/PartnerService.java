package ua.newerp.application.partners;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.partners.CategoryMapping;
import ua.newerp.domain.partners.FeedCategory;
import ua.newerp.domain.partners.FeedOffer;
import ua.newerp.domain.partners.FeedProduct;
import ua.newerp.domain.partners.Partner;
import ua.newerp.domain.partners.PartnerCategoryMap;
import ua.newerp.domain.partners.PartnerFeed;
import ua.newerp.domain.partners.PartnerFeedBuilder;
import ua.newerp.domain.partners.PartnerProductMap;
import ua.newerp.domain.partners.PartnerSettings;
import ua.newerp.domain.partners.ProductMapping;

/** Reseller partners: settings, category and product mappings, an XML feed for each partner. */
@Service
@Transactional
public class PartnerService {

    private static final int SEARCH_LIMIT = 20;
    private static final DateTimeFormatter FEED_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PartnerRepository repository;
    private final PartnerFeedProperties shop;
    private final Clock clock;

    public PartnerService(PartnerRepository repository, PartnerFeedProperties shop, Clock clock) {
        this.repository = repository;
        this.shop = shop;
        this.clock = clock;
    }

    public int create(SavePartnerRequest request) {
        var partner = new Partner(toSettings(request), now());
        apply(partner, request);
        ensureUniqueCode(partner.getCode(), null);
        repository.add(partner);
        return partner.getId();
    }

    public void update(int id, SavePartnerRequest request) {
        Partner partner = require(id);
        // Checked before the change: the query would otherwise first flush the changed code into the unique key.
        ensureUniqueCode(request.code(), id);
        partner.update(toSettings(request), now());
        apply(partner, request);
    }

    public void delete(int id) {
        repository.remove(require(id));
    }

    @Transactional(readOnly = true)
    public List<PartnerListItemDto> list() {
        return repository.list();
    }

    @Transactional(readOnly = true)
    public Optional<PartnerDto> get(int id) {
        return repository.getDto(id);
    }

    @Transactional(readOnly = true)
    public List<ShopCategoryDto> listCategories() {
        return repository.listCategories();
    }

    @Transactional(readOnly = true)
    public List<PartnerProductPickDto> searchProducts(String term) {
        return term == null || term.isBlank() ? List.of() : repository.searchProducts(term.strip(), SEARCH_LIMIT);
    }

    /** The partner's XML feed in YML format (yml_catalog), as the old site gave it. Empty: no partner or the feed is off. */
    @Transactional(readOnly = true)
    public Optional<String> buildFeedXml(String code) {
        Partner partner = repository.getByCode(code).orElse(null);
        if (partner == null || !partner.isFeedEnabled()) {
            return Optional.empty();
        }

        List<FeedProduct> products = repository.getFeedProducts(
                partner.getCategoryMap().stream().map(PartnerCategoryMap::getCategoryId).distinct().toList(),
                partner.getProductMap().stream().map(PartnerProductMap::getProductId).distinct().toList());
        List<Integer> categoryIds = Stream.concat(partner.getCategoryMap().stream().map(PartnerCategoryMap::getCategoryId),
                products.stream().flatMap(p -> p.categoryIds().stream())).distinct().toList();
        Map<Integer, String> categoryNames = repository.getCategoryNames(categoryIds);

        String storeUrl = shop.storeUrl().strip();
        if (!storeUrl.isEmpty() && !storeUrl.endsWith("/")) {
            storeUrl += "/";
        }
        PartnerFeed feed = PartnerFeedBuilder.build(partner, products, categoryNames, storeUrl);
        return Optional.of(writeXml(feed, partner, storeUrl, now()));
    }

    private String writeXml(PartnerFeed feed, Partner partner, String storeUrl, LocalDateTime now) {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element catalog = append(doc, doc, "yml_catalog");
            catalog.setAttribute("date", now.format(FEED_DATE));
            Element shopElement = append(doc, catalog, "shop");
            text(doc, shopElement, "name", shop.shopName());
            text(doc, shopElement, "company", shop.company());
            text(doc, shopElement, "url", storeUrl);

            Element currency = append(doc, append(doc, shopElement, "currencies"), "currency");
            currency.setAttribute("id", "UAH");
            currency.setAttribute("rate", "1");

            Element categories = append(doc, shopElement, "categories");
            for (FeedCategory category : feed.categories()) {
                Element element = text(doc, categories, "category", category.name());
                element.setAttribute("id", category.id());
                // The attribute is named after the first partner (EVA); its feed already reads this name.
                if (category.externalId() != null) {
                    element.setAttribute("eva_id", category.externalId());
                }
            }

            Element offers = append(doc, shopElement, "offers");
            for (FeedOffer offer : feed.offers()) {
                Element element = append(doc, offers, "offer");
                element.setAttribute("id", offer.id());
                element.setAttribute("available", "true");
                text(doc, element, "price", money(offer.price()));
                if (offer.priceOld() != null) {
                    text(doc, element, "price_old", money(offer.priceOld()));
                }
                text(doc, element, "stock_quantity", Integer.toString(partner.getDefaultQuantity()));
                text(doc, element, "currencyId", "UAH");
                text(doc, element, "categoryId", offer.categoryId());
                for (String picture : offer.pictures()) {
                    text(doc, element, "picture", picture);
                }
                text(doc, element, "vendor", partner.getVendorName());
                if (!offer.model().isEmpty()) {
                    text(doc, element, "article", offer.model());
                }
                if (offer.vendorCode() != null) {
                    text(doc, element, "vendorCode", offer.vendorCode());
                }
                text(doc, element, "name_ua", offer.name());
                append(doc, element, "description_ua").appendChild(doc.createCDATASection(offer.description()));
                if (offer.size() != null) {
                    text(doc, element, "param", offer.size()).setAttribute("name", "Розмір");
                }
                text(doc, element, "param", offer.barcode()).setAttribute("name", "Штрихкод");
            }

            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            var output = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(output));
            return output.toString();
        } catch (ParserConfigurationException | TransformerException e) {
            throw new IllegalStateException("Не вдалося сформувати XML-фід.", e);
        }
    }

    private static Element append(Document doc, Node parent, String name) {
        Element element = doc.createElement(name);
        parent.appendChild(element);
        return element;
    }

    private static Element text(Document doc, Element parent, String name, String value) {
        Element element = append(doc, parent, name);
        element.setTextContent(value);
        return element;
    }

    // Like PHP prints a number: without extra zeros (1250, 1250.5).
    private static String money(BigDecimal value) {
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_EVEN).stripTrailingZeros();
        return (rounded.scale() < 0 ? rounded.setScale(0) : rounded).toPlainString();
    }

    private Partner require(int id) {
        return repository.get(id).orElseThrow(() -> new NotFoundException("Партнера " + id + " не знайдено."));
    }

    private void ensureUniqueCode(String code, Integer exceptId) {
        code = code == null ? "" : code.strip();
        if (repository.codeExists(code, exceptId)) {
            throw new DomainException("Партнер з кодом «" + code + "» уже є.");
        }
    }

    private static void apply(Partner partner, SavePartnerRequest request) {
        partner.setCategoryMap(request.categoryMap().stream().map(r -> new CategoryMapping(r.categoryId(), r.sizeName(),
                r.externalId(), r.externalName(), r.percentSupply(), r.percentSale())).toList());
        partner.setProductMap(request.productMap().stream().map(r -> new ProductMapping(r.productId(), r.sizeName(),
                r.externalId(), r.externalName(), r.priceSupply(), r.priceSale())).toList());
    }

    private static PartnerSettings toSettings(SavePartnerRequest r) {
        return new PartnerSettings(r.code(), r.name(), r.isActive(), r.feedUrl(), r.feedEnabled(), r.vendorName(), r.notes(),
                r.defaultQuantity());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
