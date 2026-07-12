package com.bestpriceengine.ingestion.service;

import com.bestpriceengine.ingestion.domain.FulfillmentMode;
import com.bestpriceengine.ingestion.domain.Offer;
import com.bestpriceengine.ingestion.domain.Order;
import com.bestpriceengine.ingestion.domain.PriceWatchOrder;
import com.bestpriceengine.ingestion.domain.PriceWatchOrderStatus;
import com.bestpriceengine.ingestion.repository.OfferRepository;
import com.bestpriceengine.ingestion.repository.PriceWatchOrderRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceWatchOrderService {

    private final PriceWatchOrderRepository priceWatchOrderRepository;
    private final OfferRepository offerRepository;
    private final OrderService orderService;

    public PriceWatchOrderService(PriceWatchOrderRepository priceWatchOrderRepository, OfferRepository offerRepository,
                                   OrderService orderService) {
        this.priceWatchOrderRepository = priceWatchOrderRepository;
        this.offerRepository = offerRepository;
        this.orderService = orderService;
    }

    @Transactional
    public PriceWatchOrder placeWatch(Long offerId, int quantity, double maxAcceptablePrice,
                                       FulfillmentMode fulfillmentMode, int expiresAfterSeconds, Long userId, String username) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new NoSuchElementException("no offer with id " + offerId));

        PriceWatchOrder watch = new PriceWatchOrder(offer, quantity, maxAcceptablePrice, fulfillmentMode, expiresAfterSeconds, userId, username);
        priceWatchOrderRepository.save(watch);

        tryFill(watch, offer);
        return watch;
    }

    /** Called right after a price update -- checks whether it just satisfied any pending watches on that offer. */
    @Transactional
    public void onPriceUpdated(Offer offer) {
        List<PriceWatchOrder> pending = priceWatchOrderRepository.findByOfferIdAndStatus(offer.getId(), PriceWatchOrderStatus.PENDING);
        for (PriceWatchOrder watch : pending) {
            tryFill(watch, offer);
        }
    }

    private void tryFill(PriceWatchOrder watch, Offer offer) {
        if (watch.getStatus() != PriceWatchOrderStatus.PENDING) {
            return;
        }
        double currentUnitPrice = offer.unitPriceAt(watch.getQuantity());
        if (watch.qualifiesAt(currentUnitPrice)) {
            Order order = orderService.fulfillAtLivePrice(offer, watch.getQuantity(), watch.getFulfillmentMode(),
                    watch.getUserId(), watch.getUsername());
            if (order.getFilledQuantity() > 0) {
                watch.markFilled(order.getId());
                priceWatchOrderRepository.save(watch);
            }
        }
    }

    /** Expiry enforcement: every 5s, cancel any watch past its expiry. Real-world cadence would be much
     *  coarser (minutes); tight here so expiry is actually observable within a demo/test run. */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void sweepExpired() {
        List<PriceWatchOrder> pending = priceWatchOrderRepository.findByStatus(PriceWatchOrderStatus.PENDING);
        for (PriceWatchOrder watch : pending) {
            if (watch.isExpired()) {
                watch.markExpired();
                priceWatchOrderRepository.save(watch);
            }
        }
    }
}
