package ua.newerp.application.delivery;

import java.util.List;
import java.util.Optional;

import ua.newerp.domain.delivery.NovaPoshtaWarehouse;

/** Directory of Nova Poshta cities and branches (a local copy shared with the old site). */
public interface WarehouseDirectory {

    /** Cities whose name starts with the search text. */
    List<CityDto> searchCities(String search, int limit);

    /** Working branches of a city whose name contains the search text (empty: all), by number. */
    List<WarehouseDto> searchWarehouses(String cityRef, String search, int limit);

    /** A city by its exact name (as written in the order). */
    Optional<CityDto> findCity(String name);

    /** A branch of the city by its exact name (as written in the order). */
    Optional<WarehouseDto> findWarehouse(String cityRef, String name);

    boolean warehouseExists(String cityRef, String warehouseRef);

    /** Replaces the whole directory in one transaction. */
    void replaceAll(List<NovaPoshtaWarehouse> warehouses);
}
