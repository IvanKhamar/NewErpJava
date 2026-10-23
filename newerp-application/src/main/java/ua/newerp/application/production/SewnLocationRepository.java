package ua.newerp.application.production;

import java.math.BigDecimal;
import java.util.List;

import ua.newerp.domain.production.SewnBalance;
import ua.newerp.domain.production.SewnTransfer;

/** Sewn pieces at warehouses and the moves between them (tables erp_production_location, erp_production_transfer). */
public interface SewnLocationRepository {

    /** One size's balance at one warehouse. */
    record BalanceRow(int productionId, int sizeId, int warehouseId, BigDecimal quantity) {
    }

    /** A size of an active model with its sewn stock (the total). */
    record SizeRow(int productionId, String modelName, int sizeId, String sizeName, BigDecimal sewn) {
    }

    /** Sizes of the active models, in the order of the models and of their sizes. */
    List<SizeRow> listActiveSizes();

    /** Balances of all sizes of the model, tracked: change them only after the model is locked (ProductionRepository.findModel). */
    List<SewnBalance> findBalances(int productionId);

    /** Non-zero balances of active models. */
    List<BalanceRow> listBalances();

    boolean holdsStock(int warehouseId);

    /** The model's moves, newest first. */
    List<SewnTransfer> listTransfers(int productionId);

    void add(SewnBalance balance);

    void add(SewnTransfer transfer);
}
