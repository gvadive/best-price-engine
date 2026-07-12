package com.bestpriceengine.pricing.engine;

import com.bestpriceengine.pricing.domain.Criteria;
import com.bestpriceengine.pricing.domain.OfferView;
import com.bestpriceengine.pricing.domain.PricingStrategy;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;
import org.springframework.stereotype.Component;

@Component
public class PriceRanker {

    public List<OfferView> rank(List<OfferView> offers, Criteria criteria) {
        ToDoubleFunction<OfferView> priceKey = priceKeyFor(criteria);

        return offers.stream()
                .filter(OfferView::inStock)
                .filter(o -> withinDeliveryWindow(o, criteria))
                .filter(o -> meetsRatingBar(o, criteria))
                .sorted(Comparator.comparingDouble(priceKey::applyAsDouble))
                .toList();
    }

    private ToDoubleFunction<OfferView> priceKeyFor(Criteria criteria) {
        if (criteria.strategy() == PricingStrategy.UNIT_PRICE) {
            return offer -> offer.unitPriceAt(criteria.quantity());
        }
        return offer -> offer.totalCostAt(criteria.quantity());
    }

    private boolean withinDeliveryWindow(OfferView offer, Criteria criteria) {
        return criteria.maxDeliveryDays() == null || offer.deliveryDays() <= criteria.maxDeliveryDays();
    }

    private boolean meetsRatingBar(OfferView offer, Criteria criteria) {
        return criteria.minRating() == null || offer.rating() >= criteria.minRating();
    }
}
