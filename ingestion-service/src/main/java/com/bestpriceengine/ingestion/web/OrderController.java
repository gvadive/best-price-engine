package com.bestpriceengine.ingestion.web;

import com.bestpriceengine.ingestion.domain.Order;
import com.bestpriceengine.ingestion.domain.User;
import com.bestpriceengine.ingestion.security.CurrentUserResolver;
import com.bestpriceengine.ingestion.service.OrderService;
import com.bestpriceengine.ingestion.web.dto.OrderRequest;
import com.bestpriceengine.ingestion.web.dto.OrderResultDto;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;
    private final CurrentUserResolver currentUserResolver;

    public OrderController(OrderService orderService, CurrentUserResolver currentUserResolver) {
        this.orderService = orderService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/api/orders")
    public OrderResultDto placeOrder(@RequestBody OrderRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Order order = orderService.placeOrder(request, user.getId(), user.getUsername());
        return OrderResultDto.from(order);
    }

    // Order history for the currently authenticated user only -- never accepts a username
    // param, so one user can't read another's history by guessing it.
    @GetMapping("/api/orders")
    public List<OrderResultDto> orderHistory(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return orderService.historyForUser(user.getUsername()).stream()
                .map(OrderResultDto::from)
                .toList();
    }
}
