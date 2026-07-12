package com.bestpriceengine.ingestion.web.dto;

import com.bestpriceengine.ingestion.domain.Order;

public record OrderResultDto(
        Long id,
        String retailer,
        String productName,
        int requestedQuantity,
        int filledQuantity,
        int canceledQuantity,
        double unitPrice,
        double filledTotal,
        String status,
        String rejectReason,
        String username,
        String createdAt
) {
    public static OrderResultDto from(Order order) {
        return new OrderResultDto(
                order.getId(),
                order.getRetailer(),
                order.getProductName(),
                order.getRequestedQuantity(),
                order.getFilledQuantity(),
                order.getCanceledQuantity(),
                order.getUnitPrice(),
                Math.round(order.getUnitPrice() * order.getFilledQuantity() * 100.0) / 100.0,
                order.getStatus().name(),
                order.getRejectReason(),
                order.getUsername(),
                order.getCreatedAt().toString()
        );
    }
}
