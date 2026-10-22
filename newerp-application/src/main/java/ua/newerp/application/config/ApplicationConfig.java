package ua.newerp.application.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import ua.newerp.application.production.ProductionProperties;
import ua.newerp.application.staff.BonusProperties;

/** Registers the settings records of the use cases (like services.Configure&lt;BonusOptions&gt;). */
@Configuration
@EnableConfigurationProperties({BonusProperties.class, ProductionProperties.class})
class ApplicationConfig {
}
