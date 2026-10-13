package com.leaping.portfolio_app.trade.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One page of a client's order history (blotter), newest first (BR-11).
 */
public class OrderHistoryResponse {

    @JsonProperty("orders")
    private final List<OrderResponse> orders;

    @JsonProperty("page")
    private final int page;

    @JsonProperty("size")
    private final int size;

    @JsonProperty("total_elements")
    private final long totalElements;

    @JsonProperty("has_more")
    private final boolean hasMore;

    public OrderHistoryResponse(List<OrderResponse> orders, int page, int size, long totalElements, boolean hasMore) {
        this.orders = orders;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.hasMore = hasMore;
    }

    public List<OrderResponse> getOrders() {
        return orders;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public boolean isHasMore() {
        return hasMore;
    }
}
