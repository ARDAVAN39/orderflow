package io.github.ardavan39.orderflow.order;

import java.time.Duration;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.logging.Logger;

import io.github.ardavan39.orderflow.events.OrderEvent;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Sends order events to Kafka and waits for the broker to acknowledge them.
 *
 * <p>Saving an order and publishing its event are two separate writes, so publishing can fail
 * after the save succeeded. Instead of failing the request, the failure is logged and the
 * {@link OrderRepublisher} sends the event again later. Consumers are idempotent, so a duplicate
 * is harmless.
 */
@ApplicationScoped
public class OrderEventPublisher {

    private static final Logger LOG = Logger.getLogger(OrderEventPublisher.class);
    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(5);

    @Inject
    @Channel("order-events-out")
    MutinyEmitter<OrderEvent> emitter;

    /** @return true if Kafka acknowledged the event */
    public boolean tryPublish(OrderEvent event) {
        try {
            emitter.sendMessage(KafkaRecord.of(event.orderId(), event)).await().atMost(ACK_TIMEOUT);
            return true;
        } catch (RuntimeException e) {
            LOG.warnf("Could not publish %s for order %s, will retry later: %s",
                    event.type(), event.orderId(), e.getMessage());
            return false;
        }
    }
}
