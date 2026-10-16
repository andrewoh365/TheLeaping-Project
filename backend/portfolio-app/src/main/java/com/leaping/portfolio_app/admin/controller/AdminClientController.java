package com.leaping.portfolio_app.admin.controller;

import com.leaping.portfolio_app.admin.dto.AdminClientDetailResponse;
import com.leaping.portfolio_app.admin.dto.AdminClientPageResponse;
import com.leaping.portfolio_app.admin.dto.AdminHoldingPageResponse;
import com.leaping.portfolio_app.admin.dto.AdminOrderPageResponse;
import com.leaping.portfolio_app.admin.service.AdminClientService;
import com.leaping.portfolio_app.admin.service.AdminTradeLookupService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

        // GET A PAGE OF CLIENTS
    // Optional filters:
    // /api/admin/clients?search=ava
    // /api/admin/clients?status=ACTIVE

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminClientPageResponse> getClients(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {

        AdminClientPageResponse response =
                adminClientService.getClients(
                        search,
                        status,
                        page,
                        size
                );

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
    public ResponseEntity<AdminHoldingPageResponse> getClientHoldings(
            @PathVariable Long clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        AdminHoldingPageResponse response =
                adminClientService.getClientHoldings(
                        clientId,
                        page,
                        size
                );

        return ResponseEntity.ok(response);
    }

        @GetMapping("/{clientId}/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminOrderPageResponse> getClientOrders(
            @PathVariable Long clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        AdminOrderPageResponse response =
                adminTradeLookupService.getOrdersByClient(
                        clientId,
                        page,
                        size
                );

        return ResponseEntity.ok(response);
    }
}