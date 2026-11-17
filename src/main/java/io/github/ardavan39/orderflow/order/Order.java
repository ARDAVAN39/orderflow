package io.github.ardavan39.orderflow.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;

import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * An order as stored in MongoDB. Status changes are not made on this object but through
 * {@link OrderRepository#transition}, which updates the document only if it is still in the
 * expected status. That keeps concurrent Kafka handlers and HTTP requests from overwriting each
 * other without needing locks.
 */
@MongoEntity(collection = "orders")
public class Order {

    public ObjectId id;
    public String customerId;
    public List<OrderLine> lines;
    public BigDecimal total;
    public OrderStatus status;
    public String rejectionReason;
    /** False until the ORDER_CANCELLED event has reached Kafka; the republisher retries it. */
    public boolean cancellationPublished;
    public Instant createdAt;
    public Instant updatedAt;

    public static Order place(String customerId, List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new InvalidOrderException("An order needs at least one line");
        }
        Set<String> skus = new HashSet<>();
        for (OrderLine line : lines) {
            if (!skus.add(line.sku)) {
                throw new InvalidOrderException("SKU " + line.sku + " appears more than once; combine the quantities");
            }
        }

        Instant now = Instant.now();
        Order order = new Order();
        order.customerId = customerId;
        order.lines = List.copyOf(lines);
        order.total = lines.stream()
                .map(OrderLine::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        order.status = OrderStatus.PENDING;
        order.createdAt = now;
        order.updatedAt = now;
        return order;
    }
}
