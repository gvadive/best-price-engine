package com.bestpriceengine.ingestion.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * A conditional order: fill at {@code maxAcceptablePrice} or better, staying
 * pending until either the price qualifies or {@code expiresAt} passes, at
 * which point it's canceled automatically.
 */
@Entity
public class PriceWatchOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long offerId;
    private String retailer;
    private String productName;
    private int quantity;
    private double maxAcceptablePrice;

    @Enumerated(EnumType.STRING)
    private FulfillmentMode fulfillmentMode;

    @Enumerated(EnumType.STRING)
    private PriceWatchOrderStatus status;

    private Long resultingOrderId;
    private Instant createdAt;
    private Instant expiresAt;

    // Denormalized copy (same pattern as Order.userId/username) -- the watch's CREATOR, so a
    // later price update that triggers the fill still attributes correctly to the person who
    // placed the watch, not whoever's request happened to bump the price.
    private Long userId;
    private String username;

    protected PriceWatchOrder() {
    }

    public PriceWatchOrder(Offer offer, int quantity, double maxAcceptablePrice,
                            FulfillmentMode fulfillmentMode, int expiresAfterSeconds, Long userId, String username) {
        this.offerId = offer.getId();
        this.retailer = offer.getRetailer();
        this.productName = offer.getProductName();
        this.quantity = quantity;
        this.maxAcceptablePrice = maxAcceptablePrice;
        this.fulfillmentMode = fulfillmentMode;
        this.status = PriceWatchOrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plusSeconds(expiresAfterSeconds);
        this.userId = userId;
        this.username = username;
    }

    public boolean qualifiesAt(double currentUnitPrice) {
        return currentUnitPrice <= maxAcceptablePrice;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public void markFilled(Long orderId) {
        this.status = PriceWatchOrderStatus.FILLED;
        this.resultingOrderId = orderId;
    }

    public void markExpired() {
        this.status = PriceWatchOrderStatus.EXPIRED;
    }

    public Long getId() { return id; }
    public Long getOfferId() { return offerId; }
    public String getRetailer() { return retailer; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public double getMaxAcceptablePrice() { return maxAcceptablePrice; }
    public FulfillmentMode getFulfillmentMode() { return fulfillmentMode; }
    public PriceWatchOrderStatus getStatus() { return status; }
    public Long getResultingOrderId() { return resultingOrderId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
}
