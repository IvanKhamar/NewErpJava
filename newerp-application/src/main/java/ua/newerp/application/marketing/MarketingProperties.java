package ua.newerp.application.marketing;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the "marketing" section of application.yml (MarketingOptions in .NET). Defaults as in the old site's code.
 *
 * @param birthdayCouponDays       how many days a coupon is valid from the day it is made
 * @param pickupCity               the city of a customer who picked the order up (the order has no city, the shipping method has the marker)
 * @param bulkSmsDelayMilliseconds a pause between the SMS of a bulk send, to stay within the SMS service's limits
 */
@ConfigurationProperties("marketing")
public record MarketingProperties(BigDecimal birthdayDiscountPercent, Integer birthdayCouponDays, String pickupCity, String pickupMethodMarker,
        Integer bulkSmsDelayMilliseconds) {

    public MarketingProperties {
        birthdayDiscountPercent = birthdayDiscountPercent == null ? BigDecimal.valueOf(20) : birthdayDiscountPercent;
        birthdayCouponDays = birthdayCouponDays == null ? 30 : birthdayCouponDays;
        pickupCity = pickupCity == null ? "Львів" : pickupCity;
        pickupMethodMarker = pickupMethodMarker == null ? "амовив" : pickupMethodMarker;
        bulkSmsDelayMilliseconds = bulkSmsDelayMilliseconds == null ? 300 : bulkSmsDelayMilliseconds;
    }
}
