package ua.newerp.domain.payments;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.regex.Pattern;

import ua.newerp.domain.common.Text;

/**
 * A payment to a PrivatBank account or card (dd_privatbank of the old site).
 * The old site stores amounts as text with the currency ("1500.00 UAH"), so the number is parsed from the text.
 */
public class BankPayment {

    private static final Pattern NOT_NUMBER = Pattern.compile("[^\\d,.\\-]");

    private Integer id;
    private int orderId;
    private String appCode = "";
    private String card = "";
    private String amountText = "";
    private String cardAmountText = "";
    private String restText = "";
    private String description = "";
    private String terminal = "";
    private LocalDate tranDate;
    private LocalTime tranTime;
    private boolean visible = true;

    protected BankPayment() {
    }

    public BankPayment(String appCode, String card, BigDecimal amount, BigDecimal balance, String description, String terminal,
            LocalDate date, LocalTime time) {
        this.appCode = appCode;
        this.card = card;
        this.amountText = money(amount);
        this.cardAmountText = amountText;
        this.restText = money(balance == null ? BigDecimal.ZERO : balance);
        this.description = description;
        this.terminal = Text.truncate(terminal, 255);
        this.tranDate = date;
        this.tranTime = time;
    }

    public void linkTo(int orderId) {
        this.orderId = orderId;
    }

    public void unlink() {
        this.orderId = 0;
    }

    public void hide() {
        visible = false;
    }

    public void show() {
        visible = true;
    }

    public static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + " UAH";
    }

    /** "1 500,00 UAH", "1500.00 UAH", "1500" → 1500. Text that cannot be parsed is 0. */
    public static BigDecimal parseMoney(String text) {
        String number = NOT_NUMBER.matcher(text == null ? "" : text).replaceAll("").replace(',', '.');
        try {
            return new BigDecimal(number);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    public Integer getId() {
        return id;
    }

    /** The order paid by this payment; 0: not matched yet. */
    public int getOrderId() {
        return orderId;
    }

    /** Transaction id in the bank: by it the import never adds the same payment twice. */
    public String getAppCode() {
        return appCode;
    }

    /** The account or card the money came to. */
    public String getCard() {
        return card;
    }

    public String getAmountText() {
        return amountText;
    }

    /** Account balance after the operation. */
    public String getRestText() {
        return restText;
    }

    /** Payment purpose and the payer's details. */
    public String getDescription() {
        return description;
    }

    /** The payer (counterparty name). */
    public String getTerminal() {
        return terminal;
    }

    public LocalDate getTranDate() {
        return tranDate;
    }

    public LocalTime getTranTime() {
        return tranTime;
    }

    /** false: the payment is hidden (not about orders: a refund, a transfer between own accounts). */
    public boolean isVisible() {
        return visible;
    }

    public BigDecimal getAmount() {
        return parseMoney(amountText);
    }

    public LocalDateTime getTimestamp() {
        return tranDate.atTime(tranTime);
    }
}
