package ua.newerp.application.cashbox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import ua.newerp.application.common.PagedResult;
import ua.newerp.domain.cashbox.CashboxDirectoryEntry;
import ua.newerp.domain.cashbox.CashboxRequisites;
import ua.newerp.domain.cashbox.CashboxWarehouse;
import ua.newerp.domain.cashbox.Cashier;
import ua.newerp.domain.cashbox.Cheque;
import ua.newerp.domain.cashbox.WorkShift;
import ua.newerp.domain.cashbox.ZReport;

/**
 * Cash register tables. Every write method is its own transaction: the service calls Checkbox in between,
 * and a database transaction should not wait for an external API.
 */
public interface CashboxRepository {

    List<Cashier> listCashiers();

    Optional<Cashier> getCashier(int id);

    List<CashboxWarehouse> listWarehouses();

    Optional<CashboxWarehouse> getWarehouse(int id);

    List<CashboxRequisites> listRequisites();

    Optional<CashboxRequisites> getRequisites(int id);

    /** Whether cashiers or receipts refer to the warehouse: then it cannot be deleted. */
    boolean warehouseInUse(int id);

    boolean requisitesInUse(int id);

    /** Inserts a new entity (the id is known right after) or saves the changes of a loaded one. */
    <T> T save(T entity);

    void remove(CashboxDirectoryEntry entry);

    /** Open shifts (date_end IS NULL) of all cashiers. */
    List<WorkShift> listOpenShifts();

    Optional<WorkShift> getShift(int id);

    List<WorkShiftDto> listShifts(WorkShiftFilter filter);

    /** Saves the Z report and closes the shifts in one transaction. */
    void saveReport(ZReport report, List<WorkShift> closedShifts);

    List<ZReportDto> listReports(Integer cashierId, LocalDate from, LocalDate to);

    /** The cashier's receipts not closed by a Z report yet, issued in this shift or (without a shift) after it started. */
    List<Cheque> listUnreportedCheques(int cashierId, int shiftId, LocalDateTime since);

    void attachChequesToReport(List<Integer> chequeIds, int reportId);

    Optional<Cheque> getCheque(int id);

    Optional<Cheque> getChequeForOrder(int orderId);

    Optional<ChequeDto> getChequeDto(int id);

    PagedResult<ChequeDto> listCheques(ChequeFilter filter);

    void deleteCheque(int id);

    Optional<OrderForCheque> getOrderForCheque(int orderId);
}
