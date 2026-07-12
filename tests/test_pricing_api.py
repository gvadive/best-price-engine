import requests

from conftest import PRICING_URL


def test_compare_at_low_quantity_favours_lower_base_price(pricing_process, seeded_usb_cable):
    response = requests.get(f"{PRICING_URL}/api/compare", params={
        "product": seeded_usb_cable,
        "quantity": 1,
    })

    assert response.status_code == 200
    ranked = response.json()
    assert ranked[0]["retailer"] == "RetailerA"


def test_compare_at_bulk_quantity_favours_tiered_price(pricing_process, seeded_usb_cable):
    response = requests.get(f"{PRICING_URL}/api/compare", params={
        "product": seeded_usb_cable,
        "quantity": 5,
    })

    assert response.status_code == 200
    ranked = response.json()
    assert ranked[0]["retailer"] == "RetailerB"


def test_compare_respects_max_delivery_days_filter(pricing_process, seeded_usb_cable):
    response = requests.get(f"{PRICING_URL}/api/compare", params={
        "product": seeded_usb_cable,
        "quantity": 1,
        "maxDeliveryDays": 1,
    })

    assert response.status_code == 200
    ranked = response.json()
    assert all(o["deliveryDays"] <= 1 for o in ranked)
    assert ranked[0]["retailer"] == "RetailerB"  # only 1-day delivery retailer
