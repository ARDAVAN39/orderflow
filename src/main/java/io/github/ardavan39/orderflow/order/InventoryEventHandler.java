package io.github.ardavan39.orderflow.order;

import org.bson.types.ObjectId;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.github.ardavan39.orderflow.events.InventoryEvent;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Applies the inventory's answers to orders.
 *
 * <p>{@code @Blocking} moves the work off the Kafka event loop, because the MongoDB client used
 * here is blocking. Messages are still handled one at a time and in order. An exception sends the
 * message to the dead-letter topic instead of stopping the consumer.
 */
@ApplicationScoped
public class InventoryEventHandler {

    @Inject
    OrderService orders;

    @Incoming("inventory-events-in")
    @Blocking
    public void onInventoryEvent(InventoryEvent event) {
        if (event.orderId() == null || !ObjectId.isValid(event.orderId())) {
            throw new IllegalArgumentException("Inventory event without a valid order id: " + event.orderId());
        }
        ObjectId orderId = new ObjectId(event.orderId());
        switch (event.type()) {
            case STOCK_RESERVED -> orders.confirm(orderId);
            case STOCK_REJECTED -> orders.reject(orderId, event.reason());
        }
    }
}
