package com.bestpriceengine.ingestion.repository;

import com.bestpriceengine.ingestion.domain.PriceWatchOrder;
import com.bestpriceengine.ingestion.domain.PriceWatchOrderStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceWatchOrderRepository extends JpaRepository<PriceWatchOrder, Long> {

    List<PriceWatchOrder> findByOfferIdAndStatus(Long offerId, PriceWatchOrderStatus status);

    List<PriceWatchOrder> findByStatus(PriceWatchOrderStatus status);
}
