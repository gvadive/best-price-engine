package com.bestpriceengine.ingestion.repository;

import com.bestpriceengine.ingestion.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
