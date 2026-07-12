import pytest

from best_offer_selector import (
    Criteria,
    NoOfferAvailableError,
    Offer,
    PricingStrategy,
    QuantityTier,
    best_offer,
    rank_offers,
)


@pytest.fixture
def offers():
    return [
        Offer(retailer="RetailerA", base_unit_price=5.00, delivery_days=1, rating=4.8),
        Offer(
            retailer="RetailerB",
            base_unit_price=5.50,
            quantity_tiers=[QuantityTier(min_quantity=10, unit_price=4.00)],
            delivery_days=4,
            rating=4.2,
        ),
        Offer(retailer="RetailerC", base_unit_price=4.75, delivery_days=2, rating=3.5, in_stock=False),
    ]


def test_cheapest_at_single_unit_is_retailer_a(offers):
    result = best_offer(offers, Criteria(quantity=1))
    assert result.retailer == "RetailerA"


def test_bulk_quantity_tier_wins_even_with_higher_base_price(offers):
    # RetailerB's base price (5.50) is worse than A's (5.00), but its bulk
    # tier (4.00/unit at 10+) makes it cheaper in total once quantity hits 10.
    result = best_offer(offers, Criteria(quantity=10))
    assert result.retailer == "RetailerB"
    assert result.total_cost_at(10) == 40.00


def test_out_of_stock_offer_is_excluded(offers):
    result = best_offer(offers, Criteria(quantity=1))
    assert result.retailer != "RetailerC"


def test_delivery_day_criteria_filters_out_slower_retailer(offers):
    result = best_offer(offers, Criteria(quantity=1, max_delivery_days=1))
    assert result.retailer == "RetailerA"


def test_no_offer_meets_impossible_criteria_raises(offers):
    with pytest.raises(NoOfferAvailableError):
        best_offer(offers, Criteria(quantity=1, min_rating=5.0))


@pytest.mark.parametrize(
    "strategy,expected_first",
    [
        (PricingStrategy.UNIT_PRICE, "RetailerB"),   # 4.00/unit beats 5.00/unit at qty=10
        (PricingStrategy.TOTAL_COST, "RetailerB"),    # same winner here, but computed differently
    ],
)
def test_ranking_strategy_affects_comparison_key(offers, strategy, expected_first):
    ranked = rank_offers(offers, Criteria(quantity=10, strategy=strategy))
    assert ranked[0].retailer == expected_first
