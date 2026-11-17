package io.github.ardavan39.orderflow.order;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.mongodb.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderRepository implements PanacheMongoRepository<Order> {

    /**
     * Moves an order to a new status only if it is currently in one of the expected ones. This is
     * a single atomic MongoDB update, so two handlers racing on the same order cannot both win.
     *
     * @return true if the order was updated
     */
    public boolean transition(ObjectId id, Set<OrderStatus> expected, OrderStatus next, String reason) {
        List<String> expectedNames = expected.stream().map(Enum::name).toList();
        long modified = mongoCollection().updateOne(
                and(eq("_id", id), in("status", expectedNames)),
                combine(
                        set("status", next.name()),
                        set("rejectionReason", reason),
                        set("updatedAt", Date.from(Instant.now()))))
                .getModifiedCount();
        return modified == 1;
    }

    public void markCancellationPublished(ObjectId id) {
        mongoCollection().updateOne(eq("_id", id), set("cancellationPublished", true));
    }

    public List<Order> findPendingCreatedBefore(Instant cutoff) {
        return mongoCollection()
                .find(and(eq("status", OrderStatus.PENDING.name()), lt("createdAt", Date.from(cutoff))))
                .into(new ArrayList<>());
    }

    public List<Order> findUnpublishedCancellations() {
        return mongoCollection()
                .find(and(eq("status", OrderStatus.CANCELLED.name()), eq("cancellationPublished", false)))
                .into(new ArrayList<>());
    }

    public PanacheQuery<Order> findNewestFirst(OrderStatus status) {
        Sort newestFirst = Sort.descending("createdAt");
        return status == null ? findAll(newestFirst) : find("status", newestFirst, status.name());
    }
}
