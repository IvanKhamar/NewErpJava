package ua.newerp.application.cashbox;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.application.common.PagedResult;
import ua.newerp.domain.cashbox.CashboxDirectoryEntry;
import ua.newerp.domain.cashbox.CashboxRequisites;
import ua.newerp.domain.cashbox.CashboxWarehouse;
import ua.newerp.domain.cashbox.Cashier;
import ua.newerp.domain.cashbox.Cheque;
import ua.newerp.domain.cashbox.FiscalReceipt;
import ua.newerp.domain.cashbox.WorkShift;
import ua.newerp.domain.cashbox.ZReport;
import ua.newerp.domain.common.DomainException;

/**
 * The Checkbox cash register (software fiscal register), ported from the old site's cashbox module: cashiers, shifts,
 * fiscal receipts for orders, X and Z reports and closing shifts automatically.
 * No transaction around a whole method: methods call Checkbox, and each repository write is its own transaction.
 */
@Service
public class CashboxService {

    /** Like the old site, auto-close leaves a just opened shift alone. */
    public static final Duration AUTO_CLOSE_GRACE = Duration.ofMinutes(30);

    private static final Comparator<WorkShift> BY_START = Comparator.comparing(WorkShift::getDateStart);

    private final CashboxRepository repository;
    private final CheckboxClient client;
    private final Clock clock;

    public CashboxService(CashboxRepository repository, CheckboxClient client, Clock clock) {
        this.repository = repository;
        this.client = client;
        this.clock = clock;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    // ─── Setup: cashiers, warehouses, requisites ───

    public CashboxSetupDto setup() {
        List<Cashier> cashiers = repository.listCashiers();
        List<CashboxWarehouse> warehouses = repository.listWarehouses();
        List<CashboxRequisites> requisites = repository.listRequisites();
        Map<Integer, WorkShift> openShifts = repository.listOpenShifts().stream()
                .collect(Collectors.toMap(WorkShift::getCashierId, Function.identity(), (a, b) -> BY_START.compare(a, b) >= 0 ? a : b));

        List<CashierDto> dtos = cashiers.stream()
                // Cashiers with a payment code first, then the default ones
                .sorted(Comparator.comparing((Cashier c) -> c.getPaymentCode().isEmpty()).thenComparing(Cashier::getName))
                .map(c -> {
                    WorkShift shift = openShifts.get(c.getId());
                    return new CashierDto(c.getId(), c.getName(), c.getDescription(), c.getPaymentCode(), c.getAutoCloseTime(),
                            c.getWarehouseId(), nameOf(warehouses, c.getWarehouseId()),
                            c.getRequisitesId(), nameOf(requisites, c.getRequisitesId()),
                            c.isActive(), client.canUse(c.getId()),
                            shift == null ? null : shift.getId(), shift == null ? null : shift.getDateStart());
                })
                .toList();

        return new CashboxSetupDto(dtos, entries(warehouses), entries(requisites), client.isFake());
    }

    public int createCashier(SaveCashierRequest request) {
        checkReferences(request);
        var cashier = new Cashier(request.name(), request.description(), request.paymentCode(), request.autoCloseTime(),
                request.warehouseId(), request.requisitesId(), request.isActive(), now());
        return repository.save(cashier).getId();
    }

    public void updateCashier(int id, SaveCashierRequest request) {
        Cashier cashier = requireCashier(id);
        checkReferences(request);
        cashier.update(request.name(), request.description(), request.paymentCode(), request.autoCloseTime(),
                request.warehouseId(), request.requisitesId(), request.isActive());
        repository.save(cashier);
    }

    public int createWarehouse(SaveDirectoryEntryRequest request) {
        return repository.save(new CashboxWarehouse(request.name(), request.description(), now())).getId();
    }

    public void updateWarehouse(int id, SaveDirectoryEntryRequest request) {
        CashboxWarehouse warehouse = requireWarehouse(id);
        warehouse.rename(request.name(), request.description());
        repository.save(warehouse);
    }

    /** The old site deleted without a check, and cashiers and receipts were left pointing nowhere. */
    public void deleteWarehouse(int id) {
        CashboxWarehouse warehouse = requireWarehouse(id);
        if (repository.warehouseInUse(id)) {
            throw new DomainException("Склад указано в касирів чи чеках, видалити його не можна.");
        }
        repository.remove(warehouse);
    }

    public int createRequisites(SaveDirectoryEntryRequest request) {
        return repository.save(new CashboxRequisites(request.name(), request.description(), now())).getId();
    }

    public void updateRequisites(int id, SaveDirectoryEntryRequest request) {
        CashboxRequisites requisites = requireRequisites(id);
        requisites.rename(request.name(), request.description());
        repository.save(requisites);
    }

    public void deleteRequisites(int id) {
        CashboxRequisites requisites = requireRequisites(id);
        if (repository.requisitesInUse(id)) {
            throw new DomainException("Реквізити вказано в касирів чи чеках, видалити їх не можна.");
        }
        repository.remove(requisites);
    }

    // ─── Shifts and reports ───

    public List<WorkShiftDto> shifts(WorkShiftFilter filter) {
        return repository.listShifts(filter);
    }

    public List<ZReportDto> reports(Integer cashierId, LocalDate from, LocalDate to) {
        return repository.listReports(cashierId, from, to);
    }

    /**
     * Opens the cashier's shift. Like the old site, if the shift is already open in Checkbox (for example, from another place),
     * it takes that one instead of opening a second.
     */
    public WorkShiftDto openShift(int cashierId, int userId) {
        Cashier cashier = requireUsableCashier(cashierId);
        if (!cashier.isActive()) {
            throw new DomainException("Касир «" + cashier.getName() + "» вимкнений.");
        }
        if (openShiftOf(cashierId).isPresent()) {
            throw new DomainException("У касира «" + cashier.getName() + "» уже відкрита зміна.");
        }

        String checkboxShiftId = client.openShift(cashierId);
        WorkShift shift = repository.save(new WorkShift(cashierId, checkboxShiftId, userId, now()));
        return repository.listShifts(new WorkShiftFilter(cashierId, null, null)).stream()
                .filter(s -> s.id() == shift.getId()).findFirst().orElseThrow();
    }

    /**
     * Closes the cashier's shift in Checkbox (Z report) and here.
     *
     * @return empty if Checkbox is still closing the shift: then the shift check (manual or automatic) writes the Z report
     */
    public Optional<ZReportDto> closeShift(int cashierId, int userId) {
        requireUsableCashier(cashierId);
        WorkShift shift = openShiftOf(cashierId).orElseThrow(() -> new DomainException("У касира немає відкритої зміни."));

        String zReportId = client.closeShift(cashierId);
        return zReportId == null ? Optional.empty() : Optional.of(finish(shift, zReportId, userId));
    }

    /**
     * Checks the shift against Checkbox: if the register already closed it by itself (automatically at midnight or from
     * another place), writes the Z report here.
     *
     * @return the Z report if the shift is closed; empty if it is still open
     */
    public Optional<ZReportDto> syncShift(int shiftId, int userId) {
        WorkShift shift = repository.getShift(shiftId).orElseThrow(() -> new NotFoundException("Зміну " + shiftId + " не знайдено."));
        if (!shift.isOpen()) {
            throw new DomainException("Зміну вже закрито.");
        }
        if (shift.getCheckboxShiftId().isEmpty()) {
            throw new DomainException("Зміна не має id у Checkbox, звірити її нема з чим.");
        }
        requireUsableCashier(shift.getCashierId());

        CheckboxShiftState state = client.getShift(shift.getCashierId(), shift.getCheckboxShiftId());
        return state.isClosed() && state.zReportId() != null
                ? Optional.of(finish(shift, state.zReportId(), userId))
                : Optional.empty();
    }

    /**
     * Scheduled check of open shifts (a cron job on the old site): first checks whether the register closed the shift by itself,
     * then closes those whose cashier's auto-close time has come. An error in one shift does not stop the others.
     */
    public AutoCloseResult autoClose() {
        LocalDateTime now = now();
        List<WorkShift> shifts = repository.listOpenShifts().stream()
                .filter(s -> !s.getCheckboxShiftId().isEmpty() && !s.getDateStart().isAfter(now.minus(AUTO_CLOSE_GRACE))
                        && client.canUse(s.getCashierId()))
                .toList();
        int byCheckbox = 0;
        int bySchedule = 0;
        List<String> errors = new ArrayList<>();

        for (WorkShift shift : shifts) {
            try {
                if (syncShift(shift.getId(), 0).isPresent()) {
                    byCheckbox++;
                    continue;
                }

                var closeTime = repository.getCashier(shift.getCashierId()).map(Cashier::getAutoCloseTime).orElse(null);
                if (closeTime == null) {
                    continue;
                }
                LocalDateTime closeAt = now.toLocalDate().atTime(closeTime);
                if (!now.isBefore(closeAt) && shift.getDateStart().isBefore(closeAt) && closeShift(shift.getCashierId(), 0).isPresent()) {
                    bySchedule++;
                }
            } catch (DomainException e) {
                errors.add("Зміна " + shift.getId() + ": " + e.getMessage());
            }
        }

        return new AutoCloseResult(shifts.size(), byCheckbox, bySchedule, errors);
    }

    /** X report: the current shift's totals without closing it. */
    public ReportTextDto xReport(int cashierId) {
        requireUsableCashier(cashierId);
        String reportId = client.createXReport(cashierId);
        return new ReportTextDto(reportId, client.getReportText(cashierId, reportId));
    }

    public ReportTextDto zReportText(int reportId) {
        ZReportDto report = repository.listReports(null, null, null).stream()
                .filter(r -> r.id() == reportId).findFirst()
                .orElseThrow(() -> new NotFoundException("Z-звіт " + reportId + " не знайдено."));
        requireUsableCashier(report.cashierId());
        return new ReportTextDto(report.checkboxReportId(), client.getReportText(report.cashierId(), report.checkboxReportId()));
    }

    // ─── Receipts ───

    public PagedResult<ChequeDto> cheques(ChequeFilter filter) {
        PagedResult<ChequeDto> page = repository.listCheques(filter);
        return new PagedResult<>(page.items().stream().map(this::withUrl).toList(), page.page(), page.pageSize(), page.totalCount(),
                page.totalPages());
    }

    public Optional<ChequeDto> chequeForOrder(int orderId) {
        return repository.getChequeForOrder(orderId).map(c -> dto(c.getId()));
    }

    /**
     * Issues a fiscal receipt for an order (the "Receipt" button in an order on the old site).
     * If a receipt already exists, no new one is created: an unfiscalized one is fiscalized again, a fiscalized one returned as is.
     */
    public ChequeDto issueForOrder(int orderId, IssueChequeRequest request) {
        OrderForCheque order = repository.getOrderForCheque(orderId)
                .orElseThrow(() -> new NotFoundException("Замовлення " + orderId + " не знайдено."));

        Optional<Cheque> existing = repository.getChequeForOrder(orderId);
        if (existing.isPresent()) {
            return existing.get().isFiscalized() ? dto(existing.get().getId()) : fiscalize(existing.get().getId());
        }

        Cashier cashier = pickCashier(request.cashierId(), order.paymentCode());
        WorkShift shift = requireOpenShift(cashier);
        // The receipt is built before anything is written: if the order does not fit a receipt, no empty row is left.
        FiscalReceipt receipt = FiscalReceipt.build(orderId, cashier.getName(), order.lines(), order.toPay(),
                order.paymentCode(), order.paymentMethod(), order.email());

        Cheque cheque = repository.save(new Cheque(orderId, cashier, order.toPay(), shift.getId(), now()));

        // The row exists now, so if Checkbox refuses, the receipt stays in the list unfiscalized and can be retried.
        cheque.markFiscalized(client.sell(cashier.getId(), receipt), shift.getId());
        repository.save(cheque);
        return dto(cheque.getId());
    }

    /** Fiscalizes again a receipt Checkbox did not accept ("Create receipt" in the list on the old site). */
    public ChequeDto fiscalize(int chequeId) {
        Cheque cheque = repository.getCheque(chequeId).orElseThrow(() -> new NotFoundException("Чек " + chequeId + " не знайдено."));
        if (cheque.isFiscalized()) {
            throw new DomainException("Чек уже фіскалізовано.");
        }
        OrderForCheque order = repository.getOrderForCheque(cheque.getOrderId())
                .orElseThrow(() -> new DomainException("Замовлення " + cheque.getOrderId() + " чека не знайдено."));
        Cashier cashier = requireUsableCashier(cheque.getCashierId());
        WorkShift shift = requireOpenShift(cashier);

        FiscalReceipt receipt = FiscalReceipt.build(cheque.getOrderId(), cashier.getName(), order.lines(), cheque.getSum(),
                order.paymentCode(), order.paymentMethod(), order.email());
        cheque.markFiscalized(client.sell(cashier.getId(), receipt), shift.getId());
        repository.save(cheque);
        return dto(cheque.getId());
    }

    /** Only a receipt that is not in Checkbox can be deleted: a fiscalized one is already a sale registered with the tax office. */
    public void deleteCheque(int chequeId) {
        Cheque cheque = repository.getCheque(chequeId).orElseThrow(() -> new NotFoundException("Чек " + chequeId + " не знайдено."));
        if (cheque.isFiscalized()) {
            throw new DomainException("Фіскалізований чек видалити не можна: продаж уже зареєстровано в податковій.");
        }
        repository.deleteCheque(chequeId);
    }

    private ZReportDto finish(WorkShift shift, String zReportId, int userId) {
        LocalDateTime now = now();
        var report = new ZReport(shift.getCashierId(), zReportId, userId, now);
        // All open shifts of the cashier are closed: Checkbox allows one per cashier, so the rest are stuck rows.
        List<WorkShift> closed = new ArrayList<>(repository.listOpenShifts().stream()
                .filter(s -> s.getCashierId() == shift.getCashierId()).toList());
        closed.removeIf(s -> s.getId().equals(shift.getId()));
        closed.add(shift);
        closed.forEach(s -> s.close(now));
        repository.saveReport(report, closed);

        List<Integer> cheques = repository.listUnreportedCheques(shift.getCashierId(), shift.getId(), shift.getDateStart()).stream()
                .map(Cheque::getId).toList();
        repository.attachChequesToReport(cheques, report.getId());
        return repository.listReports(shift.getCashierId(), null, null).stream()
                .filter(r -> r.id() == report.getId()).findFirst().orElseThrow();
    }

    /**
     * The cashier for a receipt: the one chosen, or the active cashier for the order's payment code, or the default one (no code).
     * The old site, by mistake, always looked for the cashier with an empty payment code.
     */
    private Cashier pickCashier(Integer cashierId, String paymentCode) {
        if (cashierId != null) {
            Cashier chosen = requireUsableCashier(cashierId);
            if (!chosen.isActive()) {
                throw new DomainException("Касир «" + chosen.getName() + "» вимкнений.");
            }
            return chosen;
        }

        List<Cashier> active = repository.listCashiers().stream().filter(Cashier::isActive).toList();
        Cashier cashier = active.stream().filter(c -> !paymentCode.isEmpty() && c.getPaymentCode().equals(paymentCode)).findFirst()
                .or(() -> active.stream().filter(c -> c.getPaymentCode().isEmpty()).findFirst())
                .orElseThrow(() -> new DomainException("Немає активного касира для оплати «" + paymentCode
                        + "» і касира за замовчуванням. Оберіть касира вручну."));
        if (!client.canUse(cashier.getId())) {
            throw new DomainException("Для касира «" + cashier.getName() + "» не задано ключі Checkbox.");
        }
        return cashier;
    }

    private Optional<WorkShift> openShiftOf(int cashierId) {
        return repository.listOpenShifts().stream().filter(s -> s.getCashierId() == cashierId).max(BY_START);
    }

    private WorkShift requireOpenShift(Cashier cashier) {
        return openShiftOf(cashier.getId()).orElseThrow(() -> new DomainException(
                "У касира «" + cashier.getName() + "» немає відкритої зміни. Спершу відкрийте зміну."));
    }

    private Cashier requireCashier(int id) {
        return repository.getCashier(id).orElseThrow(() -> new NotFoundException("Касира " + id + " не знайдено."));
    }

    private Cashier requireUsableCashier(int id) {
        Cashier cashier = requireCashier(id);
        if (!client.canUse(id)) {
            throw new DomainException("Для касира «" + cashier.getName() + "» не задано ключі Checkbox (checkbox.cashiers у налаштуваннях).");
        }
        return cashier;
    }

    private CashboxWarehouse requireWarehouse(int id) {
        return repository.getWarehouse(id).orElseThrow(() -> new NotFoundException("Склад каси " + id + " не знайдено."));
    }

    private CashboxRequisites requireRequisites(int id) {
        return repository.getRequisites(id).orElseThrow(() -> new NotFoundException("Реквізити " + id + " не знайдено."));
    }

    private void checkReferences(SaveCashierRequest request) {
        if (request.warehouseId() != 0 && repository.getWarehouse(request.warehouseId()).isEmpty()) {
            throw new DomainException("Склад каси " + request.warehouseId() + " не знайдено.");
        }
        if (request.requisitesId() != 0 && repository.getRequisites(request.requisitesId()).isEmpty()) {
            throw new DomainException("Реквізити " + request.requisitesId() + " не знайдено.");
        }
    }

    private ChequeDto dto(int chequeId) {
        return withUrl(repository.getChequeDto(chequeId).orElseThrow());
    }

    private ChequeDto withUrl(ChequeDto dto) {
        return dto.isFiscalized() ? dto.withReceiptUrl(client.receiptUrl(dto.checkboxId())) : dto;
    }

    private static String nameOf(List<? extends CashboxDirectoryEntry> entries, int id) {
        return entries.stream().filter(e -> e.getId() == id).map(CashboxDirectoryEntry::getName).findFirst().orElse(null);
    }

    private static List<DirectoryEntryDto> entries(List<? extends CashboxDirectoryEntry> entries) {
        return entries.stream().map(e -> new DirectoryEntryDto(e.getId(), e.getName(), e.getDescription())).toList();
    }
}
