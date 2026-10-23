package ua.newerp.domain.marketing;

/** Where the contacts come from, as the old site's export tables. Several are combined in an EnumSet (a [Flags] enum in .NET). */
public enum ContactSource {
    ORDERS,
    /** Questions about a product (phones only). */
    QUESTIONS,
    /** The newsletter subscription (e-mails only). */
    SUBSCRIBERS
}
