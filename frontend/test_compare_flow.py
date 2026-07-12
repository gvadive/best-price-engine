"""
Top of the test pyramid: drives the real frontend in a real browser against
the real running services (via nginx, the same single origin production
uses). Everything below this (pytest unit + API tests) proves the pieces
work in isolation; this proves they work together.

Seeds its own data rather than relying on state left over from elsewhere --
a test that only passes because of a prior manual curl command isn't
reproducible, and was a real, latent bug in an earlier version of this file.
"""
import re
import uuid

import requests
from playwright.sync_api import Page, expect

APP_URL = "http://localhost:8080"
INGESTION_URL = "http://localhost:8081"


def _seed_usb_cable():
    product = f"USB Cable {uuid.uuid4().hex[:8]}"
    requests.post(f"{INGESTION_URL}/api/offers", json={
        "productName": product, "retailer": "RetailerA", "basePrice": 8.00,
        "deliveryDays": 2, "rating": 4.5, "inStock": True,
        "availableQuantity": 999, "quantityTiers": [],
    })
    requests.post(f"{INGESTION_URL}/api/offers", json={
        "productName": product, "retailer": "RetailerB", "basePrice": 9.50,
        "deliveryDays": 1, "rating": 4.9, "inStock": True, "availableQuantity": 999,
        "quantityTiers": [{"minQuantity": 5, "unitPrice": 6.50}],
    })
    return product


def test_compare_shows_bulk_tier_winner(page: Page):
    product = _seed_usb_cable()
    page.goto(APP_URL)

    page.fill("#product", product)
    page.fill("#quantity", "5")
    page.click("button[type=submit]")

    expect(page.locator("#status")).to_contain_text("offer(s) found", timeout=5000)

    rows = page.locator("#results-body tr")
    expect(rows).to_have_count(2)

    cheapest_row = page.locator("tr.cheapest")
    expect(cheapest_row).to_contain_text("RetailerB")
    expect(cheapest_row).to_contain_text("$32.50")


def test_compare_at_single_unit_favours_lower_base_price(page: Page):
    product = _seed_usb_cable()
    page.goto(APP_URL)

    page.fill("#product", product)
    page.fill("#quantity", "1")
    page.click("button[type=submit]")

    expect(page.locator("#status")).to_contain_text("offer(s) found", timeout=5000)
    cheapest_row = page.locator("tr.cheapest")
    expect(cheapest_row).to_contain_text("RetailerA")


def test_no_match_shows_helpful_message(page: Page):
    page.goto(APP_URL)

    page.fill("#product", "Nonexistent Product XYZ")
    page.fill("#quantity", "1")
    page.click("button[type=submit]")

    expect(page.locator("#status")).to_contain_text(
        re.compile("no offers", re.IGNORECASE), timeout=5000
    )
