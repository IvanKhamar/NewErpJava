package ua.newerp.application.cashbox;

/** @param cashierId which cashier to use; null: by the order's payment code, as on the old site */
public record IssueChequeRequest(Integer cashierId) {
}
