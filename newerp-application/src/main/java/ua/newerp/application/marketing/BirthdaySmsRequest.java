package ua.newerp.application.marketing;

/** @param couponCode when given, a coupon with this code is made first (it goes into the text) */
public record BirthdaySmsRequest(String phone, String text, String couponCode) {
}
