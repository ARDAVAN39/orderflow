#!/usr/bin/env bash
# Checks a running instance end to end: set stock, place an order, wait for the inventory's
# answer through Kafka, check the stock was taken. Used by CI against the Compose setup.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SKU="SMOKE-$(date +%s)-$RANDOM"

echo "Waiting for $BASE_URL to be ready"
for _ in $(seq 1 90); do
    if curl -fsS "$BASE_URL/q/health/ready" > /dev/null 2>&1; then
        break
    fi
    sleep 2
done
curl -fsS "$BASE_URL/q/health/ready" > /dev/null

curl -fsS -X PUT "$BASE_URL/api/v1/stock/$SKU" \
    -H 'Content-Type: application/json' -d '{"available": 5}' > /dev/null

ORDER_ID=$(curl -fsS -X POST "$BASE_URL/api/v1/orders" \
    -H 'Content-Type: application/json' \
    -d "{\"customerId\": \"smoke\", \"lines\": [{\"sku\": \"$SKU\", \"quantity\": 2, \"unitPrice\": 9.99}]}" \
    | jq -r .id)
echo "Placed order $ORDER_ID"

STATUS=PENDING
for _ in $(seq 1 60); do
    STATUS=$(curl -fsS "$BASE_URL/api/v1/orders/$ORDER_ID" | jq -r .status)
    [ "$STATUS" != "PENDING" ] && break
    sleep 1
done

AVAILABLE=$(curl -fsS "$BASE_URL/api/v1/stock/$SKU" | jq -r .available)
echo "Order status: $STATUS, stock left: $AVAILABLE"

if [ "$STATUS" != "CONFIRMED" ] || [ "$AVAILABLE" != "3" ]; then
    echo "Smoke test failed" >&2
    exit 1
fi
echo "Smoke test passed"
