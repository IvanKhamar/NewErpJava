package ua.newerp.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** "Today" is the Kyiv date, whatever zone the machine runs in. */
class ClockConfigTest {

    @ParameterizedTest
    @CsvSource({
        "2026-11-06T21:59:00Z, 2026-11-06, 2", // winter: UTC+2, still the same date
        "2026-11-06T22:00:00Z, 2026-11-07, 2", // already the next day in Kyiv, not yet in UTC
        "2026-11-06T23:30:00Z, 2026-11-07, 2",
        "2026-11-07T00:00:00Z, 2026-11-07, 2", // from here the dates are the same again
        "2026-07-01T20:59:00Z, 2026-07-01, 3", // summer: UTC+3
        "2026-07-01T21:00:00Z, 2026-07-02, 3",
    })
    void localDateIsTheKyivDate(String utc, String kyivDate, int offsetHours) {
        Instant instant = Instant.parse(utc);
        Clock fixed = Clock.fixed(instant, new ClockConfig().clock().getZone());

        assertThat(LocalDate.now(fixed)).isEqualTo(LocalDate.parse(kyivDate));
        assertThat(fixed.getZone().getRules().getOffset(instant)).isEqualTo(ZoneOffset.ofHours(offsetHours));
    }
}
