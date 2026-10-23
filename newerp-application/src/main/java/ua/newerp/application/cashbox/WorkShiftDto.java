package ua.newerp.application.cashbox;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WorkShiftDto(int id, int cashierId, String cashier, String checkboxShiftId, String openedBy, LocalDateTime dateStart,
        LocalDateTime dateEnd, int cheques, BigDecimal chequesSum) {
}
