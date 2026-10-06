package com.leaping.portfolio_app.trade.repository;

import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.trade.entity.TradeOrder;
import com.leaping.portfolio_app.trade.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {
    List<TradeOrder> findByPortfolioAndOrderStatus(Portfolio portfolio, OrderStatus orderStatus);
    List<TradeOrder> findByPortfolio(Portfolio portfolio);
    Optional<TradeOrder> findByOrderId(Long orderId);
}
