package com.leaping.portfolio_app.admin.controller;

import com.leaping.portfolio_app.admin.dto.AdminClientDetailResponse;
import com.leaping.portfolio_app.admin.dto.AdminOrderLookupResponse;
import com.leaping.portfolio_app.admin.dto.AdminClientSummaryResponse;
import com.leaping.portfolio_app.admin.service.AdminClientService;
import com.leaping.portfolio_app.admin.service.AdminTradeLookupService;
import com.leaping.portfolio_app.holdings.HoldingResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/clients")
public class AdminClientController {

    private final AdminClientService adminClientService;
        private final AdminTradeLookupService adminTradeLookupService;

    public AdminClientController(
                        AdminClientService adminClientService,
                        AdminTradeLookupService adminTradeLookupService
    ) {
        this.adminClientService = adminClientService;
                this.adminTradeLookupService = adminTradeLookupService;
    }

    // GET ALL CLIENTS
    // Optional filters:
    // /api/admin/clients?search=ava
    // /api/admin/clients?status=ACTIVE

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminClientSummaryResponse>> getClients(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status
    ) {

        List<AdminClientSummaryResponse> response =
                adminClientService.getClients(search, status);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{clientId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminClientDetailResponse> getClient(
            @PathVariable Long clientId
    ) {

        AdminClientDetailResponse response =
                adminClientService.getClient(clientId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{clientId}/holdings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<HoldingResponse>> getClientHoldings(
            @PathVariable Long clientId
    ) {
        List<HoldingResponse> response =
                adminClientService.getClientHoldings(clientId);

        return ResponseEntity.ok(response);
    }

        @GetMapping("/{clientId}/orders")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<List<AdminOrderLookupResponse>> getClientOrders(
                        @PathVariable Long clientId
        ) {
                List<AdminOrderLookupResponse> response =
                                adminTradeLookupService.getOrdersByClient(clientId);

                return ResponseEntity.ok(response);
        }
}