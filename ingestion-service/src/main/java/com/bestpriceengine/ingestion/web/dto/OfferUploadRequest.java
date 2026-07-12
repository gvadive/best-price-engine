package com.bestpriceengine.ingestion.web.dto;

import java.util.List;

public record OfferUploadRequest(
        String productName,
        String retailer,
        double basePrice,
        int deliveryDays,
        double rating,
        boolean inStock,
        int availableQuantity,
        List<QuantityTierDto> quantityTiers
) {
}
