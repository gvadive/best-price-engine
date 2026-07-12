package com.bestpriceengine.pricing.domain;

import java.util.List;

public record OfferView(
        Long id,
        long priceVersion,
        String productName,
        String retailer,
        double basePrice,
        int deliveryDays,
        double rating,
        boolean inStock,
        int availableQuantity,
        List<QuantityTierView> quantityTiers
) {
    public double unitPriceAt(int quantity) {
        return quantityTiers.stream()
                .filter(t -> quantity >= t.minQuantity())
                .mapToDouble(QuantityTierView::unitPrice)
                .min()
                .orElse(basePrice);
    }

    public double totalCostAt(int quantity) {
        return Math.round(unitPriceAt(quantity) * quantity * 100.0) / 100.0;
    }
}
