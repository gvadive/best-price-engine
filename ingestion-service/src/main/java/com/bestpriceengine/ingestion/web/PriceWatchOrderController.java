package com.bestpriceengine.ingestion.web;

import com.bestpriceengine.ingestion.repository.PriceWatchOrderRepository;
import com.bestpriceengine.ingestion.service.PriceWatchOrderService;
import com.bestpriceengine.ingestion.web.dto.PriceWatchOrderDto;
import com.bestpriceengine.ingestion.web.dto.PriceWatchOrderRequest;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PriceWatchOrderController {

    private final PriceWatchOrderService priceWatchOrderService;
    private final PriceWatchOrderRepository priceWatchOrderRepository;

    public PriceWatchOrderController(PriceWatchOrderService priceWatchOrderService,
                                      PriceWatchOrderRepository priceWatchOrderRepository) {
        this.priceWatchOrderService = priceWatchOrderService;
        this.priceWatchOrderRepository = priceWatchOrderRepository;
    }

    @PostMapping("/api/price-watch-orders")
    public PriceWatchOrderDto placeWatch(@RequestBody PriceWatchOrderRequest request) {
        var watch = priceWatchOrderService.placeWatch(
                request.offerId(), request.quantity(), request.maxAcceptablePrice(),
                request.fulfillmentMode(), request.expiresAfterSeconds());
        return PriceWatchOrderDto.from(watch);
    }

    @GetMapping("/api/price-watch-orders/{id}")
    public PriceWatchOrderDto getWatch(@PathVariable Long id) {
        var watch = priceWatchOrderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("no price watch order with id " + id));
        return PriceWatchOrderDto.from(watch);
    }
}
