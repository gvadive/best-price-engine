package com.bestpriceengine.ingestion.service;

import com.bestpriceengine.ingestion.domain.BulkPricingRequest;
import com.bestpriceengine.ingestion.domain.Offer;
import com.bestpriceengine.ingestion.repository.BulkPricingRequestRepository;
import com.bestpriceengine.ingestion.repository.OfferRepository;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class BulkPricingService {

    private static final int QUOTE_VALID_SECONDS = 120;
    private static final double MAX_BULK_DISCOUNT = 0.30;
    private static final double QUANTITY_SCALE = 1000.0;

    private final OfferRepository offerRepository;
    private final BulkPricingRequestRepository bulkPricingRequestRepository;

    public BulkPricingService(OfferRepository offerRepository, BulkPricingRequestRepository bulkPricingRequestRepository) {
        this.offerRepository = offerRepository;
        this.bulkPricingRequestRepository = bulkPricingRequestRepository;
    }

    /**
     * Simulates a retailer negotiating a bulk price on the spot: the discount
     * off the base price grows with requested quantity, capped at
     * MAX_BULK_DISCOUNT. A real system would route this to the retailer's own
     * pricing desk; here it's a deterministic stand-in so the flow is fully
     * demonstrable without a human in the loop.
     */
    public BulkPricingRequest requestBulkPricing(Long offerId, int requestedQuantity) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new NoSuchElementException("no offer with id " + offerId));

        double discount = Math.min(MAX_BULK_DISCOUNT, requestedQuantity / QUANTITY_SCALE);
        double negotiatedUnitPrice = Math.round(offer.getBasePrice() * (1 - discount) * 100.0) / 100.0;

        BulkPricingRequest request = new BulkPricingRequest(offer, requestedQuantity, negotiatedUnitPrice, QUOTE_VALID_SECONDS);
        return bulkPricingRequestRepository.save(request);
    }
}
