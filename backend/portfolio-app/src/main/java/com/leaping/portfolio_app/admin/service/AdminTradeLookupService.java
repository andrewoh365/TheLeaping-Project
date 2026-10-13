package com.leaping.portfolio_app.admin.service;

import com.leaping.portfolio_app.admin.dto.AdminOrderDetailResponse;
import com.leaping.portfolio_app.admin.dto.AdminOrderLookupResponse;
import com.leaping.portfolio_app.auth.model.User;
import com.leaping.portfolio_app.trade.entity.Trade;
import com.leaping.portfolio_app.trade.entity.TradeOrder;
import com.leaping.portfolio_app.trade.repository.TradeOrderRepository;
import com.leaping.portfolio_app.trade.repository.TradeRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AdminTradeLookupService {

    private final TradeOrderRepository tradeOrderRepository;
    private final TradeRepository tradeRepository;

    public AdminTradeLookupService(
            TradeOrderRepository tradeOrderRepository,
            TradeRepository tradeRepository
    ) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeRepository = tradeRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminOrderLookupResponse> getOrders(
            String search,
            String status
    ) {
        String normalizedSearch = normalize(search);
        String normalizedStatus = normalize(status);

        return tradeOrderRepository.findAll()
                .stream()
                .map(this::toLookupResponse)
                .filter(order ->
                        matchesSearch(
                                order,
                                normalizedSearch
                        )
                )
                .filter(order ->
                        matchesStatus(
                                order,
                                normalizedStatus
                        )
                )
                .sorted(
                        Comparator.comparing(
                                AdminOrderLookupResponse::getSubmittedAt,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()
                                )
                        )
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminOrderLookupResponse> getOrdersByClient(
            Long clientId
    ) {

        return tradeOrderRepository
                .findByPortfolio_Customer_UserId(
                        clientId,
                        Pageable.unpaged()
                )
                .getContent()
                .stream()
                .map(this::toLookupResponse)
                .sorted(
                        Comparator.comparing(
                                AdminOrderLookupResponse::getSubmittedAt,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()
                                )
                        )
                )
                .toList();
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

    private boolean matchesSearch(
            AdminOrderLookupResponse order,
            String search
    ) {
        if (search.isEmpty()) {
            return true;
        }

        String orderId =
                order.getOrderId() == null
                        ? ""
                        : order.getOrderId().toString();

        String tradeId =
                order.getTradeId() == null
                        ? ""
                        : order.getTradeId().toString();

        String clientId =
                order.getClientId() == null
                        ? ""
                        : order.getClientId().toString();

        String firstName =
                safeLower(order.getFirstName());

        String lastName =
                safeLower(order.getLastName());

        String fullName =
                (firstName + " " + lastName).trim();

        String symbol =
                safeLower(order.getSymbol());

        String instrumentName =
                safeLower(order.getInstrumentName());

        return orderId.contains(search)
                || tradeId.contains(search)
                || clientId.contains(search)
                || firstName.contains(search)
                || lastName.contains(search)
                || fullName.contains(search)
                || symbol.contains(search)
                || instrumentName.contains(search);
    }

    private boolean matchesStatus(
            AdminOrderLookupResponse order,
            String status
    ) {
        if (status.isEmpty()) {
            return true;
        }

        return order.getStatus() != null
                && order.getStatus()
                        .equalsIgnoreCase(status);
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

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim()
                        .toLowerCase(Locale.ROOT);
    }

    private String safeLower(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT);
    }
}