package io.github.ardavan39.orderflow.inventory;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;

import io.github.ardavan39.orderflow.events.OrderEvent;
import jakarta.enterprise.context.ApplicationScoped;

/** One document per order: {@code {_id: orderId, status, lines: [{sku, quantity}], reason}}. */
@ApplicationScoped
public class ReservationRepository {

    private final MongoCollection<Document> reservations;

    ReservationRepository(MongoClient client, @ConfigProperty(name = "quarkus.mongodb.database") String database) {
        this.reservations = client.getDatabase(database).getCollection("reservations");
    }

    public Optional<Reservation> find(String orderId) {
        return Optional.ofNullable(reservations.find(eq("_id", orderId)).first())
                .map(ReservationRepository::toReservation);
    }

    /** Fails with a duplicate-key error if the order already has a reservation. */
    public void insert(Reservation reservation) {
        List<Document> lines = reservation.lines().stream()
                .map(line -> new Document("sku", line.sku()).append("quantity", line.quantity()))
                .toList();
        reservations.insertOne(new Document("_id", reservation.orderId())
                .append("status", reservation.status().name())
                .append("lines", lines)
                .append("reason", reservation.reason())
                .append("updatedAt", Date.from(Instant.now())));
    }

    /**
     * Moves a reservation from RESERVED to RELEASED in one atomic update.
     *
     * @return true if this call made the change, so the caller is the one that must give the stock back
     */
    public boolean release(String orderId) {
        return reservations.updateOne(
                and(eq("_id", orderId), eq("status", Reservation.Status.RESERVED.name())),
                combine(set("status", Reservation.Status.RELEASED.name()), set("updatedAt", Date.from(Instant.now()))))
                .getModifiedCount() == 1;
    }

    private static Reservation toReservation(Document document) {
        List<OrderEvent.Line> lines = document.getList("lines", Document.class, List.of()).stream()
                .map(line -> new OrderEvent.Line(line.getString("sku"), line.getInteger("quantity")))
                .toList();
        return new Reservation(
                document.getString("_id"),
                Reservation.Status.valueOf(document.getString("status")),
                lines,
                document.getString("reason"));
    }
}
