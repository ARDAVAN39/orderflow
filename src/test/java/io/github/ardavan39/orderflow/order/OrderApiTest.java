package io.github.ardavan39.orderflow.order;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/** The HTTP contract: status codes, validation and response shape. */
@QuarkusTest
class OrderApiTest {

    private static final String ORDERS = "/api/v1/orders";

    @Test
    void placingAnOrderReturns202WithItsLocation() {
        given().contentType(ContentType.JSON)
                .body(order("customer-1", line("BOOK-1", 2, "12.50")))
                .when().post(ORDERS)
                .then()
                .statusCode(202)
                .header("Location", containsString(ORDERS + "/"))
                .body("id", notNullValue())
                .body("status", equalTo("PENDING"))
                .body("total", equalTo(25.00f))
                .body("lines", hasSize(1));
    }

    @Test
    void theOrderCanBeReadBack() {
        String id = given().contentType(ContentType.JSON)
                .body(order("customer-2", line("PEN-1", 1, "1.99")))
                .post(ORDERS)
                .then().statusCode(202)
                .extract().path("id");

        given().when().get(ORDERS + "/" + id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("customerId", equalTo("customer-2"))
                .body("lines[0].sku", equalTo("PEN-1"));
    }

    @Test
    void anOrderWithoutLinesIsRejected() {
        given().contentType(ContentType.JSON)
                .body(Map.of("customerId", "customer-1", "lines", List.of()))
                .when().post(ORDERS)
                .then().statusCode(400);
    }

    @Test
    void aLineWithAZeroQuantityOrNegativePriceIsRejected() {
        given().contentType(ContentType.JSON)
                .body(order("customer-1", line("BOOK-1", 0, "12.50")))
                .when().post(ORDERS)
                .then().statusCode(400);

        given().contentType(ContentType.JSON)
                .body(order("customer-1", line("BOOK-1", 1, "-1.00")))
                .when().post(ORDERS)
                .then().statusCode(400);
    }

    @Test
    void aMissingCustomerIsRejected() {
        given().contentType(ContentType.JSON)
                .body(Map.of("lines", List.of(line("BOOK-1", 1, "1.00"))))
                .when().post(ORDERS)
                .then().statusCode(400);
    }

    @Test
    void theSameSkuTwiceIsABadRequest() {
        given().contentType(ContentType.JSON)
                .body(order("customer-1", line("BOOK-1", 1, "1.00"), line("BOOK-1", 2, "1.00")))
                .when().post(ORDERS)
                .then()
                .statusCode(400)
                .body("detail", containsString("BOOK-1"));
    }

    @Test
    void unknownAndMalformedIdsAreNotFound() {
        given().when().get(ORDERS + "/" + new ObjectId().toHexString())
                .then().statusCode(404).body("status", equalTo(404));

        given().when().get(ORDERS + "/not-an-id")
                .then().statusCode(404);

        given().when().post(ORDERS + "/" + new ObjectId().toHexString() + "/cancel")
                .then().statusCode(404);
    }

    @Test
    void theListIsPagedAndThePageSizeIsLimited() {
        given().when().get(ORDERS + "?size=2")
                .then()
                .statusCode(200)
                .body("size", equalTo(2))
                .body("items.size()", lessThanOrEqualTo(2));

        given().when().get(ORDERS + "?size=1000")
                .then().statusCode(400);
    }

    @Test
    void stockCanBeSetAndRead() {
        given().contentType(ContentType.JSON)
                .body(Map.of("available", 7))
                .when().put("/api/v1/stock/API-TEST-1")
                .then().statusCode(200).body("available", equalTo(7));

        given().when().get("/api/v1/stock/API-TEST-1")
                .then().statusCode(200).body("sku", equalTo("API-TEST-1")).body("available", equalTo(7));

        given().when().get("/api/v1/stock/NOT-THERE")
                .then().statusCode(404).body("detail", endsWith("NOT-THERE"));
    }

    @Test
    void negativeStockIsRejected() {
        given().contentType(ContentType.JSON)
                .body(Map.of("available", -1))
                .when().put("/api/v1/stock/API-TEST-2")
                .then().statusCode(400);
    }

    private static Map<String, Object> order(String customerId, Map<?, ?>... lines) {
        return Map.of("customerId", customerId, "lines", List.of(lines));
    }

    private static Map<String, Object> line(String sku, int quantity, String unitPrice) {
        return Map.of("sku", sku, "quantity", quantity, "unitPrice", new BigDecimal(unitPrice));
    }
}
