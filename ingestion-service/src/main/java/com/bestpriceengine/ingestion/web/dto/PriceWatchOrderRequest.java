package com.bestpriceengine.ingestion.web.dto;

import com.bestpriceengine.ingestion.domain.FulfillmentMode;

public record PriceWatchOrderRequest(
        Long offerId,
        int quantity,
        double maxAcceptablePrice,
        FulfillmentMode fulfillmentMode,
        int expiresAfterSeconds
) {
}
