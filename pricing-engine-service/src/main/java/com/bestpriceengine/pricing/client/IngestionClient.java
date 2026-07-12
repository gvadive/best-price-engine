package com.bestpriceengine.pricing.client;

import com.bestpriceengine.pricing.domain.OfferView;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IngestionClient {

    private final RestClient restClient;
    private final ConcurrentHashMap<String, List<OfferView>> lastKnownGood = new ConcurrentHashMap<>();

    public IngestionClient(@Value("${ingestion.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    @CircuitBreaker(name = "ingestionService", fallbackMethod = "fallbackToCache")
    @Retry(name = "ingestionService")
    public List<OfferView> offersFor(String productName) {
        List<OfferView> offers = restClient.get()
                .uri("/api/offers?product={product}", productName)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<OfferView>>() {});

        lastKnownGood.put(productName, offers);
        return offers;
    }

    // Resilience4j calls this automatically when the circuit is open, or every
    // retry attempt is exhausted -- same method signature plus the Throwable.
    private List<OfferView> fallbackToCache(String productName, Throwable ex) {
        return lastKnownGood.getOrDefault(productName, List.of());
    }
}
