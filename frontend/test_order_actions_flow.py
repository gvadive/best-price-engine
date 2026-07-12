"""
E2E for the order/bulk-pricing/price-watch action panel, driven through the
real browser against the docker-compose stack (nginx unifies Pricing
Engine's /api/compare and Ingestion Service's /api/orders etc. under one
origin -- exactly as it runs in production, so this targets that stack
rather than isolated subprocesses like test_compare_flow.py does).

Requires: docker-compose up -d (nginx on :8080).
"""
import uuid

import requests
from playwright.sync_api import Page, expect

APP_URL = "http://localhost:8080"
INGESTION_URL = "http://localhost:8081"


def _seed_offer(**overrides):
    product = f"E2E Widget {uuid.uuid4().hex[:8]}"
    payload = {
        "productName": product,
        "retailer": "RetailerE2E",
        "basePrice": 40.00,
        "deliveryDays": 2,
        "rating": 4.5,
        "inStock": True,
        "availableQuantity": 5,
        "quantityTiers": [],
    }
    payload.update(overrides)
    requests.post(f"{INGESTION_URL}/api/offers", json=payload)
    return product


def _register_and_authenticate(page: Page):
    """Placing orders/watches now requires auth. There's no login UI yet (that lands with
    the React rewrite), so -- same pattern as _seed_offer bypassing the UI for setup --
    register+login via a direct API call, then hand the resulting session cookie to the
    browser context. Cookies are host-scoped, not port-scoped, so a JSESSIONID obtained by
    talking to ingestion-service directly on :8081 is presented by the browser on :8080 too
    (same "localhost" host, and nginx proxies to the same backend session store anyway)."""
    username = f"e2e-user-{uuid.uuid4().hex[:8]}"
    password = "e2epass123"
    session = requests.Session()
    session.post(f"{INGESTION_URL}/api/auth/register", json={"username": username, "password": password})
    session.post(f"{INGESTION_URL}/api/auth/login", json={"username": username, "password": password})
    jsessionid = session.cookies.get("JSESSIONID")
    page.context.add_cookies([{"name": "JSESSIONID", "value": jsessionid, "domain": "localhost", "path": "/"}])
    return username


def _search(page: Page, product: str, quantity: int = 1):
    _register_and_authenticate(page)
    page.goto(APP_URL)
    page.fill("#product", product)
    page.fill("#quantity", str(quantity))
    page.click("button[type=submit]")
    expect(page.locator("#results-body tr")).to_have_count(1, timeout=5000)


def test_order_action_accepts_within_stock(page: Page):
    product = _seed_offer(availableQuantity=5)
    _search(page, product)

    page.click(".action-order-btn")
    expect(page.locator("#action-panel")).to_be_visible()
    page.fill("#order-quantity", "3")
    page.select_option("#order-fulfillment-mode", "EXACT_QUANTITY")
    page.click("#submit-order")

    expect(page.locator("#action-result")).to_contain_text("ACCEPTED", timeout=5000)
    expect(page.locator("#action-result")).to_contain_text("filled 3/3")


def test_order_action_rejects_exact_quantity_beyond_stock(page: Page):
    product = _seed_offer(availableQuantity=2)
    _search(page, product)

    page.click(".action-order-btn")
    page.fill("#order-quantity", "10")
    page.select_option("#order-fulfillment-mode", "EXACT_QUANTITY")
    page.click("#submit-order")

    expect(page.locator("#action-result")).to_contain_text("REJECTED", timeout=5000)


def test_bulk_pricing_then_order_at_negotiated_price(page: Page):
    product = _seed_offer(basePrice=100.00, availableQuantity=1000)
    _search(page, product)

    page.click(".action-bulk-btn")
    page.fill("#bulk-quantity", "600")
    page.click("#submit-bulk-request")

    expect(page.locator("#bulk-result")).to_contain_text("Negotiated price", timeout=5000)
    negotiated_text = page.locator("#bulk-result").text_content()
    assert "$100.00" not in negotiated_text  # must be discounted off the $100 base

    page.click("#submit-order-from-bulk")
    expect(page.locator("#action-result")).to_contain_text("ACCEPTED", timeout=5000)


def test_price_watch_order_fills_immediately_through_ui(page: Page):
    product = _seed_offer(basePrice=20.00, availableQuantity=5)
    _search(page, product)

    page.click(".action-watch-btn")
    page.fill("#watch-quantity", "1")
    page.fill("#watch-max-price", "50")  # well above current price -- should fill now
    page.fill("#watch-expires-after", "86400")
    page.click("#submit-price-watch")

    expect(page.locator("#action-result")).to_contain_text("FILLED", timeout=5000)
