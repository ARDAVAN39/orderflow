package io.github.ardavan39.orderflow.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Published by the order module on the {@code order-events} topic, keyed by order id.
 *
 * <p>Both event types for one order share a key, so Kafka keeps them on one partition and the
 * inventory sees them in the order they were published: a cancellation never overtakes the
 * placement it cancels.
 */
@RegisterForReflection
public record OrderEvent(UUID eventId, Type type, String orderId, List<Line> lines, Instant occurredAt) {

    public enum Type {
        ORDER_PLACED,
        ORDER_CANCELLED
    }

    @RegisterForReflection
    public record Line(String sku, int quantity) {
    }

    public static OrderEvent placed(String orderId, List<Line> lines) {
        return new OrderEvent(UUID.randomUUID(), Type.ORDER_PLACED, orderId, List.copyOf(lines), Instant.now());
    }

    public static OrderEvent cancelled(String orderId) {
        return new OrderEvent(UUID.randomUUID(), Type.ORDER_CANCELLED, orderId, List.of(), Instant.now());
    }
}
