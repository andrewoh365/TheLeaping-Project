package com.leaping.portfolio_app.admin.dto;

import com.leaping.portfolio_app.holdings.HoldingResponse;

import java.util.List;

public class AdminHoldingPageResponse {

    private final List<HoldingResponse> holdings;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean first;
    private final boolean last;

    public AdminHoldingPageResponse(
            List<HoldingResponse> holdings,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {
        this.holdings = holdings;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.first = first;
        this.last = last;
    }

    public List<HoldingResponse> getHoldings() {
        return holdings;
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

    public int getTotalPages() {
        return totalPages;
    }

    public boolean isFirst() {
        return first;
    }

    public boolean isLast() {
        return last;
    }
}