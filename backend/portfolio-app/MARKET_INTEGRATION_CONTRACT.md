# Market Integration Contract (Phase 1-2)

## Purpose
This document defines the integration contract between the market module and downstream order/portfolio execution workflows.

Current scope:
- Market data retrieval for latest quote and history.
- Pre-trade tradability checks.
- Standardized reason codes for non-tradable outcomes.

Out of scope:
- Order matching engine.
- Holdings mutation and cash ledger mutation logic.
- Trade execution persistence.

## Base Path
`/api/market`

## Authentication
All endpoints are expected to be called with a valid bearer token in environments where security is enabled.

Header:
`Authorization: Bearer <jwt-token>`

## Endpoints Used By Trade/Portfolio Flows

Canonical symbol-based endpoints (recommended):
- `GET /api/market/instruments/quote?symbol={symbol}`
- `GET /api/market/instruments/tradability?symbol={symbol}`
- `GET /api/market/instruments/price/latest?symbol={symbol}`
- `GET /api/market/instruments/price/history?symbol={symbol}&from={iso}&to={iso}`

These are the only supported symbol lookup forms. This keeps symbol handling consistent across equities, forex, and crypto.

## Supported Seeded Asset Universe (BR-12 Aligned)
The seeded universe includes multiple assets per required instrument class and market coverage:

- US equities: `AAPL`, `MSFT`, `NVDA`
- UK equities: `VOD`, `HSBA`, `BP`
- India equities: `RELIANCE`, `TCS`, `INFY`
- Crypto: `BTC-USD`, `ETH-USD`
- FX: `EUR/USD`, `GBP/USD`

This keeps scope aligned to BR-12: equities (UK/US and Indian markets), foreign exchange, and crypto assets.

### 1) Get single instrument quote
`GET /api/market/instruments/quote?symbol={symbol}`

Success: `200 OK`
```json
{
  "symbol": "AAPL",
  "price": 189.55,
  "currencyCode": "USD",
  "timestamp": "2026-09-27T20:00:00Z",
  "sourceType": "EOD",
  "provider": "Yahoo",
  "stale": false,
  "ageSeconds": 300
}
```

Not found: `404 Not Found`
- Returned when symbol is unknown or no quote record is available.

Contract notes:
- `price` is the raw instrument price in `currencyCode`.
- `stale=true` means quote exists but should not be used for execution decisions.
- `timestamp` and `ageSeconds` support recency validation and auditing.

### 2) Get tradability decision
`GET /api/market/instruments/tradability?symbol={symbol}`

Success: `200 OK`
```json
{
  "symbol": "AAPL",
  "tradable": true,
  "reason": "TRADABLE",
  "marketOpen": true,
  "alwaysOpen": false,
  "priceAvailable": true,
  "priceStale": false,
  "message": "Instrument is tradable"
}
```

Contract notes:
- This endpoint always returns `200` and expresses decision in payload.
- `tradable` is the primary decision field.
- `reason` is the machine-readable reason code.
- `message` is informational and not intended for strict branching logic.

### 3) Get bulk quotes
`GET /api/market/quotes?symbols=AAPL&symbols=BTC-USD&symbols=EUR/USD`

Success: `200 OK`
```json
[
  {
    "symbol": "AAPL",
    "price": 189.55,
    "currencyCode": "USD",
    "timestamp": "2026-09-27T20:00:00Z",
    "sourceType": "EOD",
    "provider": "Yahoo",
    "stale": false,
    "ageSeconds": 300
  }
]
```

Contract notes:
- Unknown symbols are omitted from the list (no per-symbol error object).
- Caller should reconcile requested vs returned symbols.

## Tradability Reason Codes
Current enum values:
- `TRADABLE`
- `INSTRUMENT_NOT_FOUND`
- `INSTRUMENT_INACTIVE`
- `INSTRUMENT_NOT_TRADEABLE`
- `MARKET_CLOSED`
- `PRICE_UNAVAILABLE`
- `PRICE_STALE`

## Recommended Consumer Decision Mapping
For order intake/execution services (to be implemented by trade/portfolio team):

Allow execution only when:
- `tradable == true`
- `reason == TRADABLE`
- If quote endpoint is also used, `stale == false`

Reject order when:
- `tradable == false`

Recommended persistence mapping for rejected orders:
- `order.rejection_reason` <- `reason`
- `execution_attempt.reason` <- `reason`
- `execution_attempt.attempt_status` <- `REJECTED`

Recommended persistence mapping for accepted tradable checks:
- Store quote fields used for decision (`price`, `currencyCode`, `timestamp`, `sourceType`, `provider`, `stale`, `ageSeconds`).
- Record `attempt_status` as downstream execution outcome (`FILLED` or `FAILED`) after execution step completes.

## Integration Sequence (Reference)
1. Validate symbol/order input.
2. Call tradability endpoint.
3. If non-tradable, reject with mapped reason.
4. If tradable, call quote endpoint (or use cached quote if your service policy allows).
5. Confirm quote recency/staleness policy.
6. Execute downstream cash/holding mutations atomically in trade/portfolio transaction boundary.
7. Persist order/trade/attempt audit records.

## Error Handling Expectations
- Network/timeouts calling market endpoints should be treated as transient integration failures.
- Recommended fallback behavior for order flow: reject or retry according to service policy, with explicit failure reason such as `MARKET_INTEGRATION_FAILURE` (consumer-defined reason, outside current market enum).

## Versioning and Compatibility
This contract represents current Phase 1-2 market APIs.

If fields or reason codes change:
- Keep additive changes backward compatible where possible.
- Coordinate enum changes with trade/portfolio consumers before deployment.
