package com.bestpriceengine.ingestion.web;

import com.bestpriceengine.ingestion.service.BulkPricingService;
import com.bestpriceengine.ingestion.web.dto.BulkPricingRequestDto;
import com.bestpriceengine.ingestion.web.dto.BulkPricingRequestPayload;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BulkPricingController {

    private final BulkPricingService bulkPricingService;

    public BulkPricingController(BulkPricingService bulkPricingService) {
        this.bulkPricingService = bulkPricingService;
    }

    @PostMapping("/api/bulk-pricing-requests")
    public BulkPricingRequestDto requestBulkPricing(@RequestBody BulkPricingRequestPayload request) {
        var quote = bulkPricingService.requestBulkPricing(request.offerId(), request.requestedQuantity());
        return BulkPricingRequestDto.from(quote);
    }
}
