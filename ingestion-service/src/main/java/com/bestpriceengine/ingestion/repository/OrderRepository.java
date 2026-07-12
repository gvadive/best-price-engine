package com.bestpriceengine.ingestion.repository;

import com.bestpriceengine.ingestion.domain.Order;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUsernameOrderByCreatedAtDesc(String username);
}
