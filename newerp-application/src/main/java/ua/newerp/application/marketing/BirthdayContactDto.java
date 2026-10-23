package ua.newerp.application.marketing;

import java.time.LocalDate;

/**
 * A birthday person: data from their newest order where the birthday is given.
 *
 * @param sentThisYear how many greetings this number already got this year
 */
public record BirthdayContactDto(String phone, String firstName, String lastName, String email, String city, String instagram, int day, int month,
        LocalDate nextBirthday, int sentThisYear) {
}
