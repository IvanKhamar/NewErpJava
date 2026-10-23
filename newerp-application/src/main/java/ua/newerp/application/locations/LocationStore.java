package ua.newerp.application.locations;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import ua.newerp.domain.locations.ShopLocationData;

public interface LocationStore {

    List<LocationDto> list(String search);

    Optional<LocationDto> get(int id);

    int add(ShopLocationData data);

    /** false when there is no such record. */
    boolean update(int id, ShopLocationData data);

    boolean delete(int id);

    Set<Integer> existingIds(Collection<Integer> ids);
}
