package com.bestpriceengine.ingestion.web.dto;

import com.bestpriceengine.ingestion.domain.Offer;
import java.util.List;

public record OfferDto(
        Long id,
        long priceVersion,
        String productName,
        String retailer,
        double basePrice,
        int deliveryDays,
        double rating,
        boolean inStock,
        int availableQuantity,
        List<QuantityTierDto> quantityTiers
) {
    public static OfferDto from(Offer offer) {
        List<QuantityTierDto> tiers = offer.getQuantityTiers().stream()
                .map(t -> new QuantityTierDto(t.getMinQuantity(), t.getUnitPrice()))
                .toList();
        return new OfferDto(
                offer.getId(),
                offer.getPriceVersion(),
                offer.getProductName(),
                offer.getRetailer(),
                offer.getBasePrice(),
                offer.getDeliveryDays(),
                offer.getRating(),
                offer.isInStock(),
                offer.getAvailableQuantity(),
                tiers
        );
    }
}
