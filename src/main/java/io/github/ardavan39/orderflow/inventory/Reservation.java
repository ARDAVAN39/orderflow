package io.github.ardavan39.orderflow.inventory;

import java.util.List;

import io.github.ardavan39.orderflow.events.OrderEvent;

/**
 * What the inventory decided for one order. There is exactly one reservation per order id, and it
 * is what makes event handling idempotent: a duplicate ORDER_PLACED finds the existing reservation
 * and gets the same answer again instead of taking stock a second time.
 */
public record Reservation(String orderId, Status status, List<OrderEvent.Line> lines, String reason) {

    public enum Status {
        /** Stock was taken for the order. */
        RESERVED,
        /** Not enough stock; nothing was taken. */
        REJECTED,
        /** The order was cancelled and its stock, if any, has been given back. */
        RELEASED
    }
}
