package io.github.ardavan39.orderflow.order;

public class OrderNotCancellableException extends RuntimeException {

    public OrderNotCancellableException(String id, OrderStatus status) {
        super("Order " + id + " is " + status + " and can no longer be cancelled");
    }
}
