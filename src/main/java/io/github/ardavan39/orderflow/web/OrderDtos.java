package io.github.ardavan39.orderflow.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.github.ardavan39.orderflow.order.Order;
import io.github.ardavan39.orderflow.order.OrderLine;
import io.github.ardavan39.orderflow.order.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the order API. */
public final class OrderDtos {

    private OrderDtos() {
    }

    /**
     * Prices come from the caller. In a larger system a cart or catalog service would have priced
     * the lines already; the order service records what was agreed.
     */
    public record PlaceOrderRequest(
            @NotBlank @Size(max = 64) String customerId,
            @NotEmpty @Size(max = 50) List<@Valid @NotNull LineRequest> lines) {

        List<OrderLine> toOrderLines() {
            return lines.stream().map(line -> new OrderLine(line.sku(), line.quantity(), line.unitPrice())).toList();
        }
    }

    public record LineRequest(
            @NotBlank @Size(max = 64) String sku,
            @Min(1) @Max(1000) int quantity,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice) {
    }

    public record OrderResponse(
            String id,
            String customerId,
            List<LineResponse> lines,
            BigDecimal total,
            OrderStatus status,
            String rejectionReason,
            Instant createdAt,
            Instant updatedAt) {

        static OrderResponse from(Order order) {
            return new OrderResponse(
                    order.id.toHexString(),
                    order.customerId,
                    order.lines.stream().map(LineResponse::from).toList(),
                    order.total,
                    order.status,
                    order.rejectionReason,
                    order.createdAt,
                    order.updatedAt);
        }
    }

    public record LineResponse(String sku, int quantity, BigDecimal unitPrice) {

        static LineResponse from(OrderLine line) {
            return new LineResponse(line.sku, line.quantity, line.unitPrice);
        }
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems) {
    }
}
