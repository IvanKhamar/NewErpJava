package ua.newerp.application.common;

import java.time.ZoneId;

/**
 * The shop works on Kyiv time, so "today" is the Kyiv date, whatever zone the server or the JVM runs in. On a UTC server
 * the two dates differ from 22:00 to 24:00 UTC (from 21:00 in summer). The same rule is in .NET (BusinessClock) and Python
 * (SystemClock). Services read the time from the Clock bean; pages and dialogs, which have no bean, use this zone.
 */
public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("Europe/Kyiv");

    private BusinessTime() {
    }
}
