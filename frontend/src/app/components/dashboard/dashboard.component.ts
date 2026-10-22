import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatListModule } from '@angular/material/list';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { Subject, Subscription, forkJoin, fromEvent, merge, of, timer } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, exhaustMap, filter, finalize, map, switchMap, takeUntil, tap } from 'rxjs/operators';
import { AuthService, User } from '../../services/auth.service';
import { InstrumentService, Instrument } from '../../services/instrument.service';
import {
  MarketMover,
  MarketMovers,
  MarketNews,
  MarketService,
  MarketOverrideStatus,
  MarketStatusResponse,
  NewsItem,
  PricePointResponse,
  QuoteResponse
} from '../../services/market.service';
import {
  CashDirection,
  CashTransferResponse,
  HoldingResponse,
  PortfolioResponse,
  PortfolioService
} from '../../services/portfolio.service';
import { WatchlistInstrumentResponse, WatchlistResponse, WatchlistService } from '../../services/watchlist.service';
import { WalkthroughService } from '../../services/walkthrough.service';
import { WalkthroughComponent } from '../walkthrough/walkthrough.component';
import { TourEvent, WALKTHROUGH_STEPS, WalkthroughStep } from '../walkthrough/walkthrough-steps';
import {
  OrderAction,
  OrderHistoryPage,
  OrderPreview,
  OrderResponse,
  PlaceOrderRequest,
  PlaceOrderSuccess,
  TradeApiError,
  TradeResponse,
  TradeService
} from '../../services/trade.service';

type DashboardTab = 'holdings' | 'watchlist' | 'crypto' | 'activity';
type RangeKey = '1D' | '1W' | '1M' | '3M' | '1Y' | 'ALL';

interface DashboardHolding extends HoldingResponse {
  instrumentId: number | null;
  instrumentType: string;
  todaysChangePercent: number | null;
}

/** One sample of portfolio value, split so the account cards can chart their own slice. */
interface ChartPoint {
  time: number;
  value: number;
  brokerage: number;
  crypto: number;
}

/** An axis label: position is 0-1 along the axis (x: from the left, y: from the top). */
interface AxisTick {
  position: number;
  label: string;
}

interface Sparkline {
  path: string;
  falling: boolean;
}

interface SummaryCard {
  title: string;
  subtitle: string;
  value: number;
  changePercent: number | null;
  tone: 'positive' | 'negative' | 'neutral';
  sparkline: Sparkline | null;
}

/** Main chart drawing area, in SVG units. The line is inset so its stroke isn't clipped at the edges. */
const CHART_WIDTH = 1000;
const CHART_HEIGHT = 260;
const CHART_INSET = 10;

/** Outcome of the last order, shown on the ticket in plain language. */
interface OrderResult {
  kind: 'filled' | 'rejected' | 'pending' | 'error';
  title: string;
  detail: string;
}

interface TicketInstrument {
  instrumentId: number;
  symbol: string;
  name: string;
}

/** How often the dashboard refreshes portfolio, prices and order status while visible. */
const LIVE_UPDATE_MS = 15000;
/** How long a changed value stays highlighted. */
const HIGHLIGHT_MS = 2000;
/** News and movers change slowly and are cached server-side; refresh them every 5 minutes. */
const MARKET_CONTEXT_MS = 5 * 60 * 1000;
const NEWS_COLLAPSED_COUNT = 5;
/** Matches the backend's per-transaction cap. */
const MAX_CASH_TRANSFER_USD = 1_000_000;
import { InstrumentExpandedComponent, InstrumentTradeAction } from '../instrument-expanded/instrument-expanded.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatToolbarModule,
    MatIconModule,
    MatMenuModule,
    MatCardModule,
    MatDividerModule,
    MatListModule,
    MatDialogModule,
    WalkthroughComponent
  ],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit, OnDestroy {
  private readonly portfolioRanges: Record<RangeKey, { days: number; points: number }> = {
    '1D': { days: 1, points: 49 },     // every 30 min
    '1W': { days: 7, points: 85 },     // every 2 h
    '1M': { days: 30, points: 61 },    // every 12 h
    '3M': { days: 90, points: 91 },    // daily
    '1Y': { days: 365, points: 105 },  // ~twice a week
    'ALL': { days: 730, points: 105 }  // ~weekly
  };


  currentUser: User | null = null;
  searchQuery = '';
  searchResults: Instrument[] = [];
  selectedTab: DashboardTab = 'holdings';
  activeRange: RangeKey = '3M';
  holdingsFilterQuery = '';
  holdingsFilterType = 'ALL';
  readonly holdingsFilterOptions = [
    { value: 'ALL', label: 'All' },
    { value: 'STOCK', label: 'Stocks' },
    { value: 'CRYPTO', label: 'Crypto' },
    { value: 'FOREX', label: 'Forex' }
  ];
  isDashboardLoading = true;
  isChartLoading = false;
  dashboardError = '';
  marketStatus: MarketStatusResponse | null = null;
  /** After-hours testing switch; null until loaded, and only shown where the backend allows it. */
  marketOverride: MarketOverrideStatus | null = null;
  private marketOverrideRequested = false;
  marketOverrideSaving = false;
  portfolio: PortfolioResponse | null = null;
  holdings: DashboardHolding[] = [];
  allInstruments: Instrument[] = [];
  chartPoints: ChartPoint[] = [];
  chartLinePath = '';
  chartAreaPath = '';
  chartXTicks: AxisTick[] = [];
  chartYTicks: AxisTick[] = [];
  private chartDomain = { min: 0, max: 1 };
  private brokerageSparkline: Sparkline | null = null;
  private cryptoSparkline: Sparkline | null = null;
  watchlists: WatchlistResponse[] = [];
  watchlistInstruments: WatchlistInstrumentResponse[] = [];
  activeWatchlistName = 'Watchlist';
  activeWatchlistId: number | null = null;
  watchlistQuotes: Record<string, QuoteResponse> = {};
  // In-flight watchlist requests, cancelled when the user switches lists so a
  // slow response for the previous list cannot overwrite the current one.
  private watchlistInstrumentsRequest?: Subscription;
  private watchlistQuotesRequest?: Subscription;
  watchlistLoading = false;
  watchlistQuotesLoading = false;
  watchlistSaving = false;
  watchlistError = '';
  watchlistSearchQuery = '';
  watchlistSearchResults: Instrument[] = [];
  watchlistSearchLoading = false;
  watchlistSearchError = '';
  showCreateWatchlist = false;
  newWatchlistName = '';
  showRenameWatchlist = false;
  renameWatchlistName = '';
  watchlistAddingInstrumentId: number | null = null;
  watchlistRemovingInstrumentId: number | null = null;
  activityOrders: OrderResponse[] = [];
  activityPage = 0;
  activityHasMore = false;
  activityLoading = false;
  activityLoaded = false;
  activityError = '';
  expandedOrderId: number | null = null;
  lastUpdated: Date | null = null;
  chartHasHistory = true;
  marketNews: MarketNews | null = null;
  marketMovers: MarketMovers | null = null;
  marketContextLoaded = false;
  newsExpanded = false;
  readonly newsSearchUrl = 'https://news.google.com/search?q=stock%20market&hl=en-US&gl=US&ceid=US:en';
  changedSymbols = new Set<string>();
  totalValueChanged = false;
  private highlightTimer: ReturnType<typeof setTimeout> | null = null;

  orderForm: PlaceOrderRequest = {
    instrument_id: 0,
    order_action: 'BUY',
    order_type: 'MARKET',
    quantity: null,
    limit_price: null,
    time_in_force: 'DAY'
  };

  ticketInstrument: TicketInstrument | null = null;
  ticketSearchQuery = '';
  ticketSearchResults: Instrument[] = [];
  ticketSearchLoading = false;
  preview: OrderPreview | null = null;
  previewLoading = false;
  previewError = '';

  orderSubmitInProgress = false;
  orderSubmitMessage = '';
  orderSubmitError = '';
  orderResult: OrderResult | null = null;

  // Every way a panel opens or closes (its own buttons, opening the other
  // panel, picking an instrument from search...) goes through these setters,
  // so the walkthrough always hears about it.
  private tradePanelOpenState = false;
  private cashPanelOpenState = false;

  get tradePanelOpen(): boolean {
    return this.tradePanelOpenState;
  }

  set tradePanelOpen(open: boolean) {
    if (open === this.tradePanelOpenState) {
      return;
    }
    this.tradePanelOpenState = open;
    this.onTourEvent(open ? 'tradePanelOpened' : 'tradePanelClosed');
  }

  get cashPanelOpen(): boolean {
    return this.cashPanelOpenState;
  }

  set cashPanelOpen(open: boolean) {
    if (open === this.cashPanelOpenState) {
      return;
    }
    this.cashPanelOpenState = open;
    this.onTourEvent(open ? 'cashPanelOpened' : 'cashPanelClosed');
  }

  cashDirection: CashDirection = 'DEPOSIT';
  cashAmount: number | null = null;
  cashSubmitInProgress = false;
  cashResult: OrderResult | null = null;
  readonly quickDepositAmounts = [100, 1000, 10000];

  // First-time walkthrough. null when it isn't showing; 0 is the welcome popup.
  readonly tourSteps = WALKTHROUGH_STEPS;
  tourStepIndex: number | null = null;

  readonly rangeOptions: RangeKey[] = ['1D', '1W', '1M', '3M', '1Y', 'ALL'];
  private destroy$ = new Subject<void>();
  private searchSubject$ = new Subject<string>();
  private watchlistSearch$ = new Subject<string>();
  private holdingsSearch$ = new Subject<string>();
  private ticketSearch$ = new Subject<string>();
  private previewRequest$ = new Subject<void>();
  private holdingsSearchResults: DashboardHolding[] | null = null;
  private lastHoldingsFilterQuery = '';
  holdingsSearchLoading = false;
  holdingsSearchError = '';

  constructor(
    private authService: AuthService,
    private router: Router,
    private instrumentService: InstrumentService,
    private dialog: MatDialog,
    private portfolioService: PortfolioService,
    private marketService: MarketService,
    private watchlistService: WatchlistService,
    private tradeService: TradeService,
    private walkthroughService: WalkthroughService
  ) {}

  get displayName(): string {
    const preferredName = this.currentUser?.firstName?.trim();
    const lastName = this.currentUser?.lastName?.trim();
    if (preferredName && lastName) {
      return `${preferredName} ${lastName}`;
    }
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
    if (this.marketStatus?.open && this.marketOverride?.enabled) {
      return 'MARKET OPEN (OVERRIDE)';
    }
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

  get activeWatchlist(): WatchlistResponse | null {
    return this.watchlists.find((watchlist) => watchlist.watchlistId === this.activeWatchlistId) ?? null;
  }

  get watchlistSearchMatches(): Instrument[] {
    const watchedIds = new Set(this.watchlistInstruments.map((instrument) => instrument.instrumentId));
    return this.watchlistSearchResults.filter((instrument) => !watchedIds.has(Number(instrument.instrumentId)));
  }

  getWatchlistQuote(symbol: string): QuoteResponse | null {
    return this.watchlistQuotes[symbol] ?? null;
  }

  get filteredHoldings(): DashboardHolding[] {
    const base = this.holdingsSearchResults ?? this.filterByQuery(this.holdings, this.holdingsFilterQuery);
    const scoped = this.selectedTab === 'crypto'
      ? base.filter((holding) => holding.instrumentType === 'CRYPTO')
      : this.holdingsFilterType === 'ALL'
        ? base
        : base.filter((holding) => holding.instrumentType === this.holdingsFilterType);

    return [...scoped].sort((left, right) => this.getHoldingMarketValue(right) - this.getHoldingMarketValue(left));
  }

  /** True only when the latest preview matches what's on the ticket and the backend would accept it. */
  get canSubmitOrder(): boolean {
    const preview = this.preview;
    return !!this.ticketInstrument
      && !!preview
      && preview.can_submit
      && !this.previewLoading
      && !this.orderSubmitInProgress
      && preview.instrument_id === this.ticketInstrument.instrumentId
      && preview.order_action === this.orderForm.order_action
      && Number(preview.quantity) === Number(this.orderForm.quantity)
      && (this.orderForm.order_type === 'MARKET'
        || (this.orderForm.limit_price !== null && this.orderForm.limit_price > 0));
  }

  get submitButtonLabel(): string {
    if (this.orderSubmitInProgress) {
      return 'Placing order...';
    }
    const verb = this.orderForm.order_action === 'BUY' ? 'Buy' : 'Sell';
    if (!this.ticketInstrument || !this.orderForm.quantity || this.orderForm.quantity <= 0) {
      return verb;
    }
    return `${verb} ${this.orderForm.quantity} ${this.ticketInstrument.symbol}`;
  }

  /** Show the preview's blocking reason, except the "enter a quantity" prompt before anything is typed. */
  get ticketWarning(): string {
    if (!this.ticketInstrument || !this.preview || this.preview.can_submit || this.previewLoading) {
      if (this.orderForm.order_type === 'LIMIT'
          && this.orderForm.quantity !== null
          && (!this.orderForm.limit_price || this.orderForm.limit_price <= 0)) {
        return 'Enter a positive limit price.';
      }
      return '';
    }
    const noQuantityYet = this.orderForm.quantity === null || this.orderForm.quantity === undefined;
    const message = noQuantityYet && this.preview.tradable ? '' : this.preview.message ?? '';
    // Don't repeat a reason the order result card is already showing
    return message && this.orderResult?.detail.startsWith(message) ? '' : message;
  }

  get holdingsEmptyMessage(): string {
    if (this.holdings.length === 0) {
      return "You don't have any investments yet. Use Trade to buy your first one.";
    }
    if (this.selectedTab === 'crypto' && this.cryptoHoldings.length === 0) {
      return "You don't hold any crypto yet.";
    }
    return 'No holdings match your search.';
  }

  get visibleNews(): NewsItem[] {
    const items = this.marketNews?.items ?? [];
    return this.newsExpanded ? items : items.slice(0, NEWS_COLLAPSED_COUNT);
  }

  get hasMoreNews(): boolean {
    return (this.marketNews?.items.length ?? 0) > NEWS_COLLAPSED_COUNT;
  }

  toggleNews(): void {
    this.newsExpanded = !this.newsExpanded;
  }

  /** "12m ago", "3h ago", or a date for anything older than a day. */
  newsAge(publishedAt: string | null): string {
    if (!publishedAt) {
      return '';
    }
    const minutes = Math.max(0, Math.round((Date.now() - new Date(publishedAt).getTime()) / 60000));
    if (minutes < 60) {
      return `${minutes}m ago`;
    }
    const hours = Math.round(minutes / 60);
    if (hours < 24) {
      return `${hours}h ago`;
    }
    return new Date(publishedAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }

  /** Movers that are offered on the platform open the trade ticket; others are information only. */
  openMover(mover: MarketMover): void {
    if (mover.instrumentId === null) {
      return;
    }
    const instrument = this.allInstruments.find((candidate) => Number(candidate.instrumentId) === mover.instrumentId);
    this.setTicketInstrument({
      instrumentId: mover.instrumentId,
      symbol: mover.symbol,
      name: instrument?.name ?? mover.name
    });
  }

  /** Plain-language reason the cash form can't be submitted, or '' if it can. */
  get cashAmountIssue(): string {
    const amount = this.cashAmount;
    if (amount === null || amount === undefined) {
      return '';
    }
    if (!(amount > 0)) {
      return 'Enter an amount greater than zero.';
    }
    if (Math.round(amount * 100) !== amount * 100) {
      return "Amounts can't have more than 2 decimal places.";
    }
    if (amount > MAX_CASH_TRANSFER_USD) {
      return `The most you can move at once is ${this.formatUsd(MAX_CASH_TRANSFER_USD)}.`;
    }
    if (this.cashDirection === 'WITHDRAW' && amount > this.cashBalance) {
      return `You can't withdraw more than your cash balance of ${this.formatUsd(this.cashBalance)}.`;
    }
    return '';
  }

  get canSubmitCash(): boolean {
    return !!this.cashAmount && this.cashAmount > 0 && !this.cashAmountIssue && !this.cashSubmitInProgress;
  }

  get cashBalanceAfter(): number | null {
    if (!this.cashAmount || this.cashAmount <= 0) {
      return null;
    }
    return this.cashDirection === 'DEPOSIT' ? this.cashBalance + this.cashAmount : this.cashBalance - this.cashAmount;
  }

  get cashSubmitLabel(): string {
    if (this.cashSubmitInProgress) {
      return this.cashDirection === 'DEPOSIT' ? 'Depositing...' : 'Withdrawing...';
    }
    const verb = this.cashDirection === 'DEPOSIT' ? 'Deposit' : 'Withdraw';
    return this.cashAmount && this.cashAmount > 0 && !this.cashAmountIssue ? `${verb} ${this.formatUsd(this.cashAmount)}` : verb;
  }

  get hasSearchResults(): boolean {
    return this.searchQuery.trim().length > 0 && this.searchResults.length > 0;
  }

  get accountCards(): SummaryCard[] {
    const listedChange = this.getCategoryChangePercent('non-crypto');
    const cryptoChange = this.getCategoryChangePercent('crypto');
    return [
      {
        title: 'Brokerage Account',
        subtitle: 'Cash plus listed holdings',
        value: this.brokerageValue,
        changePercent: listedChange,
        tone: this.getTone(listedChange),
        sparkline: this.brokerageSparkline
      },
      {
        title: 'Crypto Wallet',
        subtitle: 'Digital asset exposure',
        value: this.cryptoHoldingsValue,
        changePercent: cryptoChange,
        tone: this.getTone(cryptoChange),
        // No crypto held over the period: nothing meaningful to draw
        sparkline: this.cryptoHoldings.length > 0 ? this.cryptoSparkline : null
      }
    ];
  }

  ngOnInit(): void {
    this.authService.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe((user) => {
        this.currentUser = user;
        // The override endpoint is admin-only; customers would only get a
        // 403. The role may arrive after init (e.g. from /auth/me), so check
        // on every emission but load once.
        if (user?.role === 'ADMIN' && !this.marketOverrideRequested) {
          this.marketOverrideRequested = true;
          this.loadMarketOverride();
        }
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

    this.watchlistSearch$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((query) => {
          if (!query.trim()) {
            this.watchlistSearchLoading = false;
            this.watchlistSearchError = '';
            return of<Instrument[]>([]);
          }

          this.watchlistSearchLoading = true;
          this.watchlistSearchError = '';
          return this.instrumentService.searchInstruments(query).pipe(
            catchError(() => {
              this.watchlistSearchError = 'Search is unavailable right now. Try again in a moment.';
              return of<Instrument[]>([]);
            }),
            finalize(() => {
              this.watchlistSearchLoading = false;
            })
          );
        }),
        takeUntil(this.destroy$)
      )
      .subscribe((results) => {
        this.watchlistSearchResults = results.filter((instrument) => instrument.isActive).slice(0, 6);
      });

    this.ticketSearch$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((query) => {
          const normalizedQuery = query.trim();
          if (!normalizedQuery) {
            this.ticketSearchLoading = false;
            return of<Instrument[]>([]);
          }

          this.ticketSearchLoading = true;
          return this.instrumentService.searchInstruments(normalizedQuery).pipe(
            catchError(() => of<Instrument[]>([])),
            finalize(() => {
              this.ticketSearchLoading = false;
            })
          );
        }),
        takeUntil(this.destroy$)
      )
      .subscribe((results) => {
        this.ticketSearchResults = results;
      });

    this.holdingsSearch$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((query) => {
          const normalized = query.trim();
          const portfolioId = this.portfolio?.portfolioId;

          if (!normalized || !portfolioId) {
            this.holdingsSearchLoading = false;
            return of<DashboardHolding[] | null>(null);
          }

          this.holdingsSearchLoading = true;
          this.holdingsSearchError = '';
          return forkJoin({
            rows: this.portfolioService.searchHoldingsBySymbol(portfolioId, normalized),
            minimumDisplayTime: timer(150)
          }).pipe(
            map(({ rows }) => this.toDashboardHoldingRows(rows)),
            tap(() => {
              this.holdingsSearchError = '';
            }),
            catchError(() => {
              this.holdingsSearchError = 'Could not refresh holdings search. Showing the previous results.';
              return of(this.holdingsSearchResults);
            }),
            finalize(() => {
              this.holdingsSearchLoading = false;
            })
          );
        }),
        takeUntil(this.destroy$)
      )
      .subscribe((rows: DashboardHolding[] | null) => {
        this.holdingsSearchResults = rows;
      });

    // switchMap drops out-of-date previews if the client keeps typing
    this.previewRequest$
      .pipe(
        debounceTime(100),
        switchMap(() => {
          if (!this.ticketInstrument) {
            this.preview = null;
            this.previewLoading = false;
            return of<OrderPreview | null>(null);
          }

          this.previewLoading = true;
          this.previewError = '';

          return this.tradeService.previewOrder(
            this.ticketInstrument.instrumentId,
            this.orderForm.order_action,
            this.orderForm.quantity
          ).pipe(
            catchError((error: HttpErrorResponse) => {
              this.previewError = error.error?.message || 'Unable to load the latest trade preview.';
              return of<OrderPreview | null>(null);
            }),
            finalize(() => {
              this.previewLoading = false;
            })
          );
        }),
        takeUntil(this.destroy$)
      )
      .subscribe((preview) => {
        this.preview = preview;
      });

    this.loadDashboard();
    this.startLiveUpdates();
    this.startMarketContextUpdates();

    if (this.walkthroughService.isPending()) {
      this.startTour();
    }

    this.startLiveUpdates();
  }

  openInstrumentDialog(instrument: Instrument): void {
    this.searchResults = [];

    const activeDialog = this.dialog.getDialogById('instrument-expanded-dialog');
    if (activeDialog) {
      activeDialog.close();
    }

    this.dialog.open(InstrumentExpandedComponent, {
      id: 'instrument-expanded-dialog',
      data: {
        instrumentId: Number(instrument.instrumentId),
        symbol: instrument.symbol,
        isActive: instrument.isActive,
        name: instrument.name,
        instrumentType: instrument.instrumentType,
        priceCurrencyCode: instrument.priceCurrencyCode,
        isTradeable: instrument.isTradeable
      },
      width: '80vw',
      height: '80vh',
      maxWidth: '95vw',
      autoFocus: false,
      restoreFocus: true
    }).afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((result: InstrumentTradeAction | undefined) => {
        if (!result) {
          return;
        }

        this.orderForm.order_action = result.action;
        this.setTicketInstrument({
          instrumentId: Number(result.instrument.instrumentId),
          symbol: result.instrument.symbol,
          name: result.instrument.name
        });
      });

    if (this.walkthroughService.isPending()) {
      this.startTour();
    }
  }

  ngOnDestroy(): void {
    if (this.highlightTimer) {
      clearTimeout(this.highlightTimer);
    }
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSearchInput(): void {
    this.searchSubject$.next(this.searchQuery);
  }

  onWatchlistSearchInput(): void {
    this.watchlistSearch$.next(this.watchlistSearchQuery);
  }

  onHoldingsFilterInput(): void {
    const query = this.holdingsFilterQuery.trim();
    if (!query) {
      this.holdingsSearchResults = null;
      this.lastHoldingsFilterQuery = '';
      this.holdingsSearchLoading = false;
      this.holdingsSearchError = '';
      this.holdingsSearch$.next('');
      return;
    }

    if (this.holdingsSearchResults === null) {
      this.holdingsSearchResults = this.filterByQuery(this.holdings, this.lastHoldingsFilterQuery);
    }
    this.lastHoldingsFilterQuery = query;
    this.holdingsSearch$.next(query);
  }

  setHoldingsFilterType(type: string): void {
    this.holdingsFilterType = type;
  }

  setSelectedTab(tab: DashboardTab): void {
    this.selectedTab = tab;
    if (tab === 'activity' && !this.activityLoaded) {
      this.loadActivity(true);
    }
  }

  selectWatchlist(watchlistId: number | string): void {
    const selectedId = Number(watchlistId);
    const selected = this.watchlists.find((watchlist) => watchlist.watchlistId === selectedId);
    if (!selected || selected.watchlistId === this.activeWatchlistId) {
      return;
    }

    this.showRenameWatchlist = false;
    this.watchlistError = '';
    this.loadWatchlistInstruments(selected.watchlistId, selected.watchlistName);
  }

  toggleCreateWatchlist(): void {
    this.showCreateWatchlist = !this.showCreateWatchlist;
    this.showRenameWatchlist = false;
    this.newWatchlistName = '';
    this.watchlistError = '';
  }

  createWatchlist(): void {
    const name = this.newWatchlistName.trim();
    if (!name || this.watchlistSaving) {
      return;
    }

    this.watchlistSaving = true;
    this.watchlistError = '';
    this.watchlistService.createWatchlist({ watchlistName: name })
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.watchlistSaving = false;
        })
      )
      .subscribe({
        next: (watchlist) => {
          this.watchlists = [...this.watchlists, watchlist];
          this.showCreateWatchlist = false;
          this.newWatchlistName = '';
          this.loadWatchlistInstruments(watchlist.watchlistId, watchlist.watchlistName);
        },
        error: () => {
          this.watchlistError = 'Could not create that watchlist. Please try again.';
        }
      });
  }

  startRenameWatchlist(): void {
    if (!this.activeWatchlist) {
      return;
    }

    this.showCreateWatchlist = false;
    this.showRenameWatchlist = true;
    this.renameWatchlistName = this.activeWatchlist.watchlistName;
    this.watchlistError = '';
  }

  renameWatchlist(): void {
    const active = this.activeWatchlist;
    const name = this.renameWatchlistName.trim();
    if (!active || !name || this.watchlistSaving) {
      return;
    }

    this.watchlistSaving = true;
    this.watchlistError = '';
    this.watchlistService.renameWatchlist(active.watchlistId, { watchlistName: name })
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.watchlistSaving = false;
        })
      )
      .subscribe({
        next: (watchlist) => {
          this.watchlists = this.watchlists.map((item) => item.watchlistId === watchlist.watchlistId ? watchlist : item);
          this.activeWatchlistName = watchlist.watchlistName;
          this.showRenameWatchlist = false;
        },
        error: () => {
          this.watchlistError = 'Could not rename that watchlist. Please try again.';
        }
      });
  }

  deleteActiveWatchlist(): void {
    const active = this.activeWatchlist;
    if (!active || this.watchlistSaving || !window.confirm(`Delete ${active.watchlistName}?`)) {
      return;
    }

    this.watchlistSaving = true;
    this.watchlistError = '';
    this.watchlistService.deleteWatchlist(active.watchlistId)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.watchlistSaving = false;
        })
      )
      .subscribe({
        next: () => {
          this.watchlists = this.watchlists.filter((item) => item.watchlistId !== active.watchlistId);
          const nextWatchlist = this.watchlists[0];
          if (nextWatchlist) {
            this.loadWatchlistInstruments(nextWatchlist.watchlistId, nextWatchlist.watchlistName);
          } else {
            this.activeWatchlistId = null;
            this.activeWatchlistName = 'Watchlist';
            this.watchlistInstruments = [];
            this.watchlistQuotes = {};
          }
        },
        error: () => {
          this.watchlistError = 'Could not delete that watchlist. Please try again.';
        }
      });
  }

  addWatchlistInstrument(instrument: Instrument): void {
    if (!this.activeWatchlistId || this.watchlistAddingInstrumentId !== null) {
      return;
    }

    const watchlistId = this.activeWatchlistId;
    const instrumentId = Number(instrument.instrumentId);
    this.watchlistAddingInstrumentId = instrumentId;
    this.watchlistError = '';
    this.watchlistService.addInstrument(watchlistId, instrumentId)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.watchlistAddingInstrumentId = null;
        })
      )
      .subscribe({
        next: (addedInstrument) => {
          // The user may have switched lists; the add still succeeded on the
          // original list, which reloads from the server when reselected.
          if (this.activeWatchlistId !== watchlistId) {
            return;
          }
          this.watchlistInstruments = [...this.watchlistInstruments, addedInstrument];
          this.watchlistSearchQuery = '';
          this.watchlistSearchResults = [];
          this.watchlistSearchError = '';
          this.refreshWatchlistQuotes();
        },
        error: () => {
          if (this.activeWatchlistId !== watchlistId) {
            return;
          }
          this.watchlistError = `${instrument.symbol} could not be added. It may already be on this list.`;
        }
      });
  }

  removeWatchlistInstrument(instrument: WatchlistInstrumentResponse): void {
    if (!this.activeWatchlistId || this.watchlistRemovingInstrumentId !== null) {
      return;
    }

    const watchlistId = this.activeWatchlistId;
    this.watchlistRemovingInstrumentId = instrument.instrumentId;
    this.watchlistError = '';
    this.watchlistService.removeInstrument(watchlistId, instrument.instrumentId)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.watchlistRemovingInstrumentId = null;
        })
      )
      .subscribe({
        next: () => {
          if (this.activeWatchlistId !== watchlistId) {
            return;
          }
          this.watchlistInstruments = this.watchlistInstruments.filter((item) => item.instrumentId !== instrument.instrumentId);
          this.refreshWatchlistQuotes();
        },
        error: () => {
          if (this.activeWatchlistId !== watchlistId) {
            return;
          }
          this.watchlistError = `${instrument.symbol} could not be removed. Please try again.`;
        }
      });
  }

  loadMoreActivity(): void {
    this.loadActivity(false);
  }

  toggleOrderDetails(orderId: number): void {
    this.expandedOrderId = this.expandedOrderId === orderId ? null : orderId;
  }

  orderStatusLabel(status: string): string {
    switch (status) {
      case 'FILLED': return 'Filled';
      case 'REJECTED': return 'Rejected';
      case 'CANCELLED': return 'Cancelled';
      case 'EXPIRED': return 'Expired';
      case 'SUBMITTED': return 'Submitted';
      case 'ACCEPTED': return 'Accepted';
      default: return status.charAt(0) + status.slice(1).toLowerCase();
    }
  }

  setActiveRange(range: RangeKey): void {
    if (this.activeRange === range) {
      return;
    }

    this.activeRange = range;
    this.loadPortfolioHistory();
  }

  setOrderType(orderType: 'MARKET' | 'LIMIT'): void {
    this.orderForm.order_type = orderType;
    this.orderForm.limit_price = orderType === 'MARKET' ? null : this.orderForm.limit_price;
    if (orderType === 'MARKET') {
      this.orderForm.time_in_force = 'DAY';
    }
    this.requestPreview();
  }

  setTimeInForce(timeInForce: 'DAY' | 'GTC'): void {
    this.orderForm.time_in_force = timeInForce;
  }

  selectInstrument(instrument: Instrument): void {
    this.searchQuery = '';
    this.searchResults = [];
    this.setTicketInstrument({
      instrumentId: Number(instrument.instrumentId),
      symbol: instrument.symbol,
      name: instrument.name
    });
  }

  setTradeAction(action: OrderAction): void {
    this.orderForm.order_action = action;
    this.orderSubmitError = '';
    this.orderSubmitMessage = '';
    this.cashPanelOpen = false;
    this.tradePanelOpen = true;
    this.requestPreview();
  }

  openTradePanel(): void {
    this.orderSubmitError = '';
    this.orderSubmitMessage = '';
    this.cashPanelOpen = false;
    this.tradePanelOpen = true;
  }

  openCashPanel(): void {
    this.tradePanelOpen = false;
    this.cashPanelOpen = true;
  }

  closeCashPanel(): void {
    this.cashPanelOpen = false;
  }

  get tourStep(): WalkthroughStep | null {
    return this.tourStepIndex === null ? null : this.tourSteps[this.tourStepIndex];
  }

  /** Turn the after-hours override on or off, keeping the menu open so the switch visibly flips. */
  toggleMarketOverride(event: Event): void {
    event.stopPropagation();
    if (!this.marketOverride?.allowed || this.marketOverrideSaving) {
      return;
    }
    this.marketOverrideSaving = true;
    this.marketService.setMarketOverride(!this.marketOverride.enabled)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.marketOverrideSaving = false;
        })
      )
      .subscribe({
        next: (status) => {
          this.marketOverride = status;
          this.refreshMarketStatus();
          // The ticket's open/closed state and price may have changed
          if (this.tradePanelOpen && this.ticketInstrument) {
            this.requestPreview();
          }
        },
        error: () => {
          // Leave the switch as it was; the server didn't change anything
        }
      });
  }

  private loadMarketOverride(): void {
    this.marketService.getMarketOverride()
      .pipe(
        catchError(() => of<MarketOverrideStatus | null>(null)),
        takeUntil(this.destroy$)
      )
      .subscribe((status) => {
        this.marketOverride = status;
      });
  }

  private refreshMarketStatus(): void {
    this.marketService.getMarketStatus('NASDAQ')
      .pipe(
        catchError(() => of<MarketStatusResponse | null>(null)),
        takeUntil(this.destroy$)
      )
      .subscribe((status) => {
        if (status) {
          this.marketStatus = status;
        }
      });
  }

  startTour(): void {
    // Set the step first: the welcome step ignores the "panel closed" events below.
    this.tourStepIndex = 0;
    this.tradePanelOpen = false;
    this.cashPanelOpen = false;
  }

  advanceTour(): void {
    if (this.tourStepIndex === null) {
      return;
    }
    if (this.tourStepIndex >= this.tourSteps.length - 1) {
      this.endTour();
      return;
    }
    this.goToTourStep(this.tourStepIndex + 1);
  }

  endTour(): void {
    this.tourStepIndex = null;
    this.walkthroughService.clearPending();
  }

  // Called whenever something happens that a tour step may be waiting for:
  // panels opening/closing (from the setters above), a deposit or an order.
  onTourEvent(event: TourEvent): void {
    const step = this.tourStep;
    if (!step || this.tourStepIndex === null) {
      return;
    }
    if (step.advanceOn === event) {
      this.advanceTour();
    } else if (step.backOn === event) {
      this.goToTourStep(this.tourStepIndex - 1);
    }
  }

  private goToTourStep(index: number): void {
    this.tourStepIndex = index;
    if (this.tourSteps[index].id === 'holdings') {
      this.setSelectedTab('holdings');
    }
  }

  setCashDirection(direction: CashDirection): void {
    this.cashDirection = direction;
    this.cashResult = null;
  }

  setCashAmount(amount: number): void {
    this.cashAmount = amount;
  }

  withdrawAll(): void {
    this.cashAmount = Math.floor(this.cashBalance * 100) / 100;
  }

  dismissCashResult(): void {
    this.cashResult = null;
  }

  submitCash(): void {
    if (!this.canSubmitCash || this.cashAmount === null) {
      return;
    }

    const direction = this.cashDirection;
    this.cashResult = null;
    this.cashSubmitInProgress = true;

    this.portfolioService.transferCash(direction, this.cashAmount)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.cashSubmitInProgress = false;
        })
      )
      .subscribe({
        next: (transfer: CashTransferResponse) => {
          const deposited = transfer.transactionType === 'DEPOSIT';
          this.cashResult = {
            kind: 'filled',
            title: `${deposited ? 'Deposited' : 'Withdrew'} ${this.formatUsd(Math.abs(transfer.amountUsd))}`,
            detail: `Your cash balance is now ${this.formatUsd(transfer.balanceAfterUsd)}.`
          };
          this.cashAmount = null;
          this.refreshPortfolioSlice();
          if (this.ticketInstrument) {
            this.requestPreview();
          }
          if (deposited) {
            this.onTourEvent('depositCompleted');
          }
        },
        error: (error: HttpErrorResponse) => {
          this.cashResult = error.status === 0
            ? {
                kind: 'error',
                title: "We couldn't reach the server",
                detail: 'Check your connection and look at your cash balance before trying again.'
              }
            : {
                kind: 'rejected',
                title: direction === 'DEPOSIT' ? 'Deposit not completed' : 'Withdrawal not completed',
                detail: (error.error as { message?: string } | null)?.message || 'Something went wrong. Please try again.'
              };
        }
      });
  }

  openTradeForHolding(holding: DashboardHolding, action: OrderAction): void {
    const instrument = this.allInstruments.find((candidate) => candidate.symbol === holding.instrumentSymbol);
    const instrumentId = holding.instrumentId ?? (instrument ? Number(instrument.instrumentId) : null);
    if (!instrumentId) {
      return;
    }

    this.orderForm.order_action = action;
    this.setTicketInstrument({
      instrumentId,
      symbol: holding.instrumentSymbol,
      name: holding.instrumentName || instrument?.name || holding.instrumentSymbol
    });
  }

  toggleTradePanel(): void {
    this.tradePanelOpen = !this.tradePanelOpen;
    if (this.tradePanelOpen) {
      this.cashPanelOpen = false;
    }
  }

  onTicketSearchInput(): void {
    this.ticketSearch$.next(this.ticketSearchQuery);
  }

  pickTicketInstrument(instrument: Instrument): void {
    this.setTicketInstrument({
      instrumentId: Number(instrument.instrumentId),
      symbol: instrument.symbol,
      name: instrument.name
    });
  }

  clearTicketInstrument(): void {
    this.ticketInstrument = null;
    this.orderForm.instrument_id = 0;
    this.preview = null;
    this.previewError = '';
    this.ticketSearchQuery = '';
    this.ticketSearchResults = [];
  }

  useMaxQuantity(): void {
    const max = this.toNumber(this.preview?.max_quantity);
    if (max > 0) {
      this.orderForm.quantity = max;
      this.requestPreview();
    }
  }

  requestPreview(): void {
    this.previewRequest$.next();
  }

  submitOrder(): void {
    if (!this.canSubmitOrder || !this.ticketInstrument) {
      return;
    }

    const symbol = this.ticketInstrument.symbol;
    this.orderSubmitError = '';
    this.orderSubmitMessage = '';
    this.orderResult = null;
    this.orderSubmitInProgress = true;

    this.tradeService.placeOrder({ ...this.orderForm })
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.orderSubmitInProgress = false;
        })
      )
      .subscribe({
        next: (response) => {
          const executed = response as PlaceOrderSuccess;
          const order = executed.order ?? (response as OrderResponse);
          this.orderResult = this.describeAcceptedOrder(order, executed.trade ?? null, symbol);
          // Clear the quantity so the same order can't be sent twice by accident
          this.orderForm.quantity = null;
          this.refreshPortfolioSlice();
          this.refreshActivityIfLoaded();
          this.requestPreview();
          this.onTourEvent('orderPlaced');
        },
        error: (error: HttpErrorResponse) => {
          this.orderResult = this.describeFailedOrder(error);
          if (this.orderResult.kind !== 'error') {
            this.refreshPortfolioSlice();
            this.refreshActivityIfLoaded();
          }
          this.requestPreview();
        }
      });
  }

  dismissOrderResult(): void {
    this.orderResult = null;
  }

  private setTicketInstrument(instrument: TicketInstrument): void {
    this.ticketInstrument = instrument;
    this.orderForm.instrument_id = instrument.instrumentId;
    this.orderForm.quantity = null;
    this.ticketSearchQuery = '';
    this.ticketSearchResults = [];
    this.preview = null;
    this.orderSubmitError = '';
    this.orderSubmitMessage = '';
    this.cashPanelOpen = false;
    this.tradePanelOpen = true;
    this.requestPreview();
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

  private describeAcceptedOrder(order: OrderResponse, trade: TradeResponse | null, symbol: string): OrderResult {
    const quantity = this.formatQuantity(trade?.executed_quantity ?? order.quantity);
    if (trade && order.order_status === 'FILLED') {
      const verb = order.order_action === 'SELL' ? 'Sold' : 'Bought';
      const total = this.formatUsd(trade.total_usd_value);
      return {
        kind: 'filled',
        title: `${verb} ${quantity} ${symbol} at ${this.formatUsd(trade.execution_price)}`,
        detail: `${order.order_action === 'SELL' ? 'You received' : 'Total cost'} ${total}. Your holdings and cash are updated. Order #${order.order_id}.`
      };
    }
    return {
      kind: 'pending',
      title: 'Order received',
      detail: `Order #${order.order_id} to ${order.order_action === 'SELL' ? 'sell' : 'buy'} ${quantity} ${symbol} is waiting to be filled.`
    };
  }

  private describeFailedOrder(error: HttpErrorResponse): OrderResult {
    if (error.status === 0) {
      return {
        kind: 'error',
        title: "We couldn't reach the server",
        detail: "Check your connection and try again. If you're not sure whether your order went through, check your order history before placing it again."
      };
    }

    const apiError = error.error as TradeApiError | null;
    const reason = apiError?.message || 'Something went wrong placing your order. Please try again.';
    const order = apiError?.order;
    if (order?.order_status === 'REJECTED') {
      return {
        kind: 'rejected',
        title: 'Order not completed',
        detail: `${reason} Nothing was charged. Order #${order.order_id} is recorded as rejected.`
      };
    }
    if (order) {
      // Accepted but execution failed and rolled back
      return { kind: 'pending', title: 'Order not completed yet', detail: reason };
    }
    return { kind: 'rejected', title: 'Order not placed', detail: reason };
  }

  private formatUsd(value: number | null | undefined): string {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(this.toNumber(value));
  }

  private formatQuantity(value: number | null | undefined): string {
    return new Intl.NumberFormat('en-US', { maximumFractionDigits: 8 }).format(this.toNumber(value));
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
          this.setChart([], new Date(), new Date());
          this.chartHasHistory = false;
          return;
        }

        this.applyPortfolio(portfolio);
        this.lastUpdated = new Date();
        this.loadPortfolioHistory();
        this.refreshHoldingsFromApi();

        if (watchlists.length > 0) {
          this.loadWatchlistInstruments(watchlists[0].watchlistId, watchlists[0].watchlistName);
        } else {
          this.activeWatchlistId = null;
          this.activeWatchlistName = 'Watchlist';
          this.watchlistInstruments = [];
          this.watchlistQuotes = {};
        }
      });
  }

  private refreshPortfolioSlice(): void {
    this.portfolioService.getMyPortfolio()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (portfolio: PortfolioResponse) => {
          this.applyLivePortfolio(portfolio);
          this.refreshHoldingsFromApi();
        },
        error: () => {
          this.dashboardError = 'Portfolio refresh failed after the latest trade.';
        }
      });
  }

  /**
   * Poll while the page is visible, and refresh straight away when the client comes back to the tab.
   * Polling pauses while the tab is hidden so idle dashboards don't load the backend.
   */
  private startLiveUpdates(): void {
    const becameVisible$ = fromEvent(document, 'visibilitychange').pipe(filter(() => !document.hidden));

    merge(timer(LIVE_UPDATE_MS, LIVE_UPDATE_MS), becameVisible$)
      .pipe(
        filter(() => !document.hidden && !this.isDashboardLoading && !this.orderSubmitInProgress),
        // Skip a tick if the previous refresh is still in flight
        exhaustMap(() => this.portfolioService.getMyPortfolio().pipe(catchError(() => of<PortfolioResponse | null>(null)))),
        takeUntil(this.destroy$)
      )
      .subscribe((portfolio) => {
        if (portfolio) {
          this.applyLivePortfolio(portfolio);
        }
        if (this.activeWatchlistId !== null) {
          this.refreshWatchlistQuotes();
        }
        // Keep the first page of activity current (e.g. orders placed in another tab)
        if (this.activityLoaded && this.activityPage === 0 && this.selectedTab === 'activity') {
          this.loadActivity(true);
        }
        if (this.tradePanelOpen && this.ticketInstrument) {
          this.requestPreview();
        }
      });
  }

  /** Apply a refreshed portfolio, highlight what changed, and reload the chart only if positions changed. */
  private applyLivePortfolio(portfolio: PortfolioResponse): void {
    const previousValues = new Map(this.holdings.map((holding) => [holding.instrumentSymbol, this.getHoldingMarketValue(holding)]));
    const previousPositions = this.positionsKey();
    const previousTotal = this.totalPortfolioValue;

    this.applyPortfolio(portfolio);
    this.lastUpdated = new Date();

    const changed = new Set<string>();
    for (const holding of this.holdings) {
      const before = previousValues.get(holding.instrumentSymbol);
      if (before === undefined || Math.abs(before - this.getHoldingMarketValue(holding)) >= 0.005) {
        changed.add(holding.instrumentSymbol);
      }
    }
    const totalChanged = Math.abs(previousTotal - this.totalPortfolioValue) >= 0.005;

    if (changed.size > 0 || totalChanged) {
      this.changedSymbols = changed;
      this.totalValueChanged = totalChanged;
      if (this.highlightTimer) {
        clearTimeout(this.highlightTimer);
      }
      this.highlightTimer = setTimeout(() => {
        this.changedSymbols = new Set<string>();
        this.totalValueChanged = false;
      }, HIGHLIGHT_MS);
    }

    if (this.positionsKey() !== previousPositions) {
      this.loadPortfolioHistory();
    }
  }

  private positionsKey(): string {
    return this.holdings
      .map((holding) => `${holding.instrumentSymbol}:${holding.quantity}`)
      .sort()
      .join('|');
  }

  private startMarketContextUpdates(): void {
    timer(0, MARKET_CONTEXT_MS)
      .pipe(
        filter(() => !document.hidden),
        exhaustMap(() => forkJoin({
          news: this.marketService.getNews().pipe(catchError(() => of<MarketNews | null>(null))),
          movers: this.marketService.getMovers().pipe(catchError(() => of<MarketMovers | null>(null)))
        })),
        takeUntil(this.destroy$)
      )
      .subscribe(({ news, movers }) => {
        // Keep showing what we had if a refresh fails
        this.marketNews = news ?? this.marketNews;
        this.marketMovers = movers ?? this.marketMovers;
        this.marketContextLoaded = true;
      });
  }

  private loadActivity(reset: boolean): void {
    const page = reset ? 0 : this.activityPage + 1;
    this.activityLoading = true;
    this.activityError = '';

    this.tradeService.getOrderHistory(page)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.activityLoading = false;
        })
      )
      .subscribe({
        next: (history: OrderHistoryPage) => {
          this.activityOrders = reset ? history.orders : [...this.activityOrders, ...history.orders];
          this.activityPage = history.page;
          this.activityHasMore = history.has_more;
          this.activityLoaded = true;
        },
        error: () => {
          this.activityError = 'We couldn\'t load your order history. Please try again.';
        }
      });
  }

  private refreshActivityIfLoaded(): void {
    if (this.activityLoaded) {
      this.loadActivity(true);
    }
  }

  private loadWatchlistInstruments(watchlistId: number, watchlistName: string): void {
    // Cancel before setting state: finalize() on the old request resets loading.
    this.watchlistInstrumentsRequest?.unsubscribe();
    this.watchlistQuotesRequest?.unsubscribe();
    this.activeWatchlistId = watchlistId;
    this.activeWatchlistName = watchlistName;
    this.watchlistLoading = true;
    this.watchlistError = '';
    this.watchlistInstrumentsRequest = this.watchlistService.getWatchlistInstruments(watchlistId)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.watchlistLoading = false;
        })
      )
      .subscribe({
        next: (instruments: WatchlistInstrumentResponse[]) => {
          if (this.activeWatchlistId !== watchlistId) {
            return;
          }
          this.watchlistInstruments = instruments;
          this.refreshWatchlistQuotes();
        },
        error: () => {
          if (this.activeWatchlistId !== watchlistId) {
            return;
          }
          this.watchlistInstruments = [];
          this.watchlistQuotes = {};
          this.watchlistError = 'Could not load this watchlist. Please try again.';
        }
      });
  }

  private refreshWatchlistQuotes(): void {
    this.watchlistQuotesRequest?.unsubscribe();
    const watchlistId = this.activeWatchlistId;
    const symbols = this.watchlistInstruments.map((instrument) => instrument.symbol);
    if (symbols.length === 0) {
      this.watchlistQuotes = {};
      return;
    }

    this.watchlistQuotesLoading = true;
    this.watchlistQuotesRequest = this.marketService.getQuotes(symbols)
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => of<QuoteResponse[]>([])),
        finalize(() => {
          this.watchlistQuotesLoading = false;
        })
      )
      .subscribe((quotes) => {
        if (this.activeWatchlistId !== watchlistId) {
          return;
        }
        this.watchlistQuotes = quotes.reduce<Record<string, QuoteResponse>>((lookup, quote) => {
          lookup[quote.symbol] = quote;
          return lookup;
        }, {});
      });
  }

  private applyPortfolio(portfolio: PortfolioResponse): void {
    this.portfolio = portfolio;
    this.holdings = this.toDashboardHoldingRows(portfolio.holdings);
  }

  private toDashboardHoldingRows(rows: HoldingResponse[]): DashboardHolding[] {
    return rows.map((holding: HoldingResponse) => {
      const matchedInstrument = this.allInstruments.find((instrument) => instrument.symbol === holding.instrumentSymbol);
      return {
        ...holding,
        instrumentId: holding.instrumentId ?? (matchedInstrument ? Number(matchedInstrument.instrumentId) : null),
        instrumentType: matchedInstrument?.instrumentType ?? this.inferInstrumentType(holding.instrumentSymbol),
        todaysChangePercent: holding.gainLossPercent ?? null
      };
    });
  }

  private refreshHoldingsFromApi(): void {
    const portfolioId = this.portfolio?.portfolioId;
    if (!portfolioId) {
      this.holdings = [];
      this.holdingsSearchResults = null;
      this.holdingsSearchLoading = false;
      return;
    }

    this.portfolioService.getHoldingsByPortfolio(portfolioId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (rows) => {
          this.holdings = this.toDashboardHoldingRows(rows);
          if (this.holdingsFilterQuery.trim()) {
            this.holdingsSearch$.next(this.holdingsFilterQuery.trim());
          }
        },
        error: () => {
          // Keep currently shown holdings if the dedicated holdings endpoint is temporarily unavailable.
        }
      });
  }

  private loadPortfolioHistory(): void {
    const range = this.portfolioRanges[this.activeRange];
    const holdings = this.holdings.filter((holding) => this.toNumber(holding.quantity) > 0);

    if (holdings.length === 0) {
      this.setChart([], new Date(), new Date());
      this.chartHasHistory = false;
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
        // A line needs at least two real prices; otherwise say so rather than draw a made-up trend
        this.chartHasHistory = histories.some((history) => history.length >= 2);
        this.setChart(
          this.chartHasHistory ? this.buildChartPoints(holdings, histories, from, to, range.points) : [],
          from,
          to
        );
      });
  }

  /** Store the samples and derive everything drawn from them once, not on every change detection. */
  private setChart(points: ChartPoint[], from: Date, to: Date): void {
    this.chartPoints = points;
    if (points.length === 0) {
      this.chartLinePath = '';
      this.chartAreaPath = '';
      this.chartXTicks = [];
      this.chartYTicks = [];
      this.brokerageSparkline = null;
      this.cryptoSparkline = null;
      return;
    }

    const values = points.map((point) => point.value);
    const ticks = this.niceTicks(Math.min(...values), Math.max(...values));
    this.chartDomain = { min: ticks[0], max: ticks[ticks.length - 1] };
    const step = ticks.length > 1 ? ticks[1] - ticks[0] : 1;
    this.chartYTicks = ticks.map((tick) => ({
      position: this.chartY(tick) / CHART_HEIGHT,
      label: this.formatAxisCurrency(tick, step)
    }));
    this.chartXTicks = this.buildTimeTicks(from, to);
    this.chartLinePath = this.buildLinePath(values);
    this.chartAreaPath = this.chartLinePath
      ? `${this.chartLinePath} L ${CHART_WIDTH} ${CHART_HEIGHT} L 0 ${CHART_HEIGHT} Z`
      : '';
    this.brokerageSparkline = this.buildSparkline(points.map((point) => point.brokerage));
    this.cryptoSparkline = this.buildSparkline(points.map((point) => point.crypto));
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

    // Sort each history once; sample times only move forward, so each is walked with a cursor
    const series = holdings.map((holding, index) => {
      const sorted = [...histories[index]].sort((left, right) => {
        return new Date(left.timestamp).getTime() - new Date(right.timestamp).getTime();
      });
      // History prices arrive in USD (converted on the server)
      const history = sorted.map((point) => ({ time: new Date(point.timestamp).getTime(), price: this.toNumber(point.price) }));
      const currentPriceUsd = this.toNumber(holding.currentPriceUsd || holding.averageCostUsd);
      return {
        history,
        quantity: this.toNumber(holding.quantity),
        isCrypto: holding.instrumentType === 'CRYPTO',
        // Before the first known price, carry the current price back
        price: currentPriceUsd,
        cursor: 0
      };
    });

    return Array.from({ length: pointCount }, (_, index) => {
      const time = fromMs + step * index;
      let listed = 0;
      let crypto = 0;
      for (const holding of series) {
        while (holding.cursor < holding.history.length && holding.history[holding.cursor].time <= time) {
          holding.price = holding.history[holding.cursor].price || holding.price;
          holding.cursor++;
        }
        const value = holding.quantity * holding.price;
        if (holding.isCrypto) {
          crypto += value;
        } else {
          listed += value;
        }
      }
      return {
        time,
        value: this.cashBalance + listed + crypto,
        brokerage: this.cashBalance + listed,
        crypto
      };
    });
  }

  /**
   * Round axis values that cover [min, max] in about four steps of 1, 2, 2.5 or 5 x 10^n,
   * e.g. 9,990 / 10,000 / 10,010. A flat series gets a small band around its value.
   */
  private niceTicks(min: number, max: number, targetSteps = 4): number[] {
    if (max - min < 1e-9) {
      const pad = Math.max(Math.abs(max) * 0.001, 1);
      min -= pad;
      max += pad;
    }
    const rawStep = (max - min) / targetSteps;
    const magnitude = 10 ** Math.floor(Math.log10(rawStep));
    const step = [1, 2, 2.5, 5, 10].map((multiple) => multiple * magnitude).find((candidate) => candidate >= rawStep) ?? 10 * magnitude;
    const decimals = Math.max(0, -Math.floor(Math.log10(step)) + 1);
    const first = Math.floor(min / step) * step;
    const ticks: number[] = [];
    for (let tick = first; tick < max + step - 1e-9; tick += step) {
      ticks.push(Number(tick.toFixed(decimals)));
    }
    return ticks;
  }

  /** Dollar axis label with just enough precision to tell neighbouring ticks apart. */
  private formatAxisCurrency(value: number, step: number): string {
    const compact = Math.abs(value) >= 100_000;
    const unit = !compact ? 1 : Math.abs(value) >= 1e9 ? 1e9 : Math.abs(value) >= 1e6 ? 1e6 : 1e3;
    const digits = Math.min(2, Math.max(0, Math.ceil(-Math.log10(step / unit) - 1e-9)));
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      notation: compact ? 'compact' : 'standard',
      minimumFractionDigits: digits,
      maximumFractionDigits: digits
    }).format(value);
  }

  /**
   * X-axis labels at calendar boundaries rather than at every sample, the way broker charts do:
   * hours for 1D, days for 1W, Mondays for 1M, month starts for 3M/1Y and quarters for All.
   * January shows the year. Labels too close to the edges are dropped so they aren't clipped.
   */
  private buildTimeTicks(from: Date, to: Date): AxisTick[] {
    const fromMs = from.getTime();
    const span = to.getTime() - fromMs;
    if (span <= 0) {
      return [];
    }

    const cursor = new Date(from);
    const monthLabel = (date: Date) => date.getMonth() === 0
      ? String(date.getFullYear())
      : date.toLocaleDateString([], { month: 'short' });
    let advance: (date: Date) => void;
    let label: (date: Date) => string;

    switch (this.activeRange) {
      case '1D':
        cursor.setMinutes(0, 0, 0);
        cursor.setHours(cursor.getHours() + 3 - (cursor.getHours() % 3));
        advance = (date) => date.setHours(date.getHours() + 3);
        label = (date) => date.getHours() === 0
          ? date.toLocaleDateString([], { month: 'short', day: 'numeric' })
          : date.toLocaleTimeString([], { hour: 'numeric' });
        break;
      case '1W':
        cursor.setHours(24, 0, 0, 0);
        advance = (date) => date.setDate(date.getDate() + 1);
        label = (date) => `${date.toLocaleDateString([], { weekday: 'short' })} ${date.getDate()}`;
        break;
      case '1M':
        cursor.setHours(24, 0, 0, 0);
        while (cursor.getDay() !== 1) {
          cursor.setDate(cursor.getDate() + 1);
        }
        advance = (date) => date.setDate(date.getDate() + 7);
        label = (date) => date.toLocaleDateString([], { month: 'short', day: 'numeric' });
        break;
      default: {
        // 3M and 1Y: every month start; All: every quarter start
        const monthsPerTick = this.activeRange === 'ALL' ? 3 : 1;
        cursor.setHours(0, 0, 0, 0);
        cursor.setDate(1);
        cursor.setMonth(cursor.getMonth() + 1);
        while (cursor.getMonth() % monthsPerTick !== 0) {
          cursor.setMonth(cursor.getMonth() + 1);
        }
        advance = (date) => date.setMonth(date.getMonth() + monthsPerTick);
        label = monthLabel;
      }
    }

    const ticks: AxisTick[] = [];
    while (cursor.getTime() <= to.getTime()) {
      const position = (cursor.getTime() - fromMs) / span;
      if (position >= 0.03 && position <= 0.97) {
        ticks.push({ position, label: label(cursor) });
      }
      advance(cursor);
    }
    return ticks;
  }

  /** SVG y for a value, on the same scale as the y-axis ticks. */
  private chartY(value: number): number {
    const { min, max } = this.chartDomain;
    const fraction = max > min ? (max - value) / (max - min) : 0.5;
    return CHART_INSET + fraction * (CHART_HEIGHT - 2 * CHART_INSET);
  }

  private buildLinePath(values: number[]): string {
    if (values.length === 0) {
      return '';
    }
    return values.map((value, index) => {
      const x = values.length === 1 ? CHART_WIDTH / 2 : index / (values.length - 1) * CHART_WIDTH;
      return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${this.chartY(value).toFixed(2)}`;
    }).join(' ');
  }

  /** A small trend line for an account card, scaled to its own range. A flat series draws mid-height. */
  private buildSparkline(values: number[]): Sparkline | null {
    if (values.length < 2) {
      return null;
    }
    const width = 176;
    const height = 42;
    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min;
    const path = values.map((value, index) => {
      const x = index / (values.length - 1) * width;
      const y = range > 1e-9 ? height - 3 - ((value - min) / range) * (height - 6) : height / 2;
      return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
    }).join(' ');
    return { path, falling: values[values.length - 1] < values[0] };
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
