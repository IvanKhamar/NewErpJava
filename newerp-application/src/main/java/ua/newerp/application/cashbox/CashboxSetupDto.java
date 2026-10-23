package ua.newerp.application.cashbox;

import java.util.List;

/** Everything for the cash register setup in one request. fakeMode: no keys, the Checkbox test stub works. */
public record CashboxSetupDto(List<CashierDto> cashiers, List<DirectoryEntryDto> warehouses, List<DirectoryEntryDto> requisites,
        boolean fakeMode) {
}
