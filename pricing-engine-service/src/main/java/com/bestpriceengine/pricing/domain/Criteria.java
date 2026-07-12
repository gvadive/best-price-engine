package com.bestpriceengine.pricing.domain;

public record Criteria(
        int quantity,
        Integer maxDeliveryDays,
        Double minRating,
        PricingStrategy strategy
) {
    public static Criteria defaults(int quantity) {
        return new Criteria(quantity, null, null, PricingStrategy.TOTAL_COST);
    }
}
