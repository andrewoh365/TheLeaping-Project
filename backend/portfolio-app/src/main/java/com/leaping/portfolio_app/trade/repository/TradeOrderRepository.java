package com.leaping.portfolio_app.trade.repository;

import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.trade.entity.TradeOrder;
import com.leaping.portfolio_app.trade.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {
    List<TradeOrder> findByPortfolioAndOrderStatus(Portfolio portfolio, OrderStatus orderStatus);
    List<TradeOrder> findByPortfolio(Portfolio portfolio);
    Optional<TradeOrder> findByOrderId(Long orderId);

    /** All orders belonging to the customer (User ID), paged; scoped by owner so clients only see their own. */
    Page<TradeOrder> findByPortfolio_Customer_UserId(Long userId, Pageable pageable);
}
