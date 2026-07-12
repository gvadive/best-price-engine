package com.bestpriceengine.ingestion.web;

import com.bestpriceengine.ingestion.domain.Order;
import com.bestpriceengine.ingestion.service.OrderService;
import com.bestpriceengine.ingestion.web.dto.OrderRequest;
import com.bestpriceengine.ingestion.web.dto.OrderResultDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/orders")
    public OrderResultDto placeOrder(@RequestBody OrderRequest request) {
        Order order = orderService.placeOrder(request);
        return OrderResultDto.from(order);
    }
}
