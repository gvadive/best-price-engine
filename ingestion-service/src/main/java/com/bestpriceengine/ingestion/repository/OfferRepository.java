package com.bestpriceengine.ingestion.repository;

import com.bestpriceengine.ingestion.domain.Offer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    List<Offer> findByProductName(String productName);

    @Query("select distinct o.productName from Offer o where lower(o.productName) like lower(concat('%', :query, '%')) order by o.productName")
    List<String> findDistinctProductNamesMatching(@Param("query") String query);
}
