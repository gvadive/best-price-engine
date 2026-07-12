package com.bestpriceengine.ingestion.web.dto;

import com.bestpriceengine.ingestion.domain.BulkPricingRequest;

public record BulkPricingRequestDto(
        Long id,
        Long offerId,
        String retailer,
        String productName,
        int requestedQuantity,
        double negotiatedUnitPrice,
        String expiresAt,
        boolean used
) {
    public static BulkPricingRequestDto from(BulkPricingRequest quote) {
        return new BulkPricingRequestDto(
                quote.getId(),
                quote.getOfferId(),
                quote.getRetailer(),
                quote.getProductName(),
                quote.getRequestedQuantity(),
                quote.getNegotiatedUnitPrice(),
                quote.getExpiresAt().toString(),
                quote.isUsed()
        );
    }
}
