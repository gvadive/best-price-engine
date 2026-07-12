"""
Core comparison engine: given a set of retailer offers for a product and a
buyer's criteria, selects the best offer.

Runs in two places in this project: as a plain Python module (unit-tested
directly with pytest), and packaged as the Lambda that the ingestion
pipeline calls after normalizing a scraped/uploaded retailer catalog.
"""
from dataclasses import dataclass, field
from enum import Enum


class PricingStrategy(Enum):
    UNIT_PRICE = "unit_price"      # cheapest price per single unit
    TOTAL_COST = "total_cost"      # cheapest total for the exact requested quantity


@dataclass
class QuantityTier:
    min_quantity: int
    unit_price: float


@dataclass
class Offer:
    retailer: str
    base_unit_price: float
    quantity_tiers: list[QuantityTier] = field(default_factory=list)
    delivery_days: int = 3
    rating: float = 4.0
    in_stock: bool = True

    def unit_price_at(self, quantity: int) -> float:
        """Cheapest applicable unit price for buying `quantity` units."""
        applicable = [t.unit_price for t in self.quantity_tiers if quantity >= t.min_quantity]
        return min(applicable) if applicable else self.base_unit_price

    def total_cost_at(self, quantity: int) -> float:
        return round(self.unit_price_at(quantity) * quantity, 2)


@dataclass
class Criteria:
    quantity: int = 1
    max_delivery_days: int | None = None
    min_rating: float | None = None
    strategy: PricingStrategy = PricingStrategy.TOTAL_COST


class NoOfferAvailableError(Exception):
    """Raised when no offer satisfies the given criteria."""


def eligible_offers(offers: list[Offer], criteria: Criteria) -> list[Offer]:
    result = [o for o in offers if o.in_stock]
    if criteria.max_delivery_days is not None:
        result = [o for o in result if o.delivery_days <= criteria.max_delivery_days]
    if criteria.min_rating is not None:
        result = [o for o in result if o.rating >= criteria.min_rating]
    return result


def rank_offers(offers: list[Offer], criteria: Criteria) -> list[Offer]:
    """Returns offers sorted best-first under the given criteria."""
    candidates = eligible_offers(offers, criteria)
    if not candidates:
        raise NoOfferAvailableError(f"no offer meets criteria: {criteria}")

    if criteria.strategy == PricingStrategy.UNIT_PRICE:
        key = lambda o: o.unit_price_at(criteria.quantity)
    else:
        key = lambda o: o.total_cost_at(criteria.quantity)

    return sorted(candidates, key=key)


def best_offer(offers: list[Offer], criteria: Criteria) -> Offer:
    return rank_offers(offers, criteria)[0]
