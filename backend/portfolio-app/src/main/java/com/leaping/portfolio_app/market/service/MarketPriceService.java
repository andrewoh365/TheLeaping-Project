package com.leaping.portfolio_app.market.service;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * MarketPriceService provides current market prices for all tradeable instruments.
 * 
 * Currently uses hardcoded prices for testing and development.
 * Can be extended to fetch from real market data APIs (Alpha Vantage, CoinGecko, etc.)
 * 
 * Prices are in USD as of September 28, 2026.
 */
@Service
public class MarketPriceService {

    /**
     * Hardcoded market prices for supported instruments.
     * Format: Symbol -> Current Price in USD
     * 
     * Sources (realistic projections for Sept 28, 2026):
     * - US Stocks: Major tech and market leaders
     * - Crypto: Top market cap cryptocurrencies
     * - Forex: Major currency pairs (normalized to USD quote)
     */
    private static final Map<String, BigDecimal> CURRENT_PRICES = Map.ofEntries(
        // ===== US STOCKS (NASDAQ/NYSE) =====
        // Tech Giants
        Map.entry("AAPL", new BigDecimal("222.50")),      // Apple
        Map.entry("MSFT", new BigDecimal("431.25")),      // Microsoft
        Map.entry("GOOGL", new BigDecimal("192.40")),     // Google/Alphabet
        Map.entry("AMZN", new BigDecimal("190.65")),      // Amazon
        
        // AI & Semiconductors
        Map.entry("NVDA", new BigDecimal("875.33")),      // NVIDIA
        Map.entry("AMD", new BigDecimal("198.75")),       // Advanced Micro Devices
        Map.entry("SMCI", new BigDecimal("156.20")),      // Super Micro Computer
        
        // Social Media & Entertainment
        Map.entry("META", new BigDecimal("555.45")),      // Meta Platforms (Facebook)
        Map.entry("NFLX", new BigDecimal("312.10")),      // Netflix
        Map.entry("DIS", new BigDecimal("98.75")),        // Disney
        
        // Finance & Banking
        Map.entry("TSLA", new BigDecimal("242.75")),      // Tesla
        Map.entry("JPM", new BigDecimal("245.30")),       // JPMorgan Chase
        Map.entry("BAC", new BigDecimal("42.55")),        // Bank of America
        
        // Consumer & Retail
        Map.entry("COST", new BigDecimal("892.10")),      // Costco
        Map.entry("WMT", new BigDecimal("105.40")),       // Walmart
        Map.entry("MCD", new BigDecimal("305.75")),       // McDonald's
        
        // Healthcare & Biotech
        Map.entry("JNJ", new BigDecimal("165.20")),       // Johnson & Johnson
        Map.entry("PFE", new BigDecimal("32.85")),        // Pfizer
        Map.entry("UNH", new BigDecimal("545.60")),       // UnitedHealth Group
        
        // ===== CRYPTOCURRENCIES (in USD) =====
        Map.entry("BTC", new BigDecimal("67500.00")),     // Bitcoin alias
        Map.entry("BTC-USD", new BigDecimal("67500.00")), // Bitcoin
        Map.entry("ETH", new BigDecimal("2650.75")),      // Ethereum alias
        Map.entry("ETH-USD", new BigDecimal("2650.75")),  // Ethereum
        Map.entry("BNB", new BigDecimal("612.45")),       // Binance Coin
        Map.entry("ADA", new BigDecimal("1.25")),         // Cardano
        Map.entry("SOL", new BigDecimal("185.30")),       // Solana
        Map.entry("XRP", new BigDecimal("2.85")),         // Ripple
        Map.entry("DOGE", new BigDecimal("0.35")),        // Dogecoin
        Map.entry("MATIC", new BigDecimal("0.95")),       // Polygon
        
        // ===== FOREX PAIRS (Major pairs, normalized to USD) =====
        Map.entry("EURUSD", new BigDecimal("1.0850")),    // EUR/USD alias
        Map.entry("EUR/USD", new BigDecimal("1.0850")),   // EUR/USD
        Map.entry("GBPUSD", new BigDecimal("1.2750")),    // GBP/USD alias
        Map.entry("GBP/USD", new BigDecimal("1.2750")),   // GBP/USD
        Map.entry("JPYUSD", new BigDecimal("0.0093")),    // JPY/USD (cents)
        Map.entry("CHFUSD", new BigDecimal("1.1200")),    // CHF/USD
        Map.entry("AUDUSD", new BigDecimal("0.6850")),    // AUD/USD
        Map.entry("CADUSD", new BigDecimal("0.7350")),    // CAD/USD
        Map.entry("NZDUSD", new BigDecimal("0.6100")),    // NZD/USD
        Map.entry("SGDUSD", new BigDecimal("0.7425"))     // SGD/USD
    );

    /**
     * Get the current market price for an instrument by symbol.
     * 
     * @param symbol The instrument symbol (e.g., "AAPL", "BTC", "EURUSD")
     * @return The current price in USD, or null if symbol not found
     */
    public BigDecimal getCurrentPrice(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return null;
        }
        return CURRENT_PRICES.get(symbol.toUpperCase());
    }

    /**
     * Check if a price is available for the given symbol.
     * 
     * @param symbol The instrument symbol
     * @return true if price data is available, false otherwise
     */
    public boolean hasPriceData(String symbol) {
        return symbol != null && CURRENT_PRICES.containsKey(symbol.toUpperCase());
    }

    /**
     * Get all available symbols with their prices.
     * Useful for market data endpoints.
     * 
     * @return Map of all symbol -> price entries
     */
    public Map<String, BigDecimal> getAllPrices() {
        return Map.copyOf(CURRENT_PRICES);
    }

    /**
     * Update a price (for testing or real-time updates).
     * Note: This is a simple in-memory implementation.
     * For production, integrate with a real market data provider.
     * 
     * @param symbol The instrument symbol
     * @param price The new price in USD
     */
    public void updatePrice(String symbol, BigDecimal price) {
        if (symbol != null && price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            // Note: This modifies the static map, which is not thread-safe
            // For production, use a thread-safe implementation (ConcurrentHashMap, cache, DB)
            // CURRENT_PRICES.put(symbol.toUpperCase(), price);
            
            // For now, we'll skip the actual update to keep the hardcoded prices stable
            // Can be implemented later with proper concurrency handling
        }
    }
}
