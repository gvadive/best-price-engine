package com.bestpriceengine.ingestion.service;

import com.bestpriceengine.ingestion.domain.BulkPricingRequest;
import com.bestpriceengine.ingestion.domain.FulfillmentMode;
import com.bestpriceengine.ingestion.domain.Offer;
import com.bestpriceengine.ingestion.domain.Order;
import com.bestpriceengine.ingestion.repository.BulkPricingRequestRepository;
import com.bestpriceengine.ingestion.repository.OfferRepository;
import com.bestpriceengine.ingestion.repository.OrderRepository;
import com.bestpriceengine.ingestion.web.dto.OrderRequest;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OfferRepository offerRepository;
    private final OrderRepository orderRepository;
    private final BulkPricingRequestRepository bulkPricingRequestRepository;

    public OrderService(OfferRepository offerRepository, OrderRepository orderRepository,
                         BulkPricingRequestRepository bulkPricingRequestRepository) {
        this.offerRepository = offerRepository;
        this.orderRepository = orderRepository;
        this.bulkPricingRequestRepository = bulkPricingRequestRepository;
    }

    @Transactional
    public Order placeOrder(OrderRequest request, Long userId, String username) {
        Offer offer = offerRepository.findById(request.offerId())
                .orElseThrow(() -> new NoSuchElementException("no offer with id " + request.offerId()));

        return request.bulkPricingRequestId() != null
                ? placeOrderAgainstBulkPricing(offer, request, userId, username)
                : placeOrderAgainstLivePrice(offer, request, userId, username);
    }

    /** Returns the given user's order history, most recent first. */
    public List<Order> historyForUser(String username) {
        return orderRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    /** Used by PriceWatchOrderService once a watch's price condition is satisfied -- fills at the offer's current price. */
    @Transactional
    public Order fulfillAtLivePrice(Offer offer, int quantity, FulfillmentMode mode, Long userId, String username) {
        return fulfill(offer, quantity, offer.getPriceVersion(), mode, offer.unitPriceAt(quantity), userId, username);
    }

    /** Standard path: caller quotes the offer's live priceVersion; a mismatch means the price moved. */
    private Order placeOrderAgainstLivePrice(Offer offer, OrderRequest request, Long userId, String username) {
        if (offer.getPriceVersion() != request.quotedPriceVersion()) {
            return orderRepository.save(
                    Order.rejectedForStalePrice(offer, request.quantity(), request.quotedPriceVersion(), userId, username));
        }
        return fulfill(offer, request.quantity(), request.quotedPriceVersion(),
                request.fulfillmentMode(), offer.unitPriceAt(request.quantity()), userId, username);
    }

    /** Bulk-pricing path: caller quotes a specific negotiated BulkPricingRequest instead of the live price. */
    private Order placeOrderAgainstBulkPricing(Offer offer, OrderRequest request, Long userId, String username) {
        BulkPricingRequest quote = bulkPricingRequestRepository.findById(request.bulkPricingRequestId())
                .orElseThrow(() -> new NoSuchElementException("no bulk pricing request with id " + request.bulkPricingRequestId()));

        String invalidReason = validateBulkPricing(quote, offer, request);
        if (invalidReason != null) {
            return orderRepository.save(Order.rejectedForInvalidQuote(offer, request.quantity(), invalidReason, userId, username));
        }

        Order order = fulfill(offer, request.quantity(), 0, request.fulfillmentMode(), quote.getNegotiatedUnitPrice(), userId, username);

        // Only spend the bulk pricing request once we actually attempted a fill
        // against it -- a request rejected purely for insufficient stock leaves
        // it retryable.
        if (order.getStatus() != com.bestpriceengine.ingestion.domain.OrderStatus.REJECTED
                || order.getFilledQuantity() > 0) {
            quote.markUsed();
            bulkPricingRequestRepository.save(quote);
        }
        return order;
    }

    private String validateBulkPricing(BulkPricingRequest quote, Offer offer, OrderRequest request) {
        if (!quote.getOfferId().equals(offer.getId())) {
            return "bulk pricing request does not belong to this offer";
        }
        if (quote.isUsed()) {
            return "bulk pricing request has already been used";
        }
        if (quote.isExpired()) {
            return "bulk pricing request expired at " + quote.getExpiresAt();
        }
        if (request.quantity() > quote.getRequestedQuantity()) {
            return "order quantity (%d) exceeds the requested bulk quantity (%d)"
                    .formatted(request.quantity(), quote.getRequestedQuantity());
        }
        return null;
    }

    /**
     * Shared stock-fulfillment logic for both the standard and bulk-pricing paths.
     * EXACT_QUANTITY rejects outright if the full amount isn't available;
     * BEST_EFFORT fills whatever's available and cancels the remainder.
     *
     * The read-compare-decide happens inside one transaction, which is correct
     * for this demo's concurrency level. Under heavier concurrent write load a
     * pessimistic lock (SELECT ... FOR UPDATE) would close the narrow window
     * between the read and the decision; not needed here but worth naming.
     */
    private Order fulfill(Offer offer, int quantity, long quotedPriceVersion, FulfillmentMode mode, double unitPrice, Long userId, String username) {
        Order order;
        if (mode == FulfillmentMode.EXACT_QUANTITY) {
            if (offer.getAvailableQuantity() >= quantity) {
                offer.reduceStock(quantity);
                order = Order.filledAt(offer, quantity, quotedPriceVersion, quantity, unitPrice, userId, username);
            } else {
                order = Order.rejectedForInsufficientStock(offer, quantity, quotedPriceVersion, userId, username);
            }
        } else {
            int filled = offer.reduceStock(quantity);
            order = Order.filledAt(offer, quantity, quotedPriceVersion, filled, unitPrice, userId, username);
        }
        offerRepository.save(offer);
        return orderRepository.save(order);
    }
}
