package ua.newerp.infrastructure.locations;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import ua.newerp.application.locations.LocationDto;
import ua.newerp.application.locations.LocationStore;
import ua.newerp.domain.locations.ShopLocationData;

/** The old site's dd_warehouse (structure reconstructed, see db/reconstructed.sql). */
@Repository
class JdbcLocationStore implements LocationStore {

    private static final String COLUMNS = "warehouse_id, name, description, contact, telephone, city, address, type";
    private static final RowMapper<LocationDto> DTO = (rs, n) -> new LocationDto(rs.getInt("warehouse_id"), rs.getString("name"),
            rs.getString("description"), rs.getString("contact"), rs.getString("telephone"), rs.getString("city"), rs.getString("address"),
            rs.getString("type"));

    private final JdbcClient jdbc;

    JdbcLocationStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LocationDto> list(String search) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM dd_warehouse WHERE :search IS NULL OR LOCATE(:search, name) > 0"
                + " OR LOCATE(:search, city) > 0 OR LOCATE(:search, address) > 0 OR LOCATE(:search, type) > 0 ORDER BY warehouse_id")
                .param("search", search)
                .query(DTO).list();
    }

    @Override
    public Optional<LocationDto> get(int id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM dd_warehouse WHERE warehouse_id = :id").param("id", id).query(DTO).optional();
    }

    @Override
    public int add(ShopLocationData data) {
        var keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO dd_warehouse (name, description, contact, telephone, city, address, type, image)
                VALUES (:name, :description, :contact, :telephone, :city, :address, :type, '')
                """)
                .params(params(data))
                .update(keys);
        return keys.getKey().intValue();
    }

    // NewErp does not change the image: it is uploaded in the site's admin.
    @Override
    public boolean update(int id, ShopLocationData data) {
        return jdbc.sql("""
                UPDATE dd_warehouse SET name = :name, description = :description, contact = :contact, telephone = :telephone,
                                        city = :city, address = :address, type = :type
                WHERE warehouse_id = :id
                """)
                .params(params(data)).param("id", id)
                .update() > 0;
    }

    private static Map<String, Object> params(ShopLocationData d) {
        return Map.of("name", d.name(), "description", d.description(), "contact", d.contact(), "telephone", d.telephone(),
                "city", d.city(), "address", d.address(), "type", d.type());
    }

    @Override
    public boolean delete(int id) {
        return jdbc.sql("DELETE FROM dd_warehouse WHERE warehouse_id = :id").param("id", id).update() > 0;
    }

    @Override
    public Set<Integer> existingIds(Collection<Integer> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("SELECT warehouse_id FROM dd_warehouse WHERE warehouse_id IN (:ids)").param("ids", ids)
                .query(Integer.class).list());
    }
}
