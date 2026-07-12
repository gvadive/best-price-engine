package com.bestpriceengine.pricing.web;

import com.bestpriceengine.pricing.client.IngestionClient;
import com.bestpriceengine.pricing.domain.Criteria;
import com.bestpriceengine.pricing.domain.OfferView;
import com.bestpriceengine.pricing.domain.PricingStrategy;
import com.bestpriceengine.pricing.engine.PriceRanker;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CompareController {

    private final IngestionClient ingestionClient;
    private final PriceRanker priceRanker;

    public CompareController(IngestionClient ingestionClient, PriceRanker priceRanker) {
        this.ingestionClient = ingestionClient;
        this.priceRanker = priceRanker;
    }

    @GetMapping("/api/compare")
    public List<OfferView> compare(
            @RequestParam String product,
            @RequestParam(defaultValue = "1") int quantity,
            @RequestParam(required = false) Integer maxDeliveryDays,
            @RequestParam(required = false) Double minRating,
            @RequestParam(defaultValue = "TOTAL_COST") PricingStrategy strategy
    ) {
        List<OfferView> offers = ingestionClient.offersFor(product);
        Criteria criteria = new Criteria(quantity, maxDeliveryDays, minRating, strategy);
        return priceRanker.rank(offers, criteria);
    }
}
