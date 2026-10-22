package ua.newerp.application.production;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the "production" section of application.yml (ProductionOptions in .NET).
 *
 * @param orderSubtractStatusIds statuses in which an order takes sewn pieces from stock. Entering the set writes off,
 *                               leaving it returns, moving inside it changes nothing (as in the old StockTrack module).
 */
@ConfigurationProperties("production")
public record ProductionProperties(List<Integer> orderSubtractStatusIds) {

    public ProductionProperties {
        orderSubtractStatusIds = orderSubtractStatusIds == null ? List.of() : List.copyOf(orderSubtractStatusIds);
    }
}
