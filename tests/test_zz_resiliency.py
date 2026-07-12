"""
Kills the shared ingestion_process fixture, so this file is named to sort
and run last -- every other test file needs ingestion alive.
"""
import requests

from conftest import PRICING_URL


def test_pricing_falls_back_to_cache_when_ingestion_dies(pricing_process, ingestion_process, seeded_usb_cable):
    warm_response = requests.get(f"{PRICING_URL}/api/compare", params={
        "product": seeded_usb_cable,
        "quantity": 5,
    })
    assert warm_response.status_code == 200
    expected = warm_response.json()

    ingestion_process.kill()
    ingestion_process.wait()

    for _ in range(6):
        response = requests.get(f"{PRICING_URL}/api/compare", params={
            "product": seeded_usb_cable,
            "quantity": 5,
        })
        assert response.status_code == 200, "pricing-engine should degrade to cache, not fail"
        assert response.json() == expected
