package com.bestpriceengine.ingestion.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

// Explicit table name: "order" is a reserved SQL keyword (ORDER BY) and
// breaks every generated INSERT/SELECT if left as Hibernate's default.
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long offerId;
    private String retailer;
    private String productName;
    private int requestedQuantity;
    private int filledQuantity;
    private int canceledQuantity;
    private long quotedPriceVersion;
    private long actualPriceVersionAtDecision;
    private double unitPrice;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String rejectReason;
    private Instant createdAt;

    // Denormalized copy, same pattern as retailer/productName above -- not a JPA
    // relationship to User, consistent with how this entity already avoids joins.
    private Long userId;
    private String username;

    protected Order() {
    }

    private Order(Offer offer, int requestedQuantity, long quotedPriceVersion, int filledQuantity,
                   double unitPrice, OrderStatus status, String rejectReason, Long userId, String username) {
        this.offerId = offer.getId();
        this.retailer = offer.getRetailer();
        this.productName = offer.getProductName();
        this.requestedQuantity = requestedQuantity;
        this.filledQuantity = filledQuantity;
        this.canceledQuantity = requestedQuantity - filledQuantity;
        this.quotedPriceVersion = quotedPriceVersion;
        this.actualPriceVersionAtDecision = offer.getPriceVersion();
        this.unitPrice = unitPrice;
        this.status = status;
        this.rejectReason = rejectReason;
        this.createdAt = Instant.now();
        this.userId = userId;
        this.username = username;
    }

    public static Order rejectedForStalePrice(Offer offer, int requestedQuantity, long quotedPriceVersion, Long userId, String username) {
        String reason = "price changed since quote (quoted v%d, current v%d)"
                .formatted(quotedPriceVersion, offer.getPriceVersion());
        return new Order(offer, requestedQuantity, quotedPriceVersion, 0, offer.getBasePrice(), OrderStatus.REJECTED, reason, userId, username);
    }

    public static Order rejectedForInsufficientStock(Offer offer, int requestedQuantity, long quotedPriceVersion, Long userId, String username) {
        String reason = "only %d available, exact-quantity order requires %d"
                .formatted(offer.getAvailableQuantity(), requestedQuantity);
        return new Order(offer, requestedQuantity, quotedPriceVersion, 0, offer.getBasePrice(), OrderStatus.REJECTED, reason, userId, username);
    }

    public static Order rejectedForInvalidQuote(Offer offer, int requestedQuantity, String reason, Long userId, String username) {
        return new Order(offer, requestedQuantity, 0, 0, offer.getBasePrice(), OrderStatus.REJECTED, reason, userId, username);
    }

    public static Order filled(Offer offer, int requestedQuantity, long quotedPriceVersion, int filledQuantity, Long userId, String username) {
        double unitPrice = offer.unitPriceAt(filledQuantity > 0 ? filledQuantity : requestedQuantity);
        return filledAt(offer, requestedQuantity, quotedPriceVersion, filledQuantity, unitPrice, userId, username);
    }

    /** Same fulfillment logic as {@link #filled}, but at an explicitly agreed price (e.g. a negotiated bulk pricing request). */
    public static Order filledAt(Offer offer, int requestedQuantity, long quotedPriceVersion, int filledQuantity, double unitPrice, Long userId, String username) {
        OrderStatus status;
        String reason;
        if (filledQuantity == requestedQuantity) {
            status = OrderStatus.ACCEPTED;
            reason = null;
        } else if (filledQuantity == 0) {
            status = OrderStatus.REJECTED;
            reason = "no stock available";
        } else {
            status = OrderStatus.PARTIALLY_FILLED;
            reason = "only %d of %d requested were available; remainder canceled".formatted(filledQuantity, requestedQuantity);
        }
        return new Order(offer, requestedQuantity, quotedPriceVersion, filledQuantity, unitPrice, status, reason, userId, username);
    }

    public Long getId() { return id; }
    public Long getOfferId() { return offerId; }
    public String getRetailer() { return retailer; }
    public String getProductName() { return productName; }
    public int getRequestedQuantity() { return requestedQuantity; }
    public int getFilledQuantity() { return filledQuantity; }
    public int getCanceledQuantity() { return canceledQuantity; }
    public long getQuotedPriceVersion() { return quotedPriceVersion; }
    public long getActualPriceVersionAtDecision() { return actualPriceVersionAtDecision; }
    public double getUnitPrice() { return unitPrice; }
    public OrderStatus getStatus() { return status; }
    public String getRejectReason() { return rejectReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
}
