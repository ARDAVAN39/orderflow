package io.github.ardavan39.orderflow.inventory;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Updates.inc;
import static com.mongodb.client.model.Updates.set;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Stock levels, one document per SKU: {@code {_id: sku, available: n}}.
 *
 * <p>Taking stock is a single conditional update ({@code available >= quantity}), so two orders
 * competing for the last units cannot both succeed and the level never goes below zero.
 */
@ApplicationScoped
public class StockRepository {

    private final MongoCollection<Document> stock;

    StockRepository(MongoClient client, @ConfigProperty(name = "quarkus.mongodb.database") String database) {
        this.stock = client.getDatabase(database).getCollection("stock");
    }

    public void setAvailable(String sku, int available) {
        stock.updateOne(eq("_id", sku), set("available", available), new UpdateOptions().upsert(true));
    }

    public Optional<StockLevel> find(String sku) {
        return Optional.ofNullable(stock.find(eq("_id", sku)).first()).map(StockRepository::toStockLevel);
    }

    public List<StockLevel> findAll() {
        List<StockLevel> levels = new ArrayList<>();
        for (Document document : stock.find().sort(Sorts.ascending("_id"))) {
            levels.add(toStockLevel(document));
        }
        return levels;
    }

    /** @return true if the quantity was available and has been taken */
    public boolean take(String sku, int quantity) {
        return stock.updateOne(and(eq("_id", sku), gte("available", quantity)), inc("available", -quantity))
                .getModifiedCount() == 1;
    }

    public void putBack(String sku, int quantity) {
        stock.updateOne(eq("_id", sku), inc("available", quantity));
    }

    private static StockLevel toStockLevel(Document document) {
        return new StockLevel(document.getString("_id"), document.getInteger("available", 0));
    }
}
