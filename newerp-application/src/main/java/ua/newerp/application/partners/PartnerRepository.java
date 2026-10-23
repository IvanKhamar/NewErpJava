package ua.newerp.application.partners;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ua.newerp.domain.partners.FeedProduct;
import ua.newerp.domain.partners.Partner;

public interface PartnerRepository {

    List<PartnerListItemDto> list();

    Optional<PartnerDto> getDto(int id);

    /** The partner with its mappings (to change it or to build the feed). */
    Optional<Partner> get(int id);

    Optional<Partner> getByCode(String code);

    boolean codeExists(String code, Integer exceptId);

    void add(Partner partner);

    void remove(Partner partner);

    List<ShopCategoryDto> listCategories();

    /** Products (disabled ones too) whose name contains term, or with that id. */
    List<PartnerProductPickDto> searchProducts(String term, int limit);

    /** Enabled products from these categories or with these ids, with photos, categories and options. */
    List<FeedProduct> getFeedProducts(Collection<Integer> categoryIds, Collection<Integer> productIds);

    Map<Integer, String> getCategoryNames(Collection<Integer> categoryIds);
}
