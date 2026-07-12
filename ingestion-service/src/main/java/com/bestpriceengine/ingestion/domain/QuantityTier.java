package com.bestpriceengine.ingestion.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class QuantityTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Offer offer;

    private int minQuantity;
    private double unitPrice;

    protected QuantityTier() {
    }

    public QuantityTier(Offer offer, int minQuantity, double unitPrice) {
        this.offer = offer;
        this.minQuantity = minQuantity;
        this.unitPrice = unitPrice;
    }

    public int getMinQuantity() { return minQuantity; }
    public double getUnitPrice() { return unitPrice; }
}
