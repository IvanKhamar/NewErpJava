package ua.newerp.infrastructure.cashbox;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import ua.newerp.application.cashbox.CheckboxClient;
import ua.newerp.application.cashbox.CheckboxShiftState;
import ua.newerp.domain.cashbox.FiscalReceipt;

/**
 * The stub used without keys. With checkbox.fake-receipts it "opens" shifts and "fiscalizes" receipts in memory, so the whole
 * path can be walked locally; without it it can do nothing (canUse = false), and NewErp explains that no keys are set.
 */
class StubCheckboxClient implements CheckboxClient {

    private final boolean fakeReceipts;
    private final Map<Integer, String> openShifts = new ConcurrentHashMap<>();

    StubCheckboxClient(boolean fakeReceipts) {
        this.fakeReceipts = fakeReceipts;
    }

    @Override
    public boolean isFake() {
        return true;
    }

    @Override
    public boolean canUse(int cashierId) {
        return fakeReceipts;
    }

    @Override
    public String openShift(int cashierId) {
        return openShifts.computeIfAbsent(cashierId, id -> "test-shift-" + newId());
    }

    @Override
    public CheckboxShiftState getShift(int cashierId, String shiftId) {
        return new CheckboxShiftState(shiftId, false, null);
    }

    @Override
    public String closeShift(int cashierId) {
        openShifts.remove(cashierId);
        return "test-z-" + newId();
    }

    @Override
    public String createXReport(int cashierId) {
        return "test-x-" + newId();
    }

    @Override
    public String getReportText(int cashierId, String reportId) {
        return "ТЕСТОВИЙ ЗВІТ (заглушка Checkbox, не фіскальний)\nЗвіт: " + reportId;
    }

    @Override
    public String sell(int cashierId, FiscalReceipt receipt) {
        return "test-" + newId();
    }

    @Override
    public String receiptUrl(String receiptId) {
        return null;
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
