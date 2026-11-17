package io.github.ardavan39.orderflow.web;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.github.ardavan39.orderflow.inventory.StockLevel;
import io.github.ardavan39.orderflow.inventory.StockRepository;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Lets an operator (or a test) set stock levels. */
@Path("/api/v1/stock")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Stock")
public class StockResource {

    public record StockUpdate(@Min(0) @Max(1_000_000) int available) {
    }

    @Inject
    StockRepository stock;

    @PUT
    @Path("/{sku}")
    @Operation(summary = "Set the available quantity of a SKU")
    public StockLevel set(
            @PathParam("sku") @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String sku,
            @Valid @NotNull StockUpdate update) {
        stock.setAvailable(sku, update.available());
        return new StockLevel(sku, update.available());
    }

    @GET
    @Path("/{sku}")
    @Operation(summary = "Get the available quantity of a SKU")
    public StockLevel get(@PathParam("sku") String sku) {
        return stock.find(sku).orElseThrow(() -> new NotFoundException("Unknown SKU " + sku));
    }

    @GET
    @Operation(summary = "List all stock levels")
    public List<StockLevel> list() {
        return stock.findAll();
    }
}
