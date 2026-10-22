package ua.newerp.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers the settings classes of infrastructure (like services.Configure&lt;ErpOptions&gt;). */
@Configuration
@EnableConfigurationProperties(ErpProperties.class)
class InfrastructureConfig {
}
