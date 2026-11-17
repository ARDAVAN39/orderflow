package io.github.ardavan39.orderflow.inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.github.ardavan39.orderflow.events.InventoryEvent;
import io.github.ardavan39.orderflow.events.OrderEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class InventoryService {

    private static final Logger LOG = Logger.getLogger(InventoryService.class);

    @Inject
    StockRepository stock;

    @Inject
    ReservationRepository reservations;

    /**
     * Tries to take stock for every line of an order: all lines or none.
     *
     * @return the answer to publish, or empty if the order was already cancelled and there is
     *         nothing to say
     */
    public Optional<InventoryEvent> reserve(String orderId, List<OrderEvent.Line> lines) {
        Optional<Reservation> existing = reservations.find(orderId);
        if (existing.isPresent()) {
            LOG.debugf("Order %s already handled (%s), repeating the answer", orderId, existing.get().status());
            return answerFor(existing.get());
        }

        List<OrderEvent.Line> wanted = combineSameSku(lines);
        List<OrderEvent.Line> taken = new ArrayList<>();
        for (OrderEvent.Line line : wanted) {
            if (!stock.take(line.sku(), line.quantity())) {
                taken.forEach(t -> stock.putBack(t.sku(), t.quantity()));
                String reason = "Not enough stock for SKU " + line.sku();
                reservations.insert(new Reservation(orderId, Reservation.Status.REJECTED, List.of(), reason));
                LOG.infof("Order %s rejected: %s", orderId, reason);
                return Optional.of(InventoryEvent.rejected(orderId, reason));
            }
            taken.add(line);
        }

        reservations.insert(new Reservation(orderId, Reservation.Status.RESERVED, taken, null));
        LOG.infof("Stock reserved for order %s", orderId);
        return Optional.of(InventoryEvent.reserved(orderId));
    }

    /**
     * Gives back the stock of a cancelled order. If the order was never reserved (the placement
     * event is late or lost), a RELEASED record is stored so that a later placement does not take
     * stock for an order that no longer exists.
     */
    public void release(String orderId) {
        Optional<Reservation> existing = reservations.find(orderId);
        if (existing.isEmpty()) {
            reservations.insert(new Reservation(orderId, Reservation.Status.RELEASED, List.of(), "Cancelled before reservation"));
            return;
        }
        if (reservations.release(orderId)) {
            existing.get().lines().forEach(line -> stock.putBack(line.sku(), line.quantity()));
            LOG.infof("Stock released for cancelled order %s", orderId);
        }
    }

    private static Optional<InventoryEvent> answerFor(Reservation reservation) {
        return switch (reservation.status()) {
            case RESERVED -> Optional.of(InventoryEvent.reserved(reservation.orderId()));
            case REJECTED -> Optional.of(InventoryEvent.rejected(reservation.orderId(), reservation.reason()));
            case RELEASED -> Optional.empty();
        };
    }

    /** Two lines for the same SKU must be checked against the stock together, not one by one. */
    static List<OrderEvent.Line> combineSameSku(List<OrderEvent.Line> lines) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (OrderEvent.Line line : lines) {
            quantities.merge(line.sku(), line.quantity(), Integer::sum);
        }
        return quantities.entrySet().stream()
                .map(entry -> new OrderEvent.Line(entry.getKey(), entry.getValue()))
                .toList();
    }
}
