package com.bestpriceengine.ingestion.web.dto;

public record BulkPricingRequestPayload(Long offerId, int requestedQuantity) {
}
