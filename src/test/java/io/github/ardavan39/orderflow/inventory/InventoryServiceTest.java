package io.github.ardavan39.orderflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import io.github.ardavan39.orderflow.events.InventoryEvent;
import io.github.ardavan39.orderflow.events.OrderEvent.Line;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Runs against a real MongoDB started by Quarkus Dev Services. Every test uses its own SKUs and
 * order ids, so tests do not depend on each other or on the order they run in.
 */
@QuarkusTest
class InventoryServiceTest {

    @Inject
    InventoryService inventory;

    @Inject
    StockRepository stock;

    @Inject
    ReservationRepository reservations;

    @Test
    void reservesEveryLineWhenThereIsEnoughStock() {
        String book = givenStock(5);
        String pen = givenStock(10);

        Optional<InventoryEvent> answer = inventory.reserve(orderId(), List.of(new Line(book, 2), new Line(pen, 10)));

        assertThat(answer).get().extracting(InventoryEvent::type).isEqualTo(InventoryEvent.Type.STOCK_RESERVED);
        assertThat(available(book)).isEqualTo(3);
        assertThat(available(pen)).isZero();
    }

    @Test
    void rejectsTheWholeOrderAndPutsBackWhatWasTakenWhenOneLineIsShort() {
        String book = givenStock(5);
        String pen = givenStock(1);
        String orderId = orderId();

        Optional<InventoryEvent> answer = inventory.reserve(orderId, List.of(new Line(book, 2), new Line(pen, 3)));

        assertThat(answer).get().satisfies(event -> {
            assertThat(event.type()).isEqualTo(InventoryEvent.Type.STOCK_REJECTED);
            assertThat(event.reason()).contains(pen);
        });
        assertThat(available(book)).isEqualTo(5);
        assertThat(available(pen)).isEqualTo(1);
        assertThat(reservations.find(orderId)).get()
                .extracting(Reservation::status).isEqualTo(Reservation.Status.REJECTED);
    }

    @Test
    void rejectsAnUnknownSku() {
        Optional<InventoryEvent> answer = inventory.reserve(orderId(), List.of(new Line("NO-SUCH-" + UUID.randomUUID(), 1)));

        assertThat(answer).get().extracting(InventoryEvent::type).isEqualTo(InventoryEvent.Type.STOCK_REJECTED);
    }

    @Test
    void aDuplicatePlacementGetsTheSameAnswerWithoutTakingStockTwice() {
        String book = givenStock(5);
        String orderId = orderId();

        inventory.reserve(orderId, List.of(new Line(book, 2)));
        Optional<InventoryEvent> again = inventory.reserve(orderId, List.of(new Line(book, 2)));

        assertThat(again).get().extracting(InventoryEvent::type).isEqualTo(InventoryEvent.Type.STOCK_RESERVED);
        assertThat(available(book)).isEqualTo(3);
    }

    @Test
    void linesForTheSameSkuAreCheckedTogether() {
        String book = givenStock(3);

        Optional<InventoryEvent> answer = inventory.reserve(orderId(), List.of(new Line(book, 2), new Line(book, 2)));

        assertThat(answer).get().extracting(InventoryEvent::type).isEqualTo(InventoryEvent.Type.STOCK_REJECTED);
        assertThat(available(book)).isEqualTo(3);
    }

    @Test
    void releaseGivesTheStockBackExactlyOnce() {
        String book = givenStock(5);
        String orderId = orderId();
        inventory.reserve(orderId, List.of(new Line(book, 4)));

        inventory.release(orderId);
        inventory.release(orderId);

        assertThat(available(book)).isEqualTo(5);
        assertThat(reservations.find(orderId)).get()
                .extracting(Reservation::status).isEqualTo(Reservation.Status.RELEASED);
    }

    @Test
    void releasingARejectedOrderChangesNothing() {
        String book = givenStock(1);
        String orderId = orderId();
        inventory.reserve(orderId, List.of(new Line(book, 2)));

        inventory.release(orderId);

        assertThat(available(book)).isEqualTo(1);
        assertThat(reservations.find(orderId)).get()
                .extracting(Reservation::status).isEqualTo(Reservation.Status.REJECTED);
    }

    @Test
    void aCancellationThatArrivesFirstStopsALaterReservation() {
        String book = givenStock(5);
        String orderId = orderId();

        inventory.release(orderId);
        Optional<InventoryEvent> answer = inventory.reserve(orderId, List.of(new Line(book, 2)));

        assertThat(answer).isEmpty();
        assertThat(available(book)).isEqualTo(5);
    }

    @Test
    void combineSameSkuAddsUpQuantitiesAndKeepsTheOrder() {
        List<Line> combined = InventoryService.combineSameSku(List.of(
                new Line("A", 1), new Line("B", 2), new Line("A", 3)));

        assertThat(combined).containsExactly(new Line("A", 4), new Line("B", 2));
    }

    private String givenStock(int available) {
        String sku = "SKU-" + UUID.randomUUID();
        stock.setAvailable(sku, available);
        return sku;
    }

    private int available(String sku) {
        return stock.find(sku).orElseThrow().available();
    }

    private static String orderId() {
        return new ObjectId().toHexString();
    }
}
