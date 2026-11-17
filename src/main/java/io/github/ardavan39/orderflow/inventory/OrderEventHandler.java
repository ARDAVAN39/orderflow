package io.github.ardavan39.orderflow.inventory;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.github.ardavan39.orderflow.events.InventoryEvent;
import io.github.ardavan39.orderflow.events.OrderEvent;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The inventory's side of the conversation. It only knows about orders through the events it
 * receives, never through the order collection, so the two modules could be split into separate
 * services without changing this code.
 */
@ApplicationScoped
public class OrderEventHandler {

    @Inject
    InventoryService inventory;

    @Inject
    @Channel("inventory-events-out")
    MutinyEmitter<InventoryEvent> answers;

    @Incoming("order-events-in")
    @Blocking
    public void onOrderEvent(OrderEvent event) {
        switch (event.type()) {
            case ORDER_PLACED -> inventory.reserve(event.orderId(), event.lines())
                    // wait for Kafka: if the answer cannot be sent, the message fails and the
                    // order side asks again later
                    .ifPresent(answer -> answers.sendMessageAndAwait(KafkaRecord.of(answer.orderId(), answer)));
            case ORDER_CANCELLED -> inventory.release(event.orderId());
        }
    }
}
