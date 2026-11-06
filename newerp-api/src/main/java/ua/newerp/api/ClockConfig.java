package ua.newerp.api;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ua.newerp.application.common.BusinessTime;

/** The clock of the app, always on Kyiv time (TimeProvider in .NET), so tests can replace it with a fixed one. */
@Configuration
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.system(BusinessTime.ZONE);
    }
}
