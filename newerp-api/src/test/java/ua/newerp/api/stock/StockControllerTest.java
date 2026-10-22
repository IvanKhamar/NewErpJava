package ua.newerp.api.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

import ua.newerp.IntegrationTest;
import ua.newerp.application.stock.StockRepository;

@Sql({"/sql/users.sql", "/sql/stock.sql"})
class StockControllerTest extends IntegrationTest {

    @Autowired
    StockRepository repository;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    DataSource dataSource;

    MockHttpSession keeper;

    @BeforeEach
    void logIn() {
        keeper = login("keeper");
    }

    @Test
    void onlyAdminAndStorekeeper() {
        assertThat(mvc.get().uri("/stock/items").session(login("olena"))).hasStatus(403);
        assertThat(mvc.get().uri("/stock/items").session(login("admin"))).hasStatusOk();
    }

    @Test
    void listsAndFilters() {
        assertThat(mvc.get().uri("/stock/items").session(keeper)).bodyJson()
                .extractingPath("$[*].name").asArray().containsExactly("Бавовна", "Ґудзики 15 мм", "Льон білий", "Нитки");
        assertThat(mvc.get().uri("/stock/items?belowMinimumOnly=true&categoryId=1").session(keeper)).bodyJson()
                .extractingPath("$[*].id").asArray().containsExactly(1);
        assertThat(mvc.get().uri("/stock/items/1").session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"id":1,"name":"Льон білий","unit":"м","categoryName":"Тканини","supplierName":"ТексМаркет",
                 "quantity":3.000,"minQuantity":10.000,"isBelowMinimum":true,"usedFor":"сукні"}
                """);
        assertThat(mvc.get().uri("/stock/items/99").session(keeper)).hasStatus(404);
        assertThat(mvc.get().uri("/stock/categories").session(keeper)).bodyJson().isEqualTo("""
                [{"id":1,"name":"Тканини"},{"id":2,"name":"Фурнітура"}]
                """);
    }

    @Test
    void createAndUpdate() {
        assertThat(mvc.post().uri("/stock/categories").session(keeper).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Тканини\"}"))
                .hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("Категорія \"Тканини\" вже існує.");

        var created = mvc.post().uri("/stock/items").session(keeper).contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":2,\"supplierId\":2,\"name\":\"Блискавки\",\"unit\":\"шт\",\"minQuantity\":10}")
                .exchange();
        assertThat(created).hasStatus(201).bodyJson().extractingPath("$.id").isNotNull();

        assertThat(mvc.put().uri("/stock/items/1").session(keeper).contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":1,\"supplierId\":1,\"name\":\"Льон молочний\",\"unit\":\"м\",\"minQuantity\":12,\"price\":210}"))
                .hasStatus(204);
        assertThat(mvc.get().uri("/stock/items/1").session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"name":"Льон молочний","quantity":3.000,"minQuantity":12.000,"price":210.0000}
                """);
        assertThat(mvc.put().uri("/stock/items/1").session(keeper).contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":77,\"name\":\"X\",\"unit\":\"м\"}"))
                .hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("Категорії 77 не існує.");
    }

    @Test
    void adjustWritesLog() {
        assertThat(mvc.post().uri("/stock/items/2/adjustments").session(keeper).contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":-5.5,\"reason\":\"Крій суконь\"}"))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"itemId":2,"delta":-5.5,"quantityBefore":50.000,"quantityAfter":44.500,"reason":"Крій суконь",
                         "userId":2,"username":"keeper"}
                        """);
        assertThat(mvc.post().uri("/stock/items/2/adjustments").session(keeper).contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":-100,\"reason\":\"x\"}"))
                .hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("Недостатньо залишку: є 44.5 м, списується 100 м.");

        assertThat(mvc.get().uri("/stock/items/2/movements").session(keeper)).bodyJson()
                .isLenientlyEqualTo("[{\"delta\":-5.500,\"quantityAfter\":44.500}]");
        assertThat(mvc.put().uri("/stock/items/2/min-quantity?minQuantity=20").session(keeper)).hasStatus(204);
    }

    @Test
    void itemBeingChangedIsLockedForOthers() {
        tx.executeWithoutResult(status -> {
            repository.findItem(2).orElseThrow(); // SELECT ... FOR UPDATE
            // Another connection (the old site, another user) cannot change the row until this transaction ends
            try (Connection other = dataSource.getConnection(); Statement sql = other.createStatement()) {
                sql.execute("SET SESSION innodb_lock_wait_timeout = 1");
                assertThatThrownBy(() -> sql.executeUpdate("UPDATE dd_stock_items SET stock = 49 WHERE item_id = 2"))
                        .hasMessageContaining("Lock wait timeout");
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    void reorderReport() {
        assertThat(mvc.get().uri("/stock/reorder").session(keeper)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"total":3970,
                 "suppliers":[
                   {"supplierId":2,"supplierName":"Ґудзик і Ко",
                    "lines":[{"itemId":3,"suggested":180,"sum":270.0}],
                    "messageText":"Добрий день! Хочемо замовити:\\n— Ґудзики 15 мм: 180 шт\\nДякуємо!"},
                   {"supplierId":1,"supplierName":"ТексМаркет","lines":[{"itemId":1,"suggested":17,"sum":3400.0}]},
                   {"supplierId":null,"supplierName":"Без постачальника","lines":[{"itemId":4,"suggested":10}]}]}
                """);
    }
}
