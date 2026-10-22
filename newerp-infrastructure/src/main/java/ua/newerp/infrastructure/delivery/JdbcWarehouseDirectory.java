package ua.newerp.infrastructure.delivery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.delivery.CityDto;
import ua.newerp.application.delivery.WarehouseDirectory;
import ua.newerp.application.delivery.WarehouseDto;
import ua.newerp.domain.delivery.NovaPoshtaWarehouse;

/**
 * The branch directory in dd_novaposhta_warehouse. There is no separate city table: as on the old site,
 * a city is city_ref + city_description of its branches.
 */
@Repository
@Transactional(readOnly = true)
class JdbcWarehouseDirectory implements WarehouseDirectory {

    /** Rows per INSERT batch on a full refresh (the directory has tens of thousands of branches). */
    private static final int INSERT_BATCH_SIZE = 1000;

    private static final String WAREHOUSE_COLUMNS = "ref, description, category_of_warehouse, weight";

    private final JdbcClient jdbc;
    private final JdbcTemplate template;

    JdbcWarehouseDirectory(JdbcClient jdbc, JdbcTemplate template) {
        this.jdbc = jdbc;
        this.template = template;
    }

    @Override
    public List<CityDto> searchCities(String search, int limit) {
        // LIKE with the search text escaped, so "%" or "_" typed by a person are not wildcards
        return jdbc.sql("""
                SELECT city_ref, city_description FROM dd_novaposhta_warehouse
                WHERE status = 1 AND city_description LIKE :prefix ESCAPE '!'
                GROUP BY city_ref, city_description
                ORDER BY city_description
                LIMIT :limit
                """)
                .param("prefix", escapeLike(search) + "%")
                .param("limit", limit)
                .query((rs, n) -> new CityDto(rs.getString("city_ref"), rs.getString("city_description")))
                .list();
    }

    @Override
    public List<WarehouseDto> searchWarehouses(String cityRef, String search, int limit) {
        return jdbc.sql("SELECT " + WAREHOUSE_COLUMNS + """
                 FROM dd_novaposhta_warehouse
                WHERE city_ref = :city AND status = 1 AND (:search = '' OR LOCATE(:search, description) > 0)
                ORDER BY sort_order, description
                LIMIT :limit
                """)
                .param("city", cityRef)
                .param("search", search)
                .param("limit", limit)
                .query(JdbcWarehouseDirectory::warehouse)
                .list();
    }

    @Override
    public Optional<CityDto> findCity(String name) {
        return jdbc.sql("SELECT city_ref, city_description FROM dd_novaposhta_warehouse WHERE status = 1 AND city_description = :name LIMIT 1")
                .param("name", name)
                .query((rs, n) -> new CityDto(rs.getString("city_ref"), rs.getString("city_description")))
                .optional();
    }

    @Override
    public Optional<WarehouseDto> findWarehouse(String cityRef, String name) {
        return jdbc.sql("SELECT " + WAREHOUSE_COLUMNS
                + " FROM dd_novaposhta_warehouse WHERE city_ref = :city AND status = 1 AND description = :name LIMIT 1")
                .param("city", cityRef)
                .param("name", name)
                .query(JdbcWarehouseDirectory::warehouse)
                .optional();
    }

    @Override
    public boolean warehouseExists(String cityRef, String warehouseRef) {
        return jdbc.sql("SELECT COUNT(*) FROM dd_novaposhta_warehouse WHERE ref = :ref AND city_ref = :city AND status = 1")
                .param("ref", warehouseRef)
                .param("city", cityRef)
                .query(Integer.class)
                .single() > 0;
    }

    @Override
    @Transactional
    public void replaceAll(List<NovaPoshtaWarehouse> warehouses) {
        // One transaction: the old site's shop must not see an empty directory in the middle of the refresh.
        jdbc.sql("DELETE FROM dd_novaposhta_warehouse").update();
        Map<String, NovaPoshtaWarehouse> distinct = new LinkedHashMap<>();
        warehouses.forEach(w -> distinct.putIfAbsent(w.getRef(), w));
        // batchUpdate sends the rows in groups instead of one INSERT per call
        template.batchUpdate("""
                INSERT INTO dd_novaposhta_warehouse
                    (ref, description, category_of_warehouse, city_ref, city_description, weight, sort_order, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, distinct.values(), INSERT_BATCH_SIZE, (ps, w) -> {
            ps.setString(1, w.getRef());
            ps.setString(2, w.getDescription());
            ps.setString(3, w.getCategory());
            ps.setString(4, w.getCityRef());
            ps.setString(5, w.getCityDescription());
            ps.setInt(6, w.getMaxWeight());
            ps.setInt(7, w.getSortOrder());
            ps.setBoolean(8, w.isStatus());
        });
    }

    private static WarehouseDto warehouse(ResultSet rs, int rowNum) throws SQLException {
        return new WarehouseDto(rs.getString("ref"), rs.getString("description"), rs.getString("category_of_warehouse"), rs.getInt("weight"));
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
