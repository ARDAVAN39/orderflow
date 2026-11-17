package io.github.ardavan39.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    void aNewOrderIsPendingAndTotalsItsLines() {
        Order order = Order.place("customer-1", List.of(
                new OrderLine("BOOK-1", 2, new BigDecimal("12.50")),
                new OrderLine("PEN-7", 3, new BigDecimal("1.99"))));

        assertThat(order.status).isEqualTo(OrderStatus.PENDING);
        assertThat(order.total).isEqualByComparingTo("30.97");
        assertThat(order.total.scale()).isEqualTo(2);
        assertThat(order.cancellationPublished).isFalse();
        assertThat(order.createdAt).isNotNull().isEqualTo(order.updatedAt);
    }

    @Test
    void anOrderNeedsAtLeastOneLine() {
        assertThatThrownBy(() -> Order.place("customer-1", List.of()))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void theSameSkuCannotAppearTwice() {
        List<OrderLine> lines = List.of(
                new OrderLine("BOOK-1", 1, BigDecimal.TEN),
                new OrderLine("BOOK-1", 2, BigDecimal.TEN));

        assertThatThrownBy(() -> Order.place("customer-1", lines))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("BOOK-1");
    }

    @Test
    void freeItemsAreAllowed() {
        Order order = Order.place("customer-1", List.of(new OrderLine("SAMPLE", 1, BigDecimal.ZERO)));

        assertThat(order.total).isEqualByComparingTo("0.00");
    }
}
