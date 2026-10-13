package com.leaping.portfolio_app.market.service;

import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.instrument.repository.InstrumentRepository;
import com.leaping.portfolio_app.market.dto.MarketMoverDto;
import com.leaping.portfolio_app.market.dto.MarketMoversDto;
import com.leaping.portfolio_app.market.dto.MarketNewsDto;
import com.leaping.portfolio_app.market.dto.NewsItemDto;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Market context for the client dashboard: today's market headlines and the day's
 * biggest movers among the most actively traded US stocks.
 *
 * Both come from free public feeds (no API key). Results are cached for a few minutes
 * so client page loads never hit the providers directly, and if a provider is down the
 * last good copy is served and marked stale rather than failing the dashboard.
 */
@Service
public class MarketInsightsService {

    private static final Logger log = LoggerFactory.getLogger(MarketInsightsService.class);

    static final String NEWS_URL =
            "https://news.google.com/rss/search?q=stock+market+when:1d&hl=en-US&gl=US&ceid=US:en";
    static final String MOVERS_URL =
            "https://api.nasdaq.com/api/marketmovers?assetclass=stocks&exchangestatus=currentMarket&limit=20";
    private static final Map<String, String> NASDAQ_HEADERS = Map.of(
            "Accept", "application/json",
            "Origin", "https://www.nasdaq.com",
            "Referer", "https://www.nasdaq.com/"
    );

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int MAX_NEWS_ITEMS = 15;
    private static final int MOVERS_PER_SIDE = 3;

    // Funds and leveraged products in the "most active" list aren't individual stocks
    private static final Pattern FUND_NAME = Pattern.compile(
            "(?i)\\b(ETF|ETN|SPDR|iShares|ProShares|Direxion|Invesco|Trust|Fund|Leveraged|Inverse|\\d+(\\.\\d+)?x)\\b");

    private final MarketFeedClient feedClient;
    private final InstrumentRepository instrumentRepository;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private Cached<MarketNewsDto> newsCache;
    private Cached<MarketMoversDto> moversCache;

    public MarketInsightsService(MarketFeedClient feedClient, InstrumentRepository instrumentRepository) {
        this.feedClient = feedClient;
        this.instrumentRepository = instrumentRepository;
    }

    /**
     * Keep both caches warm in the background so a client never waits on a provider.
     * Runs just inside the cache TTL; failures are logged and the last good copy kept.
     */
    @Scheduled(initialDelay = 5_000, fixedDelay = 9 * 60_000)
    public void refreshInBackground() {
        news(true);
        movers(true);
    }

    public MarketNewsDto getNews() {
        return news(false);
    }

    public MarketMoversDto getMovers() {
        return movers(false);
    }

    private synchronized MarketNewsDto news(boolean force) {
        newsCache = refreshIfExpired(newsCache, force, () -> {
            try {
                return new MarketNewsDto(
                        parseNews(feedClient.get(NEWS_URL, Map.of()), MAX_NEWS_ITEMS),
                        "Google News",
                        OffsetDateTime.now(),
                        false);
            } catch (Exception e) {
                throw new FeedUnavailableException("news", e);
            }
        });
        if (newsCache == null) {
            return new MarketNewsDto(List.of(), "Google News", null, true);
        }
        return newsCache.stale()
                ? new MarketNewsDto(newsCache.value().items(), newsCache.value().provider(), newsCache.value().fetchedAt(), true)
                : newsCache.value();
    }

    private synchronized MarketMoversDto movers(boolean force) {
        moversCache = refreshIfExpired(moversCache, force, () -> {
            try {
                return parseMovers(feedClient.get(MOVERS_URL, NASDAQ_HEADERS));
            } catch (FeedUnavailableException e) {
                throw e;
            } catch (Exception e) {
                throw new FeedUnavailableException("movers", e);
            }
        });
        if (moversCache == null) {
            return new MarketMoversDto(List.of(), List.of(), null, null, "Nasdaq", null, true);
        }
        MarketMoversDto movers = moversCache.value();
        return moversCache.stale()
                ? new MarketMoversDto(movers.gainers(), movers.losers(), movers.asOf(), movers.universe(),
                        movers.provider(), movers.fetchedAt(), true)
                : movers;
    }

    /**
     * Parse an RSS 2.0 feed into headlines, newest first. Publisher suffixes like
     * " - Reuters" are removed from titles, and only http(s) links are kept.
     */
    List<NewsItemDto> parseNews(String rss, int limit) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Feeds are untrusted input: block DOCTYPEs and external entities (XXE)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(rss)));

        List<NewsItemDto> items = new ArrayList<>();
        NodeList nodes = document.getElementsByTagName("item");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element item = (Element) nodes.item(i);
            String title = text(item, "title");
            String link = text(item, "link");
            if (title == null || !isHttpUrl(link)) {
                continue;
            }
            String source = text(item, "source");
            if (source != null && title.endsWith(" - " + source)) {
                title = title.substring(0, title.length() - source.length() - 3);
            }
            items.add(new NewsItemDto(title.trim(), source, link, parseRssDate(text(item, "pubDate"))));
        }

        items.sort(Comparator.comparing(NewsItemDto::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return items.size() > limit ? List.copyOf(items.subList(0, limit)) : items;
    }

    /**
     * Rank the most actively traded US stocks (by dollar volume) by today's % change and
     * return the top gainers and losers. Using the most-active list keeps the movers to
     * well-known, liquid names rather than penny stocks and warrants.
     */
    MarketMoversDto parseMovers(String json) {
        JsonNode section = jsonMapper.readTree(json).path("data").path("STOCKS").path("MostActiveByDollarVolume");
        JsonNode rows = section.path("table").path("rows");
        if (!rows.isArray() || rows.isEmpty()) {
            throw new FeedUnavailableException("movers", new IllegalStateException("No most-active rows in response"));
        }

        List<MarketMoverDto> stocks = new ArrayList<>();
        for (JsonNode row : rows) {
            String symbol = row.path("symbol").asString("").trim();
            String name = row.path("name").asString("").trim();
            BigDecimal changePercent = parseNumber(row.path("change").asString(null));
            if (symbol.isEmpty() || changePercent == null || FUND_NAME.matcher(name).find()) {
                continue;
            }
            Long instrumentId = instrumentRepository.findBySymbolIgnoreCaseAndIsActiveTrue(symbol)
                    .filter(instrument -> Boolean.TRUE.equals(instrument.getIsTradeable()))
                    .map(Instrument::getInstrumentId)
                    .orElse(null);
            stocks.add(new MarketMoverDto(
                    symbol,
                    name,
                    parseNumber(row.path("lastSalePrice").asString(null)),
                    parseNumber(row.path("lastSaleChange").asString(null)),
                    changePercent,
                    instrumentId));
        }

        List<MarketMoverDto> gainers = stocks.stream()
                .filter(stock -> stock.changePercent().signum() > 0)
                .sorted(Comparator.comparing(MarketMoverDto::changePercent).reversed())
                .limit(MOVERS_PER_SIDE)
                .toList();
        List<MarketMoverDto> losers = stocks.stream()
                .filter(stock -> stock.changePercent().signum() < 0)
                .sorted(Comparator.comparing(MarketMoverDto::changePercent))
                .limit(MOVERS_PER_SIDE)
                .toList();

        String asOf = section.path("dataAsOf").asString("").replaceFirst("(?i)^data as of\\s*", "").trim();
        return new MarketMoversDto(
                gainers,
                losers,
                asOf.isEmpty() ? null : asOf,
                "Most actively traded US stocks by dollar volume",
                "Nasdaq",
                OffsetDateTime.now(),
                false);
    }

    private <T> Cached<T> refreshIfExpired(Cached<T> current, boolean force, Supplier<T> fetch) {
        if (!force && current != null && !current.stale() && current.fetchedAt().plus(CACHE_TTL).isAfter(OffsetDateTime.now())) {
            return current;
        }
        if (!force && current != null && current.stale() && current.retryAt().isAfter(OffsetDateTime.now())) {
            return current;
        }
        try {
            return new Cached<>(fetch.get(), OffsetDateTime.now(), false, null);
        } catch (FeedUnavailableException e) {
            log.warn("Market {} feed unavailable: {}", e.feed, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            // Keep serving the last good copy; retry in a minute rather than on every request
            OffsetDateTime retryAt = OffsetDateTime.now().plusMinutes(1);
            return current == null ? null : new Cached<>(current.value(), current.fetchedAt(), true, retryAt);
        }
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = nodes.item(0).getTextContent();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isHttpUrl(String value) {
        if (value == null) {
            return false;
        }
        try {
            String scheme = URI.create(value).getScheme();
            return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static OffsetDateTime parseRssDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toOffsetDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Parse provider number strings such as "$1,029.50", "-2.4225" or "+5.1665%". */
    private static BigDecimal parseNumber(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[$,%+\\s]", "");
        if (cleaned.isEmpty() || cleaned.equalsIgnoreCase("N/A")) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record Cached<T>(T value, OffsetDateTime fetchedAt, boolean stale, OffsetDateTime retryAt) {
    }

    static class FeedUnavailableException extends RuntimeException {
        private final String feed;

        FeedUnavailableException(String feed, Throwable cause) {
            super(cause);
            this.feed = feed;
        }
    }
}
