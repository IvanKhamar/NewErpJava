package ua.newerp.application.production;

/** Stock operations of a model (buttons "+Cut", "To sewing", "+Sewn", "Shipped" in the old module). */
public enum ProductionOperation {

    /** +Cut: cut stock grows, consumables are written off by the cutting BOM, component stock grows. */
    CUT,

    /** To sewing: cut sets went to the seamstresses, cut stock shrinks. */
    SEND_TO_SEWING,

    /** +Sewn: sewn stock grows, components and consumables are written off by the sewing BOM. */
    SEWN,

    /** Shipped not through an order: sewn stock shrinks. */
    SHIP
}
