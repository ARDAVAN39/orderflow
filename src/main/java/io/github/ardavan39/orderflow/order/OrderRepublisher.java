package io.github.ardavan39.orderflow.order;

import java.time.Duration;
import java.time.Instant;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.github.ardavan39.orderflow.events.OrderEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Makes event publishing at-least-once. Every so often it sends again:
 *
 * <ul>
 * <li>ORDER_PLACED for orders that have been PENDING for too long. Either the first event never
 * reached Kafka, or the inventory's answer got lost; the inventory answers a duplicate with the
 * outcome it already recorded.</li>
 * <li>ORDER_CANCELLED for cancelled orders whose event was not acknowledged.</li>
 * </ul>
 */
@ApplicationScoped
public class OrderRepublisher {

    private static final Logger LOG = Logger.getLogger(OrderRepublisher.class);

    @Inject
    OrderRepository repository;

    @Inject
    OrderEventPublisher publisher;

    @ConfigProperty(name = "orderflow.republish.after")
    Duration republishAfter;

    @Scheduled(every = "{orderflow.republish.every}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void republish() {
        for (Order order : repository.findPendingCreatedBefore(Instant.now().minus(republishAfter))) {
            LOG.infof("Order %s is still pending, publishing ORDER_PLACED again", order.id);
            publisher.tryPublish(OrderService.placedEvent(order));
        }
        for (Order order : repository.findUnpublishedCancellations()) {
            if (publisher.tryPublish(OrderEvent.cancelled(order.id.toHexString()))) {
                repository.markCancellationPublished(order.id);
            }
        }
    }
}
