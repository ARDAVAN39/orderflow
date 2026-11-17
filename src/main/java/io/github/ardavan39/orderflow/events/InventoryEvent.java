package io.github.ardavan39.orderflow.events;

import java.time.Instant;
import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** Published by the inventory module on the {@code inventory-events} topic, keyed by order id. */
@RegisterForReflection
public record InventoryEvent(UUID eventId, Type type, String orderId, String reason, Instant occurredAt) {

    public enum Type {
        STOCK_RESERVED,
        STOCK_REJECTED
    }

    public static InventoryEvent reserved(String orderId) {
        return new InventoryEvent(UUID.randomUUID(), Type.STOCK_RESERVED, orderId, null, Instant.now());
    }

    public static InventoryEvent rejected(String orderId, String reason) {
        return new InventoryEvent(UUID.randomUUID(), Type.STOCK_REJECTED, orderId, reason, Instant.now());
    }
}
