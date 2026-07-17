package com.bestpriceengine.pricing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

public class OfferViewTest {

    @Test
    void totalCostIsBasePriceTimesQuantityWhenNoTierApplies() {
        OfferView offer = new OfferView(
                1L,                 // id
                1L,                 // priceVersion
                "usb-cable",        // productName
                "RetailerA",        // retailer
                5.00,               // basePrice
                2,                  // deliveryDays
                4.5,                // rating
                true,               // inStock
                100,                // availableQuantity
                List.of()           // quantityTiers -- empty, so it always falls back to basePrice
        );

        double result = offer.totalCostAt(4);

        assertEquals(20, result);
    }


    @Test
    void bulkTierPriceAppliesOnceQuantityMeetsThreshold() {
        OfferView offer = new OfferView(
                1L, 1L, "usb-cable", "RetailerA",
                5.00,               // basePrice
                2, 4.5, true, 100,
                List.of(new QuantityTierView(10, 4.00))   // 10+ units unlocks 4.00/unit
        );

        double result = offer.totalCostAt(10);

        assertEquals(40, result);
    }

    // Found by deliberately passing null for quantityTiers -- OfferView.unitPriceAt()
    // used to NPE on quantityTiers.stream() with no defense. Fixed via a compact
    // constructor that normalizes null to List.of(), matching the existing
    // empty-list fallback-to-basePrice behavior. This test now asserts the fix,
    // not the original crash.
    @Test
    void totalCostFallsBackToBasePriceWhenQuantityTiersIsNull() {
        OfferView offer = new OfferView(
                1L, 1L, "usb-cable", "RetailerA",
                5.00,               // basePrice
                2, 4.5, true, 100,
                null                // previously crashed; now normalized to List.of()
        );

        double result = offer.totalCostAt(10);

        assertEquals(50.00, result);
    }


}

