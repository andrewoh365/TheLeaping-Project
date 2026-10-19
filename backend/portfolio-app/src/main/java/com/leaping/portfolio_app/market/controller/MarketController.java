package com.leaping.portfolio_app.market.controller;

import com.leaping.portfolio_app.market.dto.MarketDto;
import com.leaping.portfolio_app.market.dto.MarketMoversDto;
import com.leaping.portfolio_app.market.dto.MarketNewsDto;
import com.leaping.portfolio_app.market.dto.MarketStatusDto;
import com.leaping.portfolio_app.market.dto.PricePointDto;
import com.leaping.portfolio_app.market.dto.QuoteDto;
import com.leaping.portfolio_app.market.dto.TradabilityDto;
import com.leaping.portfolio_app.market.entity.Market;
import com.leaping.portfolio_app.market.entity.Price;
import com.leaping.portfolio_app.market.repository.CurrencyRepository;
import com.leaping.portfolio_app.market.repository.MarketRepository;
import com.leaping.portfolio_app.market.service.MarketHoursService;
import com.leaping.portfolio_app.market.service.MarketInsightsService;
import com.leaping.portfolio_app.market.service.MarketPriceService;
import com.leaping.portfolio_app.market.service.PreTradeMarketService;
import com.leaping.portfolio_app.market.service.MarketStatus;
import com.leaping.portfolio_app.market.service.PricingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketRepository marketRepository;
    private final CurrencyRepository currencyRepository;
    private final MarketHoursService marketHoursService;
    private final PricingService pricingService;
    private final MarketPriceService marketPriceService;
    private final PreTradeMarketService preTradeMarketService;
    private final MarketInsightsService marketInsightsService;

    public MarketController(
            MarketRepository marketRepository,
            CurrencyRepository currencyRepository,
            MarketHoursService marketHoursService,
            PricingService pricingService,
            MarketPriceService marketPriceService,
            PreTradeMarketService preTradeMarketService,
            MarketInsightsService marketInsightsService
    ) {
        this.marketRepository = marketRepository;
        this.currencyRepository = currencyRepository;
        this.marketHoursService = marketHoursService;
        this.pricingService = pricingService;
        this.marketPriceService = marketPriceService;
        this.preTradeMarketService = preTradeMarketService;
        this.marketInsightsService = marketInsightsService;
    }

    /**
     * Today's market headlines (cached; served stale if the provider is unavailable).
     */
    @GetMapping("/news")
    public ResponseEntity<MarketNewsDto> getMarketNews() {
        return ResponseEntity.ok(marketInsightsService.getNews());
    }

    /**
     * Biggest gainers and losers among the most actively traded US stocks in the latest session.
     */
    @GetMapping("/movers")
    public ResponseEntity<MarketMoversDto> getMarketMovers() {
        return ResponseEntity.ok(marketInsightsService.getMovers());
    }

    @GetMapping("/exchanges")
    public ResponseEntity<List<MarketDto>> getExchanges() {
        List<MarketDto> markets = marketRepository.findByIsActiveTrueOrderByMarketNameAsc()
                .stream()
                .map(market -> new MarketDto(
                        market.getMarketName(),
                        market.getCountry(),
                        market.getTimezone(),
                        market.getOpenTime(),
                        market.getCloseTime(),
                        market.getIsActive()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(markets);
    }

    @GetMapping("/exchanges/{marketName}/status")
    public ResponseEntity<MarketStatusDto> getMarketStatus(@PathVariable String marketName) {
        return marketRepository.findByMarketNameIgnoreCase(marketName)
                .map(marketHoursService::getMarketStatus)
                .map(this::toStatusDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/instruments/price/latest")
    public ResponseEntity<PricePointDto> getLatestPriceByQuery(
            @RequestParam String symbol
    ) {
        return pricingService.getLatestPriceBySymbol(symbol)
                .flatMap(this::toUsdPriceDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/instruments/price/history")
    public ResponseEntity<List<PricePointDto>> getPriceHistoryByQuery(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        List<PricePointDto> history = pricingService.getPriceHistoryBySymbol(symbol, from, to)
                .stream()
                .map(this::toUsdPriceDto)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());

        return ResponseEntity.ok(history);
    }

    @GetMapping("/currencies")
    public ResponseEntity<List<String>> getCurrencies() {
        List<String> currencies = currencyRepository.findAll().stream()
                .map(currency -> currency.getCurrencyCode())
                .sorted()
                .collect(Collectors.toList());
        return ResponseEntity.ok(currencies);
    }

    @GetMapping("/instruments/quote")
    public ResponseEntity<QuoteDto> getQuoteByQuery(
            @RequestParam String symbol
    ) {
        return preTradeMarketService.getQuote(symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/quotes")
    public ResponseEntity<List<QuoteDto>> getQuotes(
            @RequestParam List<String> symbols
    ) {
        List<QuoteDto> quotes = symbols.stream()
                .map(preTradeMarketService::getQuote)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
        return ResponseEntity.ok(quotes);
    }

    @GetMapping("/instruments/tradability")
    public ResponseEntity<TradabilityDto> getTradabilityByQuery(
            @RequestParam String symbol
    ) {
        return ResponseEntity.ok(preTradeMarketService.getTradability(symbol));
    }

    private Optional<PricePointDto> toUsdPriceDto(Price price) {
        BigDecimal usdPrice = marketPriceService.toUsd(price);
        if (usdPrice == null) {
            return Optional.empty();
        }
        return Optional.of(new PricePointDto(
                price.getInstrument().getSymbol(),
                usdPrice,
                MarketPriceService.DISPLAY_CURRENCY,
                price.getPriceTimestamp(),
                price.getSourceType().name(),
                price.getProviderName()
        ));
    }

    private MarketStatusDto toStatusDto(MarketStatus status) {
        return new MarketStatusDto(
                status.getMarketName(),
                status.getTimezone(),
                status.getMarketTime(),
                status.isOpen(),
                status.isAlwaysOpen(),
                status.getNextOpen(),
                status.getNextClose()
        );
    }
}
