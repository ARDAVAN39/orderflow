package io.github.ardavan39.orderflow.order;

public enum OrderStatus {
    /** Saved and announced; waiting for the inventory to answer. */
    PENDING,
    /** The inventory reserved the stock. */
    CONFIRMED,
    /** The inventory could not reserve the stock. Final. */
    REJECTED,
    /** Cancelled by the customer; any reserved stock is released. Final. */
    CANCELLED
}
