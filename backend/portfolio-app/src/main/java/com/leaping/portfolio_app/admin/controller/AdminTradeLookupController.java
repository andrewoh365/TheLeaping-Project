package com.leaping.portfolio_app.admin.controller;

import com.leaping.portfolio_app.admin.dto.AdminOrderDetailResponse;
import com.leaping.portfolio_app.admin.dto.AdminOrderPageResponse;
import com.leaping.portfolio_app.admin.service.AdminTradeLookupService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminTradeLookupController {

    private final AdminTradeLookupService adminTradeLookupService;

    public AdminTradeLookupController(
            AdminTradeLookupService adminTradeLookupService
    ) {
        this.adminTradeLookupService = adminTradeLookupService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminOrderPageResponse> getOrders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {

        AdminOrderPageResponse response =
                adminTradeLookupService.getOrders(
                        search,
                        status,
                        page,
                        size
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminOrderDetailResponse> getOrder(
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(
                adminTradeLookupService.getOrder(orderId)
        );
    }
}