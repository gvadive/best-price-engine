import requests

from conftest import INGESTION_URL


def _create_offer(**overrides):
    payload = {
        "productName": "Test Widget",
        "retailer": "RetailerOrder",
        "basePrice": 10.00,
        "deliveryDays": 2,
        "rating": 4.0,
        "inStock": True,
        "availableQuantity": 5,
        "quantityTiers": [],
    }
    payload.update(overrides)
    response = requests.post(f"{INGESTION_URL}/api/offers", json=payload)
    return response.json()


def test_exact_quantity_order_accepts_when_stock_matches(ingestion_process, auth_session):
    offer = _create_offer(availableQuantity=5)
    result = auth_session.post(f"{INGESTION_URL}/api/orders", json={
        "offerId": offer["id"], "quantity": 5, "quotedPriceVersion": offer["priceVersion"],
        "fulfillmentMode": "EXACT_QUANTITY", "bulkPricingRequestId": None,
    }).json()

    assert result["status"] == "ACCEPTED"
    assert result["filledQuantity"] == 5
    assert result["canceledQuantity"] == 0


def test_exact_quantity_order_rejects_when_stock_insufficient(ingestion_process, auth_session):
    offer = _create_offer(availableQuantity=5)
    result = auth_session.post(f"{INGESTION_URL}/api/orders", json={
        "offerId": offer["id"], "quantity": 10, "quotedPriceVersion": offer["priceVersion"],
        "fulfillmentMode": "EXACT_QUANTITY", "bulkPricingRequestId": None,
    }).json()

    assert result["status"] == "REJECTED"
    assert result["filledQuantity"] == 0
    assert "insufficient" in result["rejectReason"] or "available" in result["rejectReason"]


def test_best_effort_order_partially_fills_when_stock_insufficient(ingestion_process, auth_session):
    offer = _create_offer(availableQuantity=5)
    result = auth_session.post(f"{INGESTION_URL}/api/orders", json={
        "offerId": offer["id"], "quantity": 10, "quotedPriceVersion": offer["priceVersion"],
        "fulfillmentMode": "BEST_EFFORT", "bulkPricingRequestId": None,
    }).json()

    assert result["status"] == "PARTIALLY_FILLED"
    assert result["filledQuantity"] == 5
    assert result["canceledQuantity"] == 5


def test_order_rejected_when_quoted_price_version_is_stale(ingestion_process, auth_session):
    offer = _create_offer(availableQuantity=10)
    stale_version = offer["priceVersion"]

    requests.put(f"{INGESTION_URL}/api/offers/{offer['id']}/price", json={"newBasePrice": 15.00})

    result = auth_session.post(f"{INGESTION_URL}/api/orders", json={
        "offerId": offer["id"], "quantity": 1, "quotedPriceVersion": stale_version,
        "fulfillmentMode": "BEST_EFFORT", "bulkPricingRequestId": None,
    }).json()

    assert result["status"] == "REJECTED"
    assert "price changed" in result["rejectReason"]


def test_bulk_pricing_negotiates_discount_and_is_single_use(ingestion_process, auth_session):
    offer = _create_offer(basePrice=100.00, availableQuantity=1000)

    quote = requests.post(f"{INGESTION_URL}/api/bulk-pricing-requests", json={
        "offerId": offer["id"], "requestedQuantity": 500,
    }).json()

    assert quote["negotiatedUnitPrice"] < offer["basePrice"]
    assert quote["used"] is False

    first = auth_session.post(f"{INGESTION_URL}/api/orders", json={
        "offerId": offer["id"], "quantity": 500, "quotedPriceVersion": 0,
        "fulfillmentMode": "EXACT_QUANTITY", "bulkPricingRequestId": quote["id"],
    }).json()
    assert first["status"] == "ACCEPTED"
    assert first["unitPrice"] == quote["negotiatedUnitPrice"]

    second = auth_session.post(f"{INGESTION_URL}/api/orders", json={
        "offerId": offer["id"], "quantity": 1, "quotedPriceVersion": 0,
        "fulfillmentMode": "BEST_EFFORT", "bulkPricingRequestId": quote["id"],
    }).json()
    assert second["status"] == "REJECTED"
    assert "already been used" in second["rejectReason"]


def test_price_watch_order_fills_immediately_when_ceiling_already_satisfied(ingestion_process, auth_session):
    offer = _create_offer(basePrice=20.00, availableQuantity=5)

    watch = auth_session.post(f"{INGESTION_URL}/api/price-watch-orders", json={
        "offerId": offer["id"], "quantity": 1, "maxAcceptablePrice": 25.00,
        "fulfillmentMode": "EXACT_QUANTITY", "expiresAfterSeconds": 86400,
    }).json()

    assert watch["status"] == "FILLED"
    assert watch["resultingOrderId"] is not None


def test_price_watch_order_fills_when_a_later_price_update_meets_the_ceiling(ingestion_process, auth_session):
    offer = _create_offer(basePrice=50.00, availableQuantity=5)

    watch = auth_session.post(f"{INGESTION_URL}/api/price-watch-orders", json={
        "offerId": offer["id"], "quantity": 1, "maxAcceptablePrice": 30.00,
        "fulfillmentMode": "EXACT_QUANTITY", "expiresAfterSeconds": 86400,
    }).json()
    assert watch["status"] == "PENDING"

    requests.put(f"{INGESTION_URL}/api/offers/{offer['id']}/price", json={"newBasePrice": 28.00})

    updated = requests.get(f"{INGESTION_URL}/api/price-watch-orders/{watch['id']}").json()
    assert updated["status"] == "FILLED"


def test_price_watch_order_expires_via_scheduled_sweep(ingestion_process, auth_session):
    offer = _create_offer(basePrice=50.00, availableQuantity=5)

    watch = auth_session.post(f"{INGESTION_URL}/api/price-watch-orders", json={
        "offerId": offer["id"], "quantity": 1, "maxAcceptablePrice": 1.00,
        "fulfillmentMode": "EXACT_QUANTITY", "expiresAfterSeconds": 2,
    }).json()
    assert watch["status"] == "PENDING"

    import time
    time.sleep(8)  # 2s expiry + up to 5s sweep interval + margin

    updated = requests.get(f"{INGESTION_URL}/api/price-watch-orders/{watch['id']}").json()
    assert updated["status"] == "EXPIRED"


def test_order_history_shows_only_the_authenticated_users_own_orders(ingestion_process, auth_session):
    offer = _create_offer(availableQuantity=10)

    auth_session.post(f"{INGESTION_URL}/api/orders", json={
        "offerId": offer["id"], "quantity": 1, "quotedPriceVersion": offer["priceVersion"],
        "fulfillmentMode": "EXACT_QUANTITY", "bulkPricingRequestId": None,
    })
    auth_session.post(f"{INGESTION_URL}/api/orders", json={
        "offerId": offer["id"], "quantity": 2, "quotedPriceVersion": offer["priceVersion"],
        "fulfillmentMode": "BEST_EFFORT", "bulkPricingRequestId": None,
    })

    other_session = requests.Session()
    import uuid
    other_username = f"otheruser-{uuid.uuid4().hex[:8]}"
    other_session.post(f"{INGESTION_URL}/api/auth/register", json={"username": other_username, "password": "otherpass123"})
    other_session.post(f"{INGESTION_URL}/api/auth/login", json={"username": other_username, "password": "otherpass123"})
    other_session.post(f"{INGESTION_URL}/api/orders", json={
        "offerId": offer["id"], "quantity": 1, "quotedPriceVersion": offer["priceVersion"],
        "fulfillmentMode": "EXACT_QUANTITY", "bulkPricingRequestId": None,
    })

    history = auth_session.get(f"{INGESTION_URL}/api/orders").json()
    assert len(history) == 2
    assert all(order["username"] == auth_session.username for order in history)

    other_history = other_session.get(f"{INGESTION_URL}/api/orders").json()
    assert len(other_history) == 1
    assert other_history[0]["username"] == other_username


def test_orders_and_price_watch_orders_reject_unauthenticated_requests(ingestion_process):
    offer = _create_offer(availableQuantity=5)

    order_response = requests.post(f"{INGESTION_URL}/api/orders", json={
        "offerId": offer["id"], "quantity": 1, "quotedPriceVersion": offer["priceVersion"],
        "fulfillmentMode": "EXACT_QUANTITY", "bulkPricingRequestId": None,
    })
    assert order_response.status_code == 401

    history_response = requests.get(f"{INGESTION_URL}/api/orders")
    assert history_response.status_code == 401

    watch_response = requests.post(f"{INGESTION_URL}/api/price-watch-orders", json={
        "offerId": offer["id"], "quantity": 1, "maxAcceptablePrice": 100.00,
        "fulfillmentMode": "EXACT_QUANTITY", "expiresAfterSeconds": 60,
    })
    assert watch_response.status_code == 401
