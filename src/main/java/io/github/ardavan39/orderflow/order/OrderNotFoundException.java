package io.github.ardavan39.orderflow.order;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String id) {
        super("Order " + id + " does not exist");
    }
}
