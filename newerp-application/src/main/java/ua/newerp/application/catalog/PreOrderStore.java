package ua.newerp.application.catalog;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface PreOrderStore {

    /** Product sizes by name or model, not more than limit. */
    List<PreOrderRow> list(PreOrderFilter filter, int limit);

    /** @return false when there is no such size */
    boolean update(int productOptionValueId, int quota, LocalDate dateAvailable);

    /** Takes what was sold ahead off the quotas (of all sizes, or only of these products); returns the number of changed sizes. */
    int settle(Collection<Integer> productIds);
}
