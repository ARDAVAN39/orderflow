package io.github.ardavan39.orderflow.order;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.jboss.logging.Logger;

import io.github.ardavan39.orderflow.events.OrderEvent;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    @Inject
    OrderRepository repository;

    @Inject
    OrderEventPublisher publisher;

    /** Saves the order as PENDING and announces it. The inventory's answer arrives asynchronously. */
    public Order place(String customerId, List<OrderLine> lines) {
        Order order = Order.place(customerId, lines);
        repository.persist(order);
        publisher.tryPublish(placedEvent(order));
        return order;
    }

    public Order get(String id) {
        return find(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    public List<Order> list(OrderStatus status, int page, int size) {
        return repository.findNewestFirst(status).page(Page.of(page, size)).list();
    }

    public long count(OrderStatus status) {
        return repository.findNewestFirst(status).count();
    }

    /**
     * Cancels a pending or confirmed order. A pending order may still get a stock reservation;
     * that is fine, because the cancellation event follows the placement on the same Kafka
     * partition and the inventory releases what it reserved.
     */
    public Order cancel(String id) {
        Order order = get(id);
        boolean cancelled = repository.transition(
                order.id, EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED), OrderStatus.CANCELLED, null);
        if (!cancelled) {
            throw new OrderNotCancellableException(id, get(id).status);
        }
        if (publisher.tryPublish(OrderEvent.cancelled(id))) {
            repository.markCancellationPublished(order.id);
        }
        return get(id);
    }

    void confirm(ObjectId id) {
        if (!repository.transition(id, EnumSet.of(OrderStatus.PENDING), OrderStatus.CONFIRMED, null)) {
            LOG.debugf("Ignoring stock reservation for order %s: it is no longer pending", id);
        }
    }

    void reject(ObjectId id, String reason) {
        if (!repository.transition(id, EnumSet.of(OrderStatus.PENDING), OrderStatus.REJECTED, reason)) {
            LOG.debugf("Ignoring stock rejection for order %s: it is no longer pending", id);
        }
    }

    static OrderEvent placedEvent(Order order) {
        return OrderEvent.placed(
                order.id.toHexString(),
                order.lines.stream().map(line -> new OrderEvent.Line(line.sku, line.quantity)).toList());
    }

    private Optional<Order> find(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return Optional.empty();
        }
        return repository.findByIdOptional(new ObjectId(id));
    }
}
