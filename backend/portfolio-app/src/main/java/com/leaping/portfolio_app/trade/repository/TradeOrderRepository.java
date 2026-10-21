package com.leaping.portfolio_app.trade.repository;

import com.leaping.portfolio_app.portfolio.entity.Portfolio;
import com.leaping.portfolio_app.trade.entity.TradeOrder;
import com.leaping.portfolio_app.trade.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {
    List<TradeOrder> findByPortfolioAndOrderStatus(Portfolio portfolio, OrderStatus orderStatus);
    List<TradeOrder> findByPortfolio(Portfolio portfolio);
    Optional<TradeOrder> findByOrderId(Long orderId);

    /** All orders belonging to the customer (User ID), paged; scoped by owner so clients only see their own. */
    Page<TradeOrder> findByPortfolio_Customer_UserId(Long userId, Pageable pageable);

    Page<TradeOrder> findByPortfolio_Customer_UserIdOrderBySubmittedAtDescOrderIdDesc(
            Long userId,
            Pageable pageable
    );

        @Query("""
                        SELECT o.orderId FROM TradeOrder o
                        WHERE o.orderStatus = com.leaping.portfolio_app.trade.enums.OrderStatus.ACCEPTED
                            AND (
                                        o.orderType = com.leaping.portfolio_app.trade.enums.OrderType.LIMIT
                                        OR (
                                                o.orderType = com.leaping.portfolio_app.trade.enums.OrderType.MARKET
                                                AND o.acceptedAt > :acceptedAfter
                                                AND o.acceptedAt < :acceptedBefore
                                        )
                            )
                        ORDER BY o.acceptedAt
                        """)
        List<Long> findOrdersAwaitingExecution(
            @Param("acceptedAfter") OffsetDateTime acceptedAfter,
            @Param("acceptedBefore") OffsetDateTime acceptedBefore
        );

        @Query("""
                        SELECT o.orderId FROM TradeOrder o
                        WHERE o.orderStatus = com.leaping.portfolio_app.trade.enums.OrderStatus.ACCEPTED
                            AND o.timeInForce = com.leaping.portfolio_app.trade.enums.TimeInForce.DAY
                        ORDER BY o.acceptedAt
                        """)
        List<Long> findAcceptedDayOrderIds();

    @Query(value = """
            SELECT o.*
            FROM trade_orders o
            JOIN portfolios p ON p.portfolio_id = o.portfolio_id
            JOIN customers c ON c.user_id = p.customer_id
            JOIN users u ON u.user_id = c.user_id
            JOIN instruments i ON i.instrument_id = o.instrument_id
            LEFT JOIN trades t ON t.order_id = o.order_id
            WHERE (
                :search = ''
                OR CAST(o.order_id AS TEXT) LIKE CONCAT('%', :search, '%')
                OR CAST(t.trade_id AS TEXT) LIKE CONCAT('%', :search, '%')
                OR CAST(c.user_id AS TEXT) LIKE CONCAT('%', :search, '%')
                OR LOWER(COALESCE(u.first_name, '')) LIKE CONCAT('%', LOWER(:search), '%')
                OR LOWER(COALESCE(u.last_name, '')) LIKE CONCAT('%', LOWER(:search), '%')
                OR LOWER(CONCAT(
                    COALESCE(u.first_name, ''), ' ',
                    COALESCE(u.last_name, '')
                )) LIKE CONCAT('%', LOWER(:search), '%')
                OR LOWER(COALESCE(i.symbol, '')) LIKE CONCAT('%', LOWER(:search), '%')
                OR LOWER(COALESCE(i.name, '')) LIKE CONCAT('%', LOWER(:search), '%')
            )
            AND (
                :status = ''
                OR UPPER(CAST(o.order_status AS TEXT)) = UPPER(:status)
            )
            ORDER BY o.submitted_at DESC NULLS LAST, o.order_id DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM trade_orders o
            JOIN portfolios p ON p.portfolio_id = o.portfolio_id
            JOIN customers c ON c.user_id = p.customer_id
            JOIN users u ON u.user_id = c.user_id
            JOIN instruments i ON i.instrument_id = o.instrument_id
            LEFT JOIN trades t ON t.order_id = o.order_id
            WHERE (
                :search = ''
                OR CAST(o.order_id AS TEXT) LIKE CONCAT('%', :search, '%')
                OR CAST(t.trade_id AS TEXT) LIKE CONCAT('%', :search, '%')
                OR CAST(c.user_id AS TEXT) LIKE CONCAT('%', :search, '%')
                OR LOWER(COALESCE(u.first_name, '')) LIKE CONCAT('%', LOWER(:search), '%')
                OR LOWER(COALESCE(u.last_name, '')) LIKE CONCAT('%', LOWER(:search), '%')
                OR LOWER(CONCAT(
                    COALESCE(u.first_name, ''), ' ',
                    COALESCE(u.last_name, '')
                )) LIKE CONCAT('%', LOWER(:search), '%')
                OR LOWER(COALESCE(i.symbol, '')) LIKE CONCAT('%', LOWER(:search), '%')
                OR LOWER(COALESCE(i.name, '')) LIKE CONCAT('%', LOWER(:search), '%')
            )
            AND (
                :status = ''
                OR UPPER(CAST(o.order_status AS TEXT)) = UPPER(:status)
            )
            """,
            nativeQuery = true)
    Page<TradeOrder> findAdminPage(
            @Param("search") String search,
            @Param("status") String status,
            Pageable pageable
    );
}
