package ua.newerp.application.marketing;

import java.time.LocalDate;
import java.util.Set;

import ua.newerp.domain.marketing.ContactField;
import ua.newerp.domain.marketing.ContactSource;

/** What to export: the field, the sources and the period (by the date added, inclusive). */
public record ContactExportRequest(ContactField field, Set<ContactSource> sources, LocalDate from, LocalDate to) {
}
