import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { Subject, forkJoin, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, finalize, takeUntil } from 'rxjs/operators';
import { AuthService, User } from '../../services/auth.service';
import { InstrumentService, Instrument } from '../../services/instrumentService';
import { MarketService, MarketStatusResponse, PricePointResponse } from '../../services/market.service';
import { HoldingResponse, PortfolioResponse, PortfolioService } from '../../services/portfolio.service';
import { WatchlistInstrumentResponse, WatchlistResponse, WatchlistService } from '../../services/watchlist.service';

type DashboardTab = 'holdings' | 'watchlist' | 'crypto';
type RangeKey = '1D' | '1W' | '1M' | '3M' | '1Y' | 'ALL';

interface DashboardHolding extends HoldingResponse {
  instrumentId: number | null;
  instrumentType: string;
  todaysChangePercent: number | null;
}

interface ChartPoint {
  label: string;
  value: number;
}

interface SummaryCard {
  title: string;
  subtitle: string;
  value: number;
  changePercent: number | null;
  tone: 'positive' | 'negative' | 'neutral';
  sparklinePath: string;
}

interface NewsItem {
  source: string;
  age: string;
  title: string;
  summary: string;
}

interface MarketMover {
  symbol: string;
  price: number;
  changePercent: number;
}

interface PlaceOrderRequest {
  instrument_id: number;
  order_action: 'BUY' | 'SELL';
  order_type: 'MARKET' | 'LIMIT';
  quantity: number;
  limit_price: number | null;
  time_in_force: 'DAY' | 'GTC';
}

interface OrderResponse {
  order_id: number;
  instrument_symbol: string;
  order_action: string;
  order_type: string;
  order_status: string;
  quantity: number;
  limit_price?: number;
  rejection_reason?: string;
}

interface TradeResponse {
  trade_id: number;
  execution_price: number;
  total_usd_value: number;
}

interface PlaceOrderSuccess {
  order: OrderResponse;
  trade?: TradeResponse;
}

interface ApiError {
  message: string;
  order?: OrderResponse;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatDividerModule
  ],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit, OnDestroy {
  private readonly tradeApiUrl = '/api/trade';
  private readonly portfolioRanges: Record<RangeKey, { days: number; points: number }> = {
    '1D': { days: 1, points: 8 },
    '1W': { days: 7, points: 8 },
    '1M': { days: 30, points: 10 },
    '3M': { days: 90, points: 12 },
    '1Y': { days: 365, points: 12 },
    'ALL': { days: 730, points: 14 }
  };

  currentUser: User | null = null;
  searchQuery = '';
  searchResults: Instrument[] = [];
  selectedTab: DashboardTab = 'holdings';
  activeRange: RangeKey = '3M';
  holdingsFilterQuery = '';
  isDashboardLoading = true;
  isChartLoading = false;
  dashboardError = '';
  marketStatus: MarketStatusResponse | null = null;
  portfolio: PortfolioResponse | null = null;
  holdings: DashboardHolding[] = [];
  allInstruments: Instrument[] = [];
  chartPoints: ChartPoint[] = [];
  watchlists: WatchlistResponse[] = [];
  watchlistInstruments: WatchlistInstrumentResponse[] = [];
  activeWatchlistName = 'Watchlist';
  orderLookupId: number | null = null;
  lookupResult: OrderResponse | null = null;

  orderForm: PlaceOrderRequest = {
    instrument_id: 1,
    order_action: 'BUY',
    order_type: 'MARKET',
    quantity: 1,
    limit_price: null,
    time_in_force: 'DAY'
  };

  orderSubmitInProgress = false;
  orderSubmitMessage = '';
  orderSubmitError = '';
  placedOrder: OrderResponse | null = null;
  placedTrade: TradeResponse | null = null;
  tradePanelOpen = false;

  readonly rangeOptions: RangeKey[] = ['1D', '1W', '1M', '3M', '1Y', 'ALL'];
  readonly marketNews: NewsItem[] = [
    {
      source: 'Bloomberg',
      age: '10m ago',
      title: 'Treasury yields steady as rate-cut timing stays in focus',
      summary: 'Large-cap equities drift higher while traders price in a slower path for easing.'
    },
    {
      source: 'TechCrunch',
      age: '1h ago',
      title: 'Chip infrastructure names lead another AI-heavy session',
      summary: 'Semiconductor suppliers and cloud platforms extended gains on new enterprise demand signals.'
    },
    {
      source: 'Reuters',
      age: '3h ago',
      title: 'Oil ticks up as supply expectations tighten into quarter-end',
      summary: 'Energy shares firmed after inventory data and renewed focus on export constraints.'
    },
    {
      source: 'CNBC',
      age: '5h ago',
      title: 'Bitcoin stabilizes after rally as ETF flows remain constructive',
      summary: 'Crypto markets held recent gains as spot demand and risk appetite stayed resilient.'
    }
  ];

  readonly topGainers: MarketMover[] = [
    { symbol: 'NVDA', price: 875.12, changePercent: 5.82 },
    { symbol: 'AMD', price: 184.22, changePercent: 4.11 },
    { symbol: 'COIN', price: 241.5, changePercent: 3.85 }
  ];

  readonly topLosers: MarketMover[] = [
    { symbol: 'TSLA', price: 179.24, changePercent: -3.15 },
    { symbol: 'INTC', price: 42.15, changePercent: -2.45 },
    { symbol: 'BABA', price: 71.9, changePercent: -1.82 }
  ];

  private destroy$ = new Subject<void>();
  private searchSubject$ = new Subject<string>();

  constructor(
    private authService: AuthService,
    private router: Router,
    private instrumentService: InstrumentService,
    private portfolioService: PortfolioService,
    private marketService: MarketService,
    private watchlistService: WatchlistService,
    private http: HttpClient
  ) {}

  get displayName(): string {
    const preferredName = this.currentUser?.username?.trim();
    if (preferredName) {
      return preferredName;
    }

    const emailName = this.currentUser?.email?.split('@')[0]?.replace(/[._-]+/g, ' ').trim();
    if (!emailName) {
      return 'Investor';
    }

    return emailName.replace(/\b\w/g, (match) => match.toUpperCase());
  }

  get userInitials(): string {
    const tokens = this.displayName.split(' ').filter(Boolean);
    return tokens.slice(0, 2).map((token) => token[0]?.toUpperCase() ?? '').join('') || 'LP';
  }

  get marketStatusLabel(): string {
    return this.marketStatus?.open ? 'MARKET OPEN' : 'MARKET CLOSED';
  }

  get cashBalance(): number {
    return this.toNumber(this.portfolio?.cashBalanceUsd);
  }

  get totalHoldingsValue(): number {
    return this.holdings.reduce((sum, holding) => sum + this.getHoldingMarketValue(holding), 0);
  }

  get totalPortfolioValue(): number {
    return this.cashBalance + this.totalHoldingsValue;
  }

  get totalCostBasis(): number {
    return this.holdings.reduce((sum, holding) => {
      return sum + this.toNumber(holding.quantity) * this.toNumber(holding.averageCostUsd);
    }, 0);
  }

  get totalGainLossUsd(): number {
    return this.holdings.reduce((sum, holding) => sum + this.toNumber(holding.gainLossUsd), 0);
  }

  get totalGainLossPercent(): number {
    const costBasis = this.totalCostBasis;
    if (!costBasis) {
      return 0;
    }

    return this.totalGainLossUsd / costBasis * 100;
  }

  get nonCryptoHoldingsValue(): number {
    return this.holdings
      .filter((holding) => holding.instrumentType !== 'CRYPTO')
      .reduce((sum, holding) => sum + this.getHoldingMarketValue(holding), 0);
  }

  get cryptoHoldings(): DashboardHolding[] {
    return this.holdings.filter((holding) => holding.instrumentType === 'CRYPTO');
  }

  get cryptoHoldingsValue(): number {
    return this.cryptoHoldings.reduce((sum, holding) => sum + this.getHoldingMarketValue(holding), 0);
  }

  get brokerageValue(): number {
    return this.cashBalance + this.nonCryptoHoldingsValue;
  }

  get watchlistCount(): number {
    return this.watchlistInstruments.length;
  }

  get filteredHoldings(): DashboardHolding[] {
    const filtered = this.filterByQuery(this.holdings, this.holdingsFilterQuery);
    if (this.selectedTab === 'crypto') {
      return filtered.filter((holding) => holding.instrumentType === 'CRYPTO');
    }

    return filtered;
  }

  get hasSearchResults(): boolean {
    return this.searchQuery.trim().length > 0 && this.searchResults.length > 0;
  }

  get accountCards(): SummaryCard[] {
    return [
      {
        title: 'Brokerage Account',
        subtitle: 'Cash plus listed holdings',
        value: this.brokerageValue,
        changePercent: this.totalGainLossPercent,
        tone: this.getTone(this.totalGainLossPercent),
        sparklinePath: this.buildSparklinePath(this.chartPoints.slice(0, 7).map((point) => point.value), 176, 42)
      },
      {
        title: 'Crypto Wallet',
        subtitle: 'Digital asset exposure',
        value: this.cryptoHoldingsValue,
        changePercent: this.getCategoryChangePercent('crypto'),
        tone: this.getTone(this.getCategoryChangePercent('crypto')),
        sparklinePath: this.buildSparklinePath(this.cryptoHoldings.map((holding) => this.getHoldingMarketValue(holding)), 176, 42)
      }
    ];
  }

  ngOnInit(): void {
    this.authService.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe((user) => {
        this.currentUser = user;
      });

    this.searchSubject$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        takeUntil(this.destroy$)
      )
      .subscribe((query) => {
        this.performSearch(query);
      });

    this.loadDashboard();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSearchInput(): void {
    this.searchSubject$.next(this.searchQuery);
  }

  setSelectedTab(tab: DashboardTab): void {
    this.selectedTab = tab;
  }

  setActiveRange(range: RangeKey): void {
    if (this.activeRange === range) {
      return;
    }

    this.activeRange = range;
    this.loadPortfolioHistory();
  }

  selectInstrument(instrument: Instrument): void {
    this.orderForm.instrument_id = Number(instrument.instrumentId);
    this.searchQuery = instrument.symbol;
    this.searchResults = [];
    this.orderSubmitError = '';
    this.orderSubmitMessage = `Selected ${instrument.symbol} for order entry.`;
    this.tradePanelOpen = true;
  }

  setTradeAction(action: 'BUY' | 'SELL'): void {
    this.orderForm.order_action = action;
    this.orderSubmitError = '';
    this.orderSubmitMessage = `${action} ticket is ready.`;
    this.tradePanelOpen = true;
  }

  openTradePanel(): void {
    this.orderSubmitError = '';
    this.orderSubmitMessage = 'Trade ticket is ready.';
    this.tradePanelOpen = true;
  }

  showUnavailableAction(action: 'Transfer' | 'Deposit'): void {
    this.orderSubmitError = '';
    this.orderSubmitMessage = `${action} is not wired to an API yet.`;
  }

  openTradeForHolding(holding: DashboardHolding, action: 'BUY' | 'SELL'): void {
    if (holding.instrumentId) {
      this.orderForm.instrument_id = holding.instrumentId;
    }

    this.orderForm.order_action = action;
    this.orderSubmitError = '';
    this.orderSubmitMessage = `${action} ticket prepared for ${holding.instrumentSymbol}.`;
    this.tradePanelOpen = true;
  }

  toggleTradePanel(): void {
    this.tradePanelOpen = !this.tradePanelOpen;
  }

  submitOrder(): void {
    this.orderSubmitError = '';
    this.orderSubmitMessage = '';
    this.placedOrder = null;
    this.placedTrade = null;

    if (!this.orderForm.instrument_id || this.orderForm.instrument_id <= 0) {
      this.orderSubmitError = 'Instrument ID is required.';
      return;
    }

    if (!this.orderForm.quantity || this.orderForm.quantity <= 0) {
      this.orderSubmitError = 'Quantity must be greater than zero.';
      return;
    }

    if (this.orderForm.order_type === 'LIMIT' && (!this.orderForm.limit_price || this.orderForm.limit_price <= 0)) {
      this.orderSubmitError = 'Limit price is required for LIMIT orders.';
      return;
    }

    if (this.orderForm.order_type === 'MARKET') {
      this.orderForm.limit_price = null;
    }

    this.orderSubmitInProgress = true;

    this.http.post<OrderResponse | PlaceOrderSuccess | ApiError>(`${this.tradeApiUrl}/orders`, this.orderForm)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          this.orderSubmitInProgress = false;
          const executed = response as PlaceOrderSuccess;
          const orderOnly = response as OrderResponse;

          if (executed.order) {
            this.placedOrder = executed.order;
            this.placedTrade = executed.trade ?? null;
            this.orderSubmitMessage = 'Order placed successfully.';
            this.refreshPortfolioSlice();
            return;
          }

          if (orderOnly.order_id) {
            this.placedOrder = orderOnly;
            this.orderSubmitMessage = 'Order placed successfully.';
            this.refreshPortfolioSlice();
            return;
          }

          this.orderSubmitMessage = 'Request completed.';
        },
        error: (error) => {
          this.orderSubmitInProgress = false;
          const apiError = error?.error as ApiError;
          this.orderSubmitError = apiError?.message || 'Failed to place order.';
          this.placedOrder = apiError?.order ?? null;
          this.placedTrade = null;
        }
      });
  }

  fetchOrderById(): void {
    this.orderSubmitError = '';
    this.orderSubmitMessage = '';
    this.lookupResult = null;

    if (!this.orderLookupId || this.orderLookupId <= 0) {
      this.orderSubmitError = 'Enter a valid order ID to lookup.';
      return;
    }

    this.http.get<OrderResponse>(`${this.tradeApiUrl}/orders/${this.orderLookupId}`)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (order) => {
          this.lookupResult = order;
          this.orderSubmitMessage = 'Order loaded.';
        },
        error: (error) => {
          const apiError = error?.error as ApiError;
          this.orderSubmitError = apiError?.message || 'Failed to load order.';
        }
      });
  }

  getPortfolioLinePath(): string {
    return this.buildLinePath(this.chartPoints.map((point) => point.value), 1000, 260);
  }

  getPortfolioAreaPath(): string {
    return this.buildAreaPath(this.chartPoints.map((point) => point.value), 1000, 260);
  }

  getHoldingMarketValue(holding: HoldingResponse): number {
    const marketValue = this.toNumber(holding.marketValueUsd);
    if (marketValue > 0) {
      return marketValue;
    }

    return this.toNumber(holding.quantity) * this.toNumber(holding.currentPriceUsd || holding.averageCostUsd);
  }

  getTone(value: number | null): 'positive' | 'negative' | 'neutral' {
    if (value === null || value === 0) {
      return 'neutral';
    }

    return value > 0 ? 'positive' : 'negative';
  }

  formatSignedCurrency(value: number): string {
    const absolute = Math.abs(value).toLocaleString('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });

    return `${value >= 0 ? '+' : '-'}${absolute.replace('-', '')}`;
  }

  formatSignedPercent(value: number | null): string {
    if (value === null) {
      return '0.00%';
    }

    return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`;
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  private loadDashboard(): void {
    this.isDashboardLoading = true;
    this.dashboardError = '';

    forkJoin({
      portfolio: this.portfolioService.getMyPortfolio().pipe(catchError(() => of<PortfolioResponse | null>(null))),
      instruments: this.instrumentService.getAllInstruments().pipe(catchError(() => of<Instrument[]>([]))),
      watchlists: this.watchlistService.getWatchlists().pipe(catchError(() => of<WatchlistResponse[]>([]))),
      marketStatus: this.marketService.getMarketStatus('NASDAQ').pipe(catchError(() => of<MarketStatusResponse | null>(null)))
    })
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.isDashboardLoading = false;
        })
      )
      .subscribe(({ portfolio, instruments, watchlists, marketStatus }) => {
        this.allInstruments = instruments;
        this.watchlists = watchlists;
        this.marketStatus = marketStatus;

        if (!portfolio) {
          this.dashboardError = 'Unable to load your portfolio data.';
          this.portfolio = null;
          this.holdings = [];
          this.chartPoints = this.buildFallbackChart();
          return;
        }

        this.applyPortfolio(portfolio);
        this.loadPortfolioHistory();

        if (watchlists.length > 0) {
          this.loadWatchlistInstruments(watchlists[0].watchlistId, watchlists[0].watchlistName);
        }
      });
  }

  private refreshPortfolioSlice(): void {
    this.portfolioService.getMyPortfolio()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (portfolio: PortfolioResponse) => {
          this.applyPortfolio(portfolio);
          this.loadPortfolioHistory();
        },
        error: () => {
          this.dashboardError = 'Portfolio refresh failed after the latest trade.';
        }
      });
  }

  private loadWatchlistInstruments(watchlistId: number, watchlistName: string): void {
    this.activeWatchlistName = watchlistName;
    this.watchlistService.getWatchlistInstruments(watchlistId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (instruments: WatchlistInstrumentResponse[]) => {
          this.watchlistInstruments = instruments;
        },
        error: () => {
          this.watchlistInstruments = [];
        }
      });
  }

  private applyPortfolio(portfolio: PortfolioResponse): void {
    this.portfolio = portfolio;
    this.holdings = portfolio.holdings.map((holding: HoldingResponse) => {
      const matchedInstrument = this.allInstruments.find((instrument) => instrument.symbol === holding.instrumentSymbol);
      return {
        ...holding,
        instrumentId: matchedInstrument ? Number(matchedInstrument.instrumentId) : null,
        instrumentType: matchedInstrument?.instrumentType ?? this.inferInstrumentType(holding.instrumentSymbol),
        todaysChangePercent: holding.gainLossPercent ?? null
      };
    });
  }

  private loadPortfolioHistory(): void {
    const range = this.portfolioRanges[this.activeRange];
    const holdings = this.holdings.filter((holding) => this.toNumber(holding.quantity) > 0);

    if (holdings.length === 0) {
      this.chartPoints = this.buildFallbackChart();
      return;
    }

    const to = new Date();
    const from = new Date(to.getTime() - range.days * 24 * 60 * 60 * 1000);

    this.isChartLoading = true;

    forkJoin(
      holdings.map((holding) => this.marketService.getPriceHistory(
        holding.instrumentSymbol,
        from.toISOString(),
        to.toISOString()
      ).pipe(catchError(() => of<PricePointResponse[]>([]))))
    )
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.isChartLoading = false;
        })
      )
      .subscribe((histories: PricePointResponse[][]) => {
        this.chartPoints = this.buildChartPoints(holdings, histories, from, to, range.points);
      });
  }

  private buildChartPoints(
    holdings: DashboardHolding[],
    histories: PricePointResponse[][],
    from: Date,
    to: Date,
    points: number
  ): ChartPoint[] {
    const fromMs = from.getTime();
    const toMs = to.getTime();
    const pointCount = Math.max(points, 2);
    const step = (toMs - fromMs) / (pointCount - 1);

    return Array.from({ length: pointCount }, (_, index) => {
      const targetTime = fromMs + step * index;
      const bucketDate = new Date(targetTime);
      const holdingsValue = holdings.reduce((sum, holding, historyIndex) => {
        const history = [...histories[historyIndex]].sort((left, right) => {
          return new Date(left.timestamp).getTime() - new Date(right.timestamp).getTime();
        });
        const price = this.findHistoricalPrice(history, targetTime, this.toNumber(holding.currentPriceUsd || holding.averageCostUsd));
        return sum + this.toNumber(holding.quantity) * price;
      }, 0);

      return {
        label: this.formatRangeLabel(bucketDate),
        value: this.cashBalance + holdingsValue
      };
    });
  }

  private buildFallbackChart(): ChartPoint[] {
    const baseValue = this.totalPortfolioValue || 0;
    return ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'].map((label, index) => ({
      label,
      value: baseValue * (0.98 + index * 0.01)
    }));
  }

  private findHistoricalPrice(history: PricePointResponse[], targetTime: number, fallback: number): number {
    let latestValue = fallback;
    for (const point of history) {
      const pointTime = new Date(point.timestamp).getTime();
      if (pointTime <= targetTime) {
        latestValue = this.toNumber(point.price) || latestValue;
        continue;
      }
      break;
    }

    return latestValue;
  }

  private formatRangeLabel(date: Date): string {
    if (this.activeRange === '1D') {
      return date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
    }

    if (this.activeRange === '1W' || this.activeRange === '1M') {
      return date.toLocaleDateString([], { month: 'short', day: 'numeric' });
    }

    return date.toLocaleDateString([], { month: 'short' });
  }

  private buildLinePath(values: number[], width: number, height: number): string {
    if (values.length === 0) {
      return '';
    }

    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min || 1;

    return values.map((value, index) => {
      const x = values.length === 1 ? width / 2 : index / (values.length - 1) * width;
      const y = height - ((value - min) / range) * (height - 24) - 12;
      return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
    }).join(' ');
  }

  private buildAreaPath(values: number[], width: number, height: number): string {
    if (values.length === 0) {
      return '';
    }

    const linePath = this.buildLinePath(values, width, height);
    return `${linePath} L ${width} ${height} L 0 ${height} Z`;
  }

  private buildSparklinePath(values: number[], width: number, height: number): string {
    const safeValues = values.length > 1 ? values : [0, values[0] ?? 0, values[0] ?? 0];
    return this.buildLinePath(safeValues, width, height);
  }

  private inferInstrumentType(symbol: string): string {
    const normalized = symbol.toUpperCase();
    if (['BTC', 'ETH', 'SOL', 'DOGE'].includes(normalized)) {
      return 'CRYPTO';
    }

    return 'STOCK';
  }

  private filterByQuery(items: DashboardHolding[], query: string): DashboardHolding[] {
    const normalizedQuery = query.trim().toLowerCase();
    if (!normalizedQuery) {
      return items;
    }

    return items.filter((holding) => {
      return holding.instrumentSymbol.toLowerCase().includes(normalizedQuery)
        || holding.instrumentName.toLowerCase().includes(normalizedQuery)
        || holding.instrumentType.toLowerCase().includes(normalizedQuery);
    });
  }

  private getCategoryChangePercent(category: 'crypto' | 'non-crypto'): number {
    const relevantHoldings = this.holdings.filter((holding) => {
      return category === 'crypto'
        ? holding.instrumentType === 'CRYPTO'
        : holding.instrumentType !== 'CRYPTO';
    });

    const costBasis = relevantHoldings.reduce((sum, holding) => {
      return sum + this.toNumber(holding.quantity) * this.toNumber(holding.averageCostUsd);
    }, 0);

    if (!costBasis) {
      return 0;
    }

    const gain = relevantHoldings.reduce((sum, holding) => sum + this.toNumber(holding.gainLossUsd), 0);
    return gain / costBasis * 100;
  }

  private performSearch(query: string): void {
    if (!query.trim()) {
      this.searchResults = [];
      return;
    }

    this.instrumentService.searchInstruments(query)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (results) => {
          this.searchResults = results;
        },
        error: () => {
          this.searchResults = [];
        }
      });
  }

  private toNumber(value: number | null | undefined): number {
    return Number(value ?? 0);
  }
}
