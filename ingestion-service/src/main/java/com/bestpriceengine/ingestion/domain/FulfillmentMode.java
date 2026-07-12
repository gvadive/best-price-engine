package com.bestpriceengine.ingestion.domain;

public enum FulfillmentMode {
    /** Fill the entire requested quantity or nothing at all. */
    EXACT_QUANTITY,
    /** Fill as much as is available; cancel whatever's left over. */
    BEST_EFFORT
}
