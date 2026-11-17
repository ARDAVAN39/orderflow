package io.github.ardavan39.orderflow;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * The whole conversation, over real Kafka and MongoDB started by Quarkus Dev Services: an order is
 * placed through the API, the inventory reserves or rejects it through Kafka, and the order
 * picks up the answer.
 */
@QuarkusTest
class OrderFlowTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Test
    void anOrderWithEnoughStockIsConfirmedAndTheStockIsTaken() {
        String book = givenStock(10);
        String pen = givenStock(5);

        String id = placeOrder(Map.of("sku", book, "quantity", 3), Map.of("sku", pen, "quantity", 2));

        awaitStatus(id, "CONFIRMED");
        assertThat(available(book)).isEqualTo(7);
        assertThat(available(pen)).isEqualTo(3);
    }

    @Test
    void anOrderWithoutEnoughStockIsRejectedAndNothingIsTaken() {
        String book = givenStock(10);
        String pen = givenStock(1);

        String id = placeOrder(Map.of("sku", book, "quantity", 3), Map.of("sku", pen, "quantity", 2));

        awaitStatus(id, "REJECTED");
        String reason = given().get("/api/v1/orders/" + id).then().extract().path("rejectionReason");
        assertThat(reason).contains(pen);
        assertThat(available(book)).isEqualTo(10);
        assertThat(available(pen)).isEqualTo(1);
    }

    @Test
    void cancellingAConfirmedOrderGivesTheStockBack() {
        String book = givenStock(4);
        String id = placeOrder(Map.of("sku", book, "quantity", 4));
        awaitStatus(id, "CONFIRMED");
        assertThat(available(book)).isZero();

        given().post("/api/v1/orders/" + id + "/cancel")
                .then().statusCode(200).body("status", org.hamcrest.Matchers.equalTo("CANCELLED"));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(available(book)).isEqualTo(4));
        assertThat(status(id)).isEqualTo("CANCELLED");
    }

    @Test
    void aRejectedOrderCannotBeCancelled() {
        String book = givenStock(0);
        String id = placeOrder(Map.of("sku", book, "quantity", 1));
        awaitStatus(id, "REJECTED");

        given().post("/api/v1/orders/" + id + "/cancel")
                .then().statusCode(409);
    }

    @Test
    void twoOrdersCompetingForTheLastUnitsCannotBothWin() {
        String book = givenStock(3);

        String first = placeOrder(Map.of("sku", book, "quantity", 2));
        String second = placeOrder(Map.of("sku", book, "quantity", 2));

        await().atMost(TIMEOUT).until(() -> !status(first).equals("PENDING") && !status(second).equals("PENDING"));
        assertThat(List.of(status(first), status(second))).containsExactlyInAnyOrder("CONFIRMED", "REJECTED");
        assertThat(available(book)).isEqualTo(1);
    }

    private static String givenStock(int available) {
        String sku = "FLOW-" + UUID.randomUUID();
        given().contentType(ContentType.JSON)
                .body(Map.of("available", available))
                .put("/api/v1/stock/" + sku)
                .then().statusCode(200);
        return sku;
    }

    @SafeVarargs
    private static String placeOrder(Map<String, Object>... lines) {
        List<Map<String, Object>> priced = Arrays.stream(lines)
                .map(line -> {
                    Map<String, Object> withPrice = new HashMap<>(line);
                    withPrice.put("unitPrice", new BigDecimal("9.99"));
                    return withPrice;
                })
                .toList();
        return given().contentType(ContentType.JSON)
                .body(Map.of("customerId", "flow-test", "lines", priced))
                .post("/api/v1/orders")
                .then().statusCode(202)
                .extract().path("id");
    }

    private static void awaitStatus(String id, String expected) {
        await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(250)).until(() -> status(id).equals(expected));
    }

    private static String status(String id) {
        return given().get("/api/v1/orders/" + id).then().statusCode(200).extract().path("status");
    }

    private static int available(String sku) {
        return given().get("/api/v1/stock/" + sku).then().statusCode(200).extract().path("available");
    }
}
