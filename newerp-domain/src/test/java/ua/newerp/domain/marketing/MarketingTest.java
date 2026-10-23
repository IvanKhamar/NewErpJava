package ua.newerp.domain.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ua.newerp.domain.common.DomainException;

class MarketingTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 12, 28);

    @ParameterizedTest
    @CsvSource({
        "28, 12, TODAY, true",
        "29, 12, TOMORROW, true",
        "28, 12, WEEK, true",   // today's birthdays are in the week too
        "3, 1, WEEK, true",     // the week crosses New Year
        "5, 1, WEEK, false",
        "1, 12, MONTH, true",
        "31, 4, ALL, false"})   // an impossible date
    void inPeriod(int day, int month, BirthdayPeriod period, boolean expected) {
        assertThat(Birthdays.inPeriod(day, month, period, TODAY)).isEqualTo(expected);
    }

    @Test
    void nextMovesFebruary29ToTheLastDayInACommonYear() {
        assertThat(Birthdays.next(29, 2, TODAY)).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(Birthdays.next(3, 1, TODAY)).isEqualTo(LocalDate.of(2027, 1, 3));
    }

    @Test
    void couponCodeHasPrefixAndHexAndIsValidated() {
        assertThat(BirthdayCoupon.newCode(new Random(1))).matches("^DN[0-9A-F]{8}$");
        assertThat(BirthdayCoupon.requireCode(" dnabc123 ")).isEqualTo("DNABC123");
        assertThatThrownBy(() -> BirthdayCoupon.requireCode("DN-1")).isInstanceOf(DomainException.class);
    }

    @Test
    void buildDeduplicatesCleanValuesAndTakesNameFromLatestOrder() {
        List<OrderContact> orders = List.of(
                new OrderContact("+38 (067) 111-22-33", "old@test.ua", "Оля", "Стара", "Київ", LocalDateTime.of(2025, 1, 1, 0, 0)),
                new OrderContact("0671112233", "OLGA@test.ua", " Ольга ", "Нова", "Львів", LocalDateTime.of(2026, 1, 1, 0, 0)));

        assertThat(ContactExport.build(ContactField.PHONE, List.of("0671112233", "+380671112233", "12345", "380501234567"), orders))
                .containsExactly(new ContactRow("", "", "", "380501234567", ""), new ContactRow("Ольга", "Нова", "Львів", "380671112233", "olga@test.ua"));
    }

    @Test
    void buildByEmailSkipsInvalid() {
        assertThat(ContactExport.build(ContactField.EMAIL, List.of("a@b.ua", "A@B.ua", "not-an-email", "x@localhost"), List.of()))
                .extracting(ContactRow::email).containsExactly("a@b.ua");
    }

    @Test
    void csvHasHeaderAndEscapesFormulasAndSeparators() {
        assertThat(ContactExport.toCsv(ContactField.EMAIL, List.of(new ContactRow("=HYPERLINK(1)", "Ко;ва", "", "380671112233", "a@b.ua"))))
                .isEqualTo("Ім'я;Прізвище;Місто;E-mail;Телефон\r\n'=HYPERLINK(1);\"Ко;ва\";;a@b.ua;380671112233\r\n");
    }
}
