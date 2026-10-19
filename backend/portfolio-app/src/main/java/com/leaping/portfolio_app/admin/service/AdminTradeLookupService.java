package com.leaping.portfolio_app.admin.service;

import com.leaping.portfolio_app.admin.dto.AdminOrderDetailResponse;
import com.leaping.portfolio_app.admin.dto.AdminOrderLookupResponse;
import com.leaping.portfolio_app.admin.dto.AdminOrderPageResponse;
import com.leaping.portfolio_app.admin.dto.AuditEventResponse;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.audit.entity.AuditEvent;
import com.leaping.portfolio_app.audit.repository.AuditEventRepository;
import com.leaping.portfolio_app.trade.entity.Trade;
import com.leaping.portfolio_app.trade.entity.TradeOrder;
import com.leaping.portfolio_app.trade.repository.TradeOrderRepository;
import com.leaping.portfolio_app.trade.repository.TradeRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class AdminTradeLookupService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TradeOrderRepository tradeOrderRepository;
    private final TradeRepository tradeRepository;
    private final AuditEventRepository auditEventRepository;

    public AdminTradeLookupService(
            TradeOrderRepository tradeOrderRepository,
            TradeRepository tradeRepository,
            AuditEventRepository auditEventRepository
    ) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeRepository = tradeRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    public AdminOrderPageResponse getOrders(
            String search,
            String status,
            int page,
            int size
    ) {
        String normalizedSearch = normalize(search);
        String normalizedStatus = normalize(status);
        Page<TradeOrder> orderPage = tradeOrderRepository.findAdminPage(
                normalizedSearch,
                normalizedStatus,
                pageRequest(page, size)
        );
        return toPageResponse(orderPage);
    }

    @Transactional(readOnly = true)
    public AdminOrderPageResponse getOrdersByClient(
            Long clientId,
            int page,
            int size
    ) {
        Page<TradeOrder> orderPage = tradeOrderRepository
                .findByPortfolio_Customer_UserIdOrderBySubmittedAtDescOrderIdDesc(
                        clientId,
                        pageRequest(page, size)
                );
        return toPageResponse(orderPage);
    }

    private AdminOrderPageResponse toPageResponse(
            Page<TradeOrder> orderPage
    ) {
        List<AdminOrderLookupResponse> orders = orderPage
                .map(this::toLookupResponse)
                .getContent();
        return new AdminOrderPageResponse(
                orders,
                orderPage.getNumber(),
                orderPage.getSize(),
                orderPage.getTotalElements(),
                orderPage.getTotalPages(),
                orderPage.isFirst(),
                orderPage.isLast()
        );
    }

    private Pageable pageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(
                Math.max(size, 1),
                MAX_PAGE_SIZE
        );
        return PageRequest.of(safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public AdminOrderDetailResponse getOrder(Long orderId) {
        TradeOrder order = tradeOrderRepository
                .findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found"
                ));
        Trade trade = tradeRepository
                .findByOrderOrderId(orderId)
                .orElse(null);
        User user = null;
        Long clientId = null;
        Long portfolioId = null;
        if (order.getPortfolio() != null) {
            portfolioId = order.getPortfolio().getPortfolioId();
            if (order.getPortfolio().getCustomer() != null) {
                clientId = order.getPortfolio()
                        .getCustomer()
                        .getUserId();
                user = order.getPortfolio()
                        .getCustomer()
                        .getUser();
            }
        }
        return new AdminOrderDetailResponse(
                order.getOrderId(),
                order.getOrderStatus() != null
                        ? order.getOrderStatus().name()
                        : null,
                order.getOrderAction() != null
                        ? order.getOrderAction().name()
                        : null,
                order.getOrderType() != null
                        ? order.getOrderType().name()
                        : null,
                order.getTimeInForce() != null
                        ? order.getTimeInForce().name()
                        : null,
                order.getQuantity(),
                order.getLimitPriceUsd(),
                clientId,
                user != null ? user.getFirstName() : null,
                user != null ? user.getLastName() : null,
                portfolioId,
                order.getInstrument() != null
                        ? order.getInstrument().getInstrumentId()
                        : null,
                order.getInstrument() != null
                        ? order.getInstrument().getSymbol()
                        : null,
                order.getInstrument() != null
                        ? order.getInstrument().getName()
                        : null,
                order.getSubmittedAt(),
                order.getAcceptedAt(),
                order.getRejectedAt(),
                order.getRejectionReason(),
                order.getCancelledAt(),
                order.getFilledAt(),
                trade != null ? trade.getTradeId() : null,
                trade != null ? trade.getExecutionPrice() : null,
                trade != null && trade.getExecutionPriceCurrency() != null
                        ? trade.getExecutionPriceCurrency().getCurrencyCode()
                        : null,
                trade != null
                        ? trade.getExchangeRateToUsdAtExecution()
                        : null,
                trade != null ? trade.getExecutionPriceUsd() : null,
                trade != null ? trade.getTotalUsdValue() : null,
                trade != null ? trade.getExecutedAt() : null
        );
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> getOrderAudit(Long orderId) {
        tradeOrderRepository
                .findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found"
                ));
        return auditEventRepository
                .findByOrder_OrderIdOrderByOccurredAtAscAuditEventIdAsc(
                        orderId,
                        Pageable.unpaged()
                )
                .map(this::toAuditEventResponse)
                .getContent();
    }

    private AdminOrderLookupResponse toLookupResponse(
            TradeOrder order
    ) {
        Trade trade = tradeRepository
                .findByOrderOrderId(order.getOrderId())
                .orElse(null);
        User user = null;
        Long clientId = null;
        if (order.getPortfolio() != null
                && order.getPortfolio().getCustomer() != null) {
            clientId =
                    order.getPortfolio()
                            .getCustomer()
                            .getUserId();
            user =
                    order.getPortfolio()
                            .getCustomer()
                            .getUser();
        }
        return new AdminOrderLookupResponse(
                order.getOrderId(),
                trade != null
                        ? trade.getTradeId()
                        : null,
                clientId,
                user != null
                        ? user.getFirstName()
                        : null,
                user != null
                        ? user.getLastName()
                        : null,
                order.getInstrument() != null
                        ? order.getInstrument()
                                .getInstrumentId()
                        : null,
                order.getInstrument() != null
                        ? order.getInstrument()
                                .getSymbol()
                        : null,
                order.getInstrument() != null
                        ? order.getInstrument()
                                .getName()
                        : null,
                order.getOrderAction() != null
                        ? order.getOrderAction().name()
                        : null,
                order.getQuantity(),
                order.getOrderStatus() != null
                        ? order.getOrderStatus().name()
                        : null,
                order.getSubmittedAt(),
                trade != null
                        ? trade.getExecutedAt()
                        : null
        );
    }

    private AuditEventResponse toAuditEventResponse(AuditEvent event) {
        return new AuditEventResponse(
                event.getAuditEventId(),
                event.getEventType(),
                event.getOccurredAt(),
                event.getUser() != null ? event.getUser().getId() : null,
                event.getOrder() != null ? event.getOrder().getOrderId() : null,
                event.getExecutionAttempt() != null
                        ? event.getExecutionAttempt().getExecutionAttemptId()
                        : null,
                event.getTrade() != null ? event.getTrade().getTradeId() : null,
                event.getCashTransaction() != null
                        ? event.getCashTransaction().getCashTransactionId()
                        : null,
                event.getEventDetails()
        );
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim()
                        .toLowerCase(Locale.ROOT);
    }
}
