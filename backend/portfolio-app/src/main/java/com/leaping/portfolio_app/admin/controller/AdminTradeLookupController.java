package com.leaping.portfolio_app.admin.controller;

import com.leaping.portfolio_app.admin.dto.AdminOrderLookupResponse;
import com.leaping.portfolio_app.admin.service.AdminTradeLookupService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public ResponseEntity<List<AdminOrderLookupResponse>> getOrders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status
    ) {

        List<AdminOrderLookupResponse> response =
                adminTradeLookupService.getOrders(
                        search,
                        status
                );

        return ResponseEntity.ok(response);
    }
}