package com.bestpriceengine.ingestion.service;

import com.bestpriceengine.ingestion.domain.Offer;
import com.bestpriceengine.ingestion.repository.OfferRepository;
import com.bestpriceengine.ingestion.web.dto.OfferUploadRequest;
import com.bestpriceengine.ingestion.web.dto.QuantityTierDto;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {

    private final OfferRepository offerRepository;
    private final PriceWatchOrderService priceWatchOrderService;

    public CatalogService(OfferRepository offerRepository, PriceWatchOrderService priceWatchOrderService) {
        this.offerRepository = offerRepository;
        this.priceWatchOrderService = priceWatchOrderService;
    }

    public Offer addOffer(OfferUploadRequest request) {
        Offer offer = new Offer(
                request.productName(),
                request.retailer(),
                request.basePrice(),
                request.deliveryDays(),
                request.rating(),
                request.inStock(),
                request.availableQuantity()
        );
        for (QuantityTierDto tier : request.quantityTiers()) {
            offer.addTier(tier.minQuantity(), tier.unitPrice());
        }
        return offerRepository.save(offer);
    }

    public List<Offer> offersForProduct(String productName) {
        return offerRepository.findByProductName(productName);
    }

    /** Powers search-as-you-type: distinct product names containing the query, case-insensitive. */
    public List<String> searchProductNames(String query) {
        return offerRepository.findDistinctProductNamesMatching(query == null ? "" : query);
    }

    // Simulates the retailer's own price feed changing. Bumps priceVersion
    // (a manually-managed counter, only incremented here) so any order
    // already quoted at the old version is rejected instead of silently
    // filled at the new price, and triggers a check of pending price watches.
    public Offer updatePrice(Long offerId, double newBasePrice) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new NoSuchElementException("no offer with id " + offerId));
        offer.updatePrice(newBasePrice);
        Offer saved = offerRepository.save(offer);
        priceWatchOrderService.onPriceUpdated(saved);
        return saved;
    }
}
