package com.bestpriceengine.ingestion.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * A retailer-negotiated bulk price for a specific quantity,
 * distinct from the publicly-listed price/tiers. Short-lived and
 * single-use -- an order can spend it once, before it expires.
 */
@Entity
public class BulkPricingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long offerId;
    private String retailer;
    private String productName;
    private int requestedQuantity;
    private double negotiatedUnitPrice;
    private Instant createdAt;
    private Instant expiresAt;
    private boolean used;

    protected BulkPricingRequest() {
    }

    public BulkPricingRequest(Offer offer, int requestedQuantity, double negotiatedUnitPrice, int validForSeconds) {
        this.offerId = offer.getId();
        this.retailer = offer.getRetailer();
        this.productName = offer.getProductName();
        this.requestedQuantity = requestedQuantity;
        this.negotiatedUnitPrice = negotiatedUnitPrice;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plusSeconds(validForSeconds);
        this.used = false;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public void markUsed() {
        this.used = true;
    }

    public Long getId() { return id; }
    public Long getOfferId() { return offerId; }
    public String getRetailer() { return retailer; }
    public String getProductName() { return productName; }
    public int getRequestedQuantity() { return requestedQuantity; }
    public double getNegotiatedUnitPrice() { return negotiatedUnitPrice; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isUsed() { return used; }
}
