package com.bestpriceengine.ingestion.web.dto;

import com.bestpriceengine.ingestion.domain.PriceWatchOrder;

public record PriceWatchOrderDto(
        Long id,
        Long offerId,
        String retailer,
        String productName,
        int quantity,
        double maxAcceptablePrice,
        String fulfillmentMode,
        String status,
        Long resultingOrderId,
        String expiresAt,
        String username
) {
    public static PriceWatchOrderDto from(PriceWatchOrder watch) {
        return new PriceWatchOrderDto(
                watch.getId(),
                watch.getOfferId(),
                watch.getRetailer(),
                watch.getProductName(),
                watch.getQuantity(),
                watch.getMaxAcceptablePrice(),
                watch.getFulfillmentMode().name(),
                watch.getStatus().name(),
                watch.getResultingOrderId(),
                watch.getExpiresAt().toString(),
                watch.getUsername()
        );
    }
}
