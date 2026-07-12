package com.bestpriceengine.ingestion.repository;

import com.bestpriceengine.ingestion.domain.Offer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    List<Offer> findByProductName(String productName);
}
