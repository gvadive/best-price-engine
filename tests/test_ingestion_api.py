import requests

from conftest import INGESTION_URL


def test_post_offer_returns_created_with_body(ingestion_process):
    response = requests.post(f"{INGESTION_URL}/api/offers", json={
        "productName": "Keyboard",
        "retailer": "RetailerX",
        "basePrice": 25.00,
        "deliveryDays": 3,
        "rating": 4.1,
        "inStock": True,
        "quantityTiers": [],
    })

    assert response.status_code == 201
    body = response.json()
    assert body["retailer"] == "RetailerX"
    assert body["basePrice"] == 25.00


def test_get_offers_returns_only_matching_product(ingestion_process, seeded_usb_cable):
    response = requests.get(f"{INGESTION_URL}/api/offers", params={"product": seeded_usb_cable})

    assert response.status_code == 200
    offers = response.json()
    assert len(offers) == 2
    assert {o["retailer"] for o in offers} == {"RetailerA", "RetailerB"}
    assert all(o["productName"] == "USB Cable" for o in offers)


def test_get_offers_for_unknown_product_returns_empty_list(ingestion_process):
    response = requests.get(f"{INGESTION_URL}/api/offers", params={"product": "Nonexistent XYZ"})

    assert response.status_code == 200
    assert response.json() == []
