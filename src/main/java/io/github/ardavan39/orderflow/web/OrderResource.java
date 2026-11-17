package io.github.ardavan39.orderflow.web;

import java.net.URI;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.github.ardavan39.orderflow.order.Order;
import io.github.ardavan39.orderflow.order.OrderService;
import io.github.ardavan39.orderflow.order.OrderStatus;
import io.github.ardavan39.orderflow.web.OrderDtos.OrderResponse;
import io.github.ardavan39.orderflow.web.OrderDtos.PageResponse;
import io.github.ardavan39.orderflow.web.OrderDtos.PlaceOrderRequest;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/orders")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Orders")
public class OrderResource {

    @Inject
    OrderService orders;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Place an order. It starts PENDING; poll it until the inventory has answered.")
    public Response place(@Valid @NotNull PlaceOrderRequest request) {
        Order order = orders.place(request.customerId(), request.toOrderLines());
        return Response.accepted(OrderResponse.from(order))
                .location(URI.create("/api/v1/orders/" + order.id.toHexString()))
                .build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get an order")
    public OrderResponse get(@PathParam("id") String id) {
        return OrderResponse.from(orders.get(id));
    }

    @GET
    @Operation(summary = "List orders, newest first")
    public PageResponse<OrderResponse> list(
            @QueryParam("status") OrderStatus status,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        return new PageResponse<>(
                orders.list(status, page, size).stream().map(OrderResponse::from).toList(),
                page,
                size,
                orders.count(status));
    }

    @POST
    @Path("/{id}/cancel")
    @Operation(summary = "Cancel a pending or confirmed order; reserved stock is released")
    public OrderResponse cancel(@PathParam("id") String id) {
        return OrderResponse.from(orders.cancel(id));
    }
}
