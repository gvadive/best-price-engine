package com.bestpriceengine.ingestion.web.dto;

import com.bestpriceengine.ingestion.domain.FulfillmentMode;

// quotedPriceVersion is ignored when bulkPricingRequestId is set -- a bulk
// pricing request's own expiry/single-use check replaces the live
// price-version check.
public record OrderRequest(
        Long offerId,
        int quantity,
        long quotedPriceVersion,
        FulfillmentMode fulfillmentMode,
        Long bulkPricingRequestId
) {
}
