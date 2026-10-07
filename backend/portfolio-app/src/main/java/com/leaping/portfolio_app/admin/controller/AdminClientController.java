package com.leaping.portfolio_app.admin.controller;

import com.leaping.portfolio_app.admin.dto.AdminClientDetailResponse;
import com.leaping.portfolio_app.admin.dto.AdminClientSummaryResponse;
import com.leaping.portfolio_app.admin.service.AdminClientService;

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

    public AdminClientController(
            AdminClientService adminClientService
    ) {
        this.adminClientService = adminClientService;
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
}