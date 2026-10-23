package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.util.List;

import ua.newerp.domain.stock.StockBalance;
import ua.newerp.domain.stock.StockTransfer;

/** Balances of consumables at warehouses and the moves between them (tables erp_stock_location, erp_stock_transfer). */
public interface StockLocationRepository {

    /** One item's balance at one warehouse. */
    record BalanceRow(int itemId, int warehouseId, BigDecimal quantity) {
    }

    /** The item's balances, tracked: change them only after the item itself is locked (StockRepository.findItem). */
    List<StockBalance> findBalances(int itemId);

    /** Non-zero balances of active items. */
    List<BalanceRow> listBalances();

    /** Whether anything still lies at the warehouse. */
    boolean holdsStock(int warehouseId);

    /** The item's moves, newest first. */
    List<StockTransfer> listTransfers(int itemId);

    void add(StockBalance balance);

    void add(StockTransfer transfer);
}
