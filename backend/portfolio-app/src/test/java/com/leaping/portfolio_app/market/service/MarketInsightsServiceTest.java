package com.leaping.portfolio_app.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.repository.InstrumentRepository;
import com.leaping.portfolio_app.market.dto.MarketMoversDto;
import com.leaping.portfolio_app.market.dto.MarketNewsDto;
import com.leaping.portfolio_app.market.dto.NewsItemDto;

@ExtendWith(MockitoExtension.class)
class MarketInsightsServiceTest {

    private static final String RSS = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel><title>stock market</title>
          <item>
            <title>Stocks slip as yields rise - Reuters</title>
            <link>https://news.example.com/a</link>
            <pubDate>Sun, 11 Oct 2026 13:19:13 GMT</pubDate>
            <source url="https://www.reuters.com">Reuters</source>
          </item>
          <item>
            <title>Futures due amid comments - Investor's Business Daily</title>
            <link>https://news.example.com/b</link>
            <pubDate>Sun, 11 Oct 2026 17:54:00 GMT</pubDate>
            <source url="https://www.investors.com">Investor's Business Daily</source>
          </item>
          <item>
            <title>Bad link should be dropped - Spam</title>
            <link>javascript:alert(1)</link>
            <pubDate>Sun, 11 Oct 2026 18:00:00 GMT</pubDate>
            <source>Spam</source>
          </item>
        </channel></rss>
        """;

    private static final String MOVERS_JSON = """
        {"data":{"STOCKS":{"MostActiveByDollarVolume":{
          "dataAsOf":"Data as of Oct 9, 2026 4:15 PM ET",
          "table":{"rows":[
            {"symbol":"MU","name":"Micron Technology, Inc.","lastSalePrice":"$1,029","lastSaleChange":"-6.84","change":"-0.6603%"},
            {"symbol":"NVDA","name":"NVIDIA Corporation","lastSalePrice":"$229.28","lastSaleChange":"-1.2","change":"-0.5207%"},
            {"symbol":"SPY","name":"SPDR S&P 500","lastSalePrice":"$778.54","lastSaleChange":"+4.61","change":"+0.5957%"},
            {"symbol":"MSFT","name":"Microsoft Corporation","lastSalePrice":"$535.07","lastSaleChange":"+12.46","change":"+2.3842%"},
            {"symbol":"AMD","name":"Advanced Micro Devices, Inc.","lastSalePrice":"$608.1","lastSaleChange":"-12.58","change":"-2.0268%"},
            {"symbol":"SOXL","name":"Direxion Daily Semiconductor Bull 3x Shares","lastSalePrice":"$139.74","lastSaleChange":"-2.78","change":"-11.95%"},
            {"symbol":"MRNA","name":"Moderna, Inc.","lastSalePrice":"$225","lastSaleChange":"+28","change":"+14.2132%"},
            {"symbol":"PLTR","name":"Palantir Technologies Inc.","lastSalePrice":"$209.05","lastSaleChange":"+10.27","change":"+5.1665%"},
            {"symbol":"QQQ","name":"Invesco QQQ Trust, Series 1","lastSalePrice":"$751.27","lastSaleChange":"+3.69","change":"+30.4936%"}
          ]}}}}}
        """;

    @Mock
    private MarketFeedClient feedClient;

    @Mock
    private InstrumentRepository instrumentRepository;

    @InjectMocks
    private MarketInsightsService service;

    @BeforeEach
    void setUp() {
        Instrument msft = new Instrument();
        msft.setInstrumentId(9L);
        msft.setSymbol("MSFT");
        msft.setIsTradeable(true);
        lenient().when(instrumentRepository.findBySymbolIgnoreCaseAndIsActiveTrue(anyString())).thenReturn(Optional.empty());
        lenient().when(instrumentRepository.findBySymbolIgnoreCaseAndIsActiveTrue("MSFT")).thenReturn(Optional.of(msft));
    }

    @Test
    void parseNewsShouldStripPublisherSortNewestFirstAndDropNonHttpLinks() throws Exception {
        List<NewsItemDto> items = service.parseNews(RSS, 10);

        assertEquals(2, items.size());
        assertEquals("Futures due amid comments", items.get(0).title());
        assertEquals("Investor's Business Daily", items.get(0).source());
        assertEquals("Stocks slip as yields rise", items.get(1).title());
        assertEquals("https://news.example.com/a", items.get(1).url());
        assertTrue(items.get(0).publishedAt().isAfter(items.get(1).publishedAt()));
    }

    @Test
    void parseNewsShouldRespectLimit() throws Exception {
        assertEquals(1, service.parseNews(RSS, 1).size());
    }

    @Test
    void parseNewsShouldRejectDoctypeToPreventXxe() {
        String xxe = """
            <?xml version="1.0"?>
            <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <rss><channel><item><title>&xxe;</title><link>https://x.example</link></item></channel></rss>
            """;
        assertThrows(Exception.class, () -> service.parseNews(xxe, 10));
    }

    @Test
    void parseMoversShouldRankStocksAndExcludeFunds() {
        MarketMoversDto movers = service.parseMovers(MOVERS_JSON);

        assertEquals(List.of("MRNA", "PLTR", "MSFT"), movers.gainers().stream().map(m -> m.symbol()).toList());
        assertEquals(List.of("AMD", "MU", "NVDA"), movers.losers().stream().map(m -> m.symbol()).toList());
        assertEquals(new BigDecimal("14.2132"), movers.gainers().get(0).changePercent());
        assertEquals(new BigDecimal("1029"), movers.losers().get(1).price());
        assertEquals("Oct 9, 2026 4:15 PM ET", movers.asOf());
        assertFalse(movers.stale());
    }

    @Test
    void parseMoversShouldLinkStocksTradableOnThePlatform() {
        MarketMoversDto movers = service.parseMovers(MOVERS_JSON);

        assertEquals(9L, movers.gainers().get(2).instrumentId()); // MSFT
        assertNull(movers.gainers().get(0).instrumentId());       // MRNA isn't offered
    }

    @Test
    void getMoversShouldCacheBetweenCalls() throws Exception {
        when(feedClient.get(eq(MarketInsightsService.MOVERS_URL), anyMap())).thenReturn(MOVERS_JSON);

        service.getMovers();
        service.getMovers();

        verify(feedClient, times(1)).get(eq(MarketInsightsService.MOVERS_URL), anyMap());
    }

    @Test
    void getNewsShouldReturnEmptyStaleResultWhenProviderIsDownAndNothingCached() throws Exception {
        when(feedClient.get(eq(MarketInsightsService.NEWS_URL), anyMap())).thenThrow(new IOException("HTTP 503"));

        MarketNewsDto news = service.getNews();

        assertTrue(news.items().isEmpty());
        assertTrue(news.stale());
    }
}
