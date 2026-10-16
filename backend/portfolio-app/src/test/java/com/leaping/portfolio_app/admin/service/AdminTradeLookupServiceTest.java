package com.leaping.portfolio_app.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.leaping.portfolio_app.admin.dto.AdminOrderPageResponse;
import com.leaping.portfolio_app.trade.repository.TradeOrderRepository;
import com.leaping.portfolio_app.trade.repository.TradeRepository;

@ExtendWith(MockitoExtension.class)
class AdminTradeLookupServiceTest {

    @Mock
    private TradeOrderRepository tradeOrderRepository;

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private AdminTradeLookupService adminTradeLookupService;

    @Test
    void getOrdersUsesDatabasePageAndReturnsMetadata() {
        when(tradeOrderRepository.findAdminPage(
                eq("ava"),
                eq("filled"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(),
                PageRequest.of(2, 100),
                250
        ));

        AdminOrderPageResponse response =
                adminTradeLookupService.getOrders(
                        " Ava ",
                        " FILLED ",
                        2,
                        500
                );

        ArgumentCaptor<Pageable> pageable =
                ArgumentCaptor.forClass(Pageable.class);
        verify(tradeOrderRepository).findAdminPage(
                eq("ava"),
                eq("filled"),
                pageable.capture()
        );

        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize());
        assertEquals(2, response.getPage());
        assertEquals(100, response.getSize());
        assertEquals(250, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
    }
}