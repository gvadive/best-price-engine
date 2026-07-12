package com.bestpriceengine.ingestion.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Deliberately NOT @Version: JPA's automatic optimistic-lock column bumps
    // on ANY field change, which would conflate a stock decrement (a fill)
    // with an actual price change. This is manually incremented, only in
    // updatePrice() -- so it means exactly "the price changed", nothing else.
    private long priceVersion = 0;

    private String productName;
    private String retailer;
    private double basePrice;
    private int deliveryDays;
    private double rating;
    private boolean inStock;
    private int availableQuantity;

    @OneToMany(mappedBy = "offer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuantityTier> quantityTiers = new ArrayList<>();

    protected Offer() {
        // required by JPA -- it builds entities via reflection, not this constructor
    }

    public Offer(String productName, String retailer, double basePrice,
                 int deliveryDays, double rating, boolean inStock, int availableQuantity) {
        this.productName = productName;
        this.retailer = retailer;
        this.basePrice = basePrice;
        this.deliveryDays = deliveryDays;
        this.rating = rating;
        this.inStock = inStock;
        this.availableQuantity = availableQuantity;
    }

    public void addTier(int minQuantity, double unitPrice) {
        QuantityTier tier = new QuantityTier(this, minQuantity, unitPrice);
        this.quantityTiers.add(tier);
    }

    public void updatePrice(double newBasePrice) {
        this.basePrice = newBasePrice;
        this.priceVersion++;
    }

    /** Reduces stock by up to {@code requested}, returns how many units were actually taken. */
    public int reduceStock(int requested) {
        int taken = Math.min(requested, availableQuantity);
        availableQuantity -= taken;
        return taken;
    }

    public double unitPriceAt(int quantity) {
        return quantityTiers.stream()
                .filter(t -> quantity >= t.getMinQuantity())
                .mapToDouble(QuantityTier::getUnitPrice)
                .min()
                .orElse(basePrice);
    }

    public Long getId() { return id; }
    public long getPriceVersion() { return priceVersion; }
    public String getProductName() { return productName; }
    public String getRetailer() { return retailer; }
    public double getBasePrice() { return basePrice; }
    public int getDeliveryDays() { return deliveryDays; }
    public double getRating() { return rating; }
    public boolean isInStock() { return inStock; }
    public int getAvailableQuantity() { return availableQuantity; }
    public List<QuantityTier> getQuantityTiers() { return quantityTiers; }
}
