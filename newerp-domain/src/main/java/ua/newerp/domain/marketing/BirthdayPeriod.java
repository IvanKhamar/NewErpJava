package ua.newerp.domain.marketing;

/** For which period to show birthdays (like the filters of the old site's "Дні народження" page). */
public enum BirthdayPeriod {
    TODAY,
    TOMORROW,
    /** From today to the seventh day inclusive. */
    WEEK,
    /** The current calendar month. */
    MONTH,
    ALL
}
