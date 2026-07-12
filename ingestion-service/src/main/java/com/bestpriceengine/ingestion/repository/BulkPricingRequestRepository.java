package com.bestpriceengine.ingestion.repository;

import com.bestpriceengine.ingestion.domain.BulkPricingRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BulkPricingRequestRepository extends JpaRepository<BulkPricingRequest, Long> {
}
