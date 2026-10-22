package ua.newerp.application.staff;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings of the "bonus" section of application.yml (BonusOptions in .NET). */
@ConfigurationProperties("bonus")
public record BonusProperties(BigDecimal percent, List<Integer> countedStatusIds) {

    public BonusProperties {
        percent = percent == null ? BigDecimal.ZERO : percent;
        countedStatusIds = countedStatusIds == null ? List.of() : List.copyOf(countedStatusIds);
    }
}
