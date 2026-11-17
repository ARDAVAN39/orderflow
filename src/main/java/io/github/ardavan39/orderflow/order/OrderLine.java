package io.github.ardavan39.orderflow.order;

import java.math.BigDecimal;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** One line of an order. Public fields and a no-arg constructor are what the MongoDB codec needs. */
@RegisterForReflection
public class OrderLine {

    public String sku;
    public int quantity;
    public BigDecimal unitPrice;

    public OrderLine() {
    }

    public OrderLine(String sku, int quantity, BigDecimal unitPrice) {
        this.sku = sku;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
