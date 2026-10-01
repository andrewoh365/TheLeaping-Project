import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subject, forkJoin, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, switchMap, takeUntil } from 'rxjs/operators';
import { AuthService, User } from '../../services/auth.service';
import { InstrumentService, Instrument } from '../../services/instrumentService';
import { MarketService, Quote, Tradability } from '../../services/market.service';
import { Watchlist, WatchlistInstrument, WatchlistService } from '../../services/watchlist.service';

interface PlaceholderHolding {
  instrument: string;
  allocation: string;
  value: string;
  change: string;
  status: 'Synced' | 'Planned';
}

interface PlaceholderOrder {
  ref: string;
  instrument: string;
  side: 'BUY' | 'SELL';
  status: 'Submitted' | 'Accepted' | 'Filled' | 'Pending Integration';
  timestamp: string;
}

interface PlaceholderInsight {
  title: string;
  metric: string;
  note: string;
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
    MatCardModule,
    MatDividerModule,
    MatListModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit, OnDestroy {
  currentUser: User | null = null;
  mobileNavOpen = false;

  searchQuery: string = '';
  searchResults: Instrument[] = [];
  selectedQuote: Quote | null = null;
  selectedTradability: Tradability | null = null;
  selectedSymbol: string | null = null;

  watchlists: Watchlist[] = [];
  activeWatchlistId: number | null = null;
  watchlistInstruments: WatchlistInstrument[] = [];
  newWatchlistName = '';

  isLoadingQuote = false;
  isLoadingPulse = false;
  isWatchlistBusy = false;
  orderStatusMessage = '';

  marketPulse: Quote[] = [];
  readonly pulseSymbols = ['AAPL', 'MSFT', 'VOD', 'RELIANCE', 'EUR/USD', 'BTC-USD'];

  quickTrade = {
    symbol: 'AAPL',
    side: 'BUY',
    quantity: 25,
    orderType: 'MARKET'
  };

  readonly placeholderHoldings: PlaceholderHolding[] = [
    { instrument: 'US Equities Basket', allocation: '32%', value: '$48,120', change: '+2.1%', status: 'Planned' },
    { instrument: 'UK Income Basket', allocation: '21%', value: 'GBP 19,440', change: '+0.6%', status: 'Planned' },
    { instrument: 'India Growth Basket', allocation: '17%', value: 'INR 1,240,000', change: '+1.9%', status: 'Planned' },
    { instrument: 'FX Pairs', allocation: '16%', value: '$22,180', change: '-0.4%', status: 'Planned' },
    { instrument: 'Crypto Exposure', allocation: '14%', value: '$15,960', change: '+3.7%', status: 'Planned' }
  ];

  readonly placeholderOrders: PlaceholderOrder[] = [
    { ref: 'ORD-84713', instrument: 'AAPL', side: 'BUY', status: 'Accepted', timestamp: 'Today 10:22' },
    { ref: 'ORD-84710', instrument: 'EUR/USD', side: 'SELL', status: 'Submitted', timestamp: 'Today 10:18' },
    { ref: 'ORD-84702', instrument: 'BTC-USD', side: 'BUY', status: 'Filled', timestamp: 'Today 09:54' },
    { ref: 'ORD-84691', instrument: 'RELIANCE', side: 'BUY', status: 'Pending Integration', timestamp: 'Today 09:30' }
  ];

  readonly placeholderInsights: PlaceholderInsight[] = [
    { title: 'Most Active Segment', metric: 'US Equities', note: 'Placeholder until BR-16 reporting pipeline is integrated.' },
    { title: 'Top Instrument (24h)', metric: 'BTC-USD', note: 'Calculated in UI placeholder mode for design continuity.' },
    { title: 'Client Activity Trend', metric: '+12%', note: 'Upcoming analytics service will replace mock trend values.' }
  ];

  private destroy$ = new Subject<void>();
  private searchSubject$ = new Subject<string>();

  constructor(
    private authService: AuthService,
    private router: Router,
    private instrumentService: InstrumentService,
    private marketService: MarketService,
    private watchlistService: WatchlistService
  ) {}

  onSearchInput(): void {
    this.searchSubject$.next(this.searchQuery);
  }

  createWatchlist(): void {
    const name = this.newWatchlistName.trim();
    if (!name) {
      return;
    }

    this.isWatchlistBusy = true;
    this.watchlistService.createWatchlist(name)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (watchlist) => {
          this.watchlists = [watchlist, ...this.watchlists];
          this.newWatchlistName = '';
          this.selectWatchlist(watchlist.watchlistId);
          this.isWatchlistBusy = false;
        },
        error: () => {
          this.isWatchlistBusy = false;
        }
      });
  }

  selectWatchlist(watchlistId: number): void {
    this.activeWatchlistId = watchlistId;
    this.watchlistService.getWatchlistInstruments(watchlistId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (instruments) => {
          this.watchlistInstruments = instruments;
        },
        error: () => {
          this.watchlistInstruments = [];
        }
      });
  }

  addToWatchlist(instrument: Instrument): void {
    if (!this.activeWatchlistId) {
      return;
    }

    this.watchlistService.addInstrument(this.activeWatchlistId, instrument.instrumentId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (added) => {
          this.watchlistInstruments = [added, ...this.watchlistInstruments];
        }
      });
  }

  loadMarketDetail(symbol: string): void {
    this.selectedSymbol = symbol;
    this.isLoadingQuote = true;

    forkJoin({
      quote: this.marketService.getQuote(symbol).pipe(catchError(() => of(null))),
      tradability: this.marketService.getTradability(symbol).pipe(catchError(() => of(null)))
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe(({ quote, tradability }) => {
        this.selectedQuote = quote;
        this.selectedTradability = tradability;
        this.isLoadingQuote = false;
      });
  }

  submitQuickTradePlaceholder(): void {
    this.orderStatusMessage = `Order workflow preview: ${this.quickTrade.side} ${this.quickTrade.quantity} ${this.quickTrade.symbol} (${this.quickTrade.orderType}). Execution endpoint integration pending.`;
  }

  getInitials(user: User | null): string {
    const first = user?.firstName?.[0] ?? '';
    const last = user?.lastName?.[0] ?? '';
    const initials = `${first}${last}`.trim();
    return initials ? initials.toUpperCase() : 'LP';
  }

  formatPrice(quote: Quote): string {
    return `${quote.currencyCode} ${Number(quote.price).toLocaleString(undefined, { maximumFractionDigits: 4 })}`;
  }

  ngOnInit(): void {
    this.authService.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe(user => {
        this.currentUser = user;
      });

    if (this.authService.isAuthenticated() && !this.currentUser) {
      this.authService.getCurrentUser().pipe(takeUntil(this.destroy$)).subscribe();
    }

    this.searchSubject$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((query) => {
          const q = query.trim();
          if (!q) {
            return of([] as Instrument[]);
          }
          return this.instrumentService.searchInstruments(q).pipe(
            catchError(() => of([] as Instrument[]))
          );
        }),
        takeUntil(this.destroy$)
      )
      .subscribe(results => {
        this.searchResults = results;
      });

    this.loadWatchlists();
    this.loadMarketPulse();
    this.loadMarketDetail(this.quickTrade.symbol);
  }

  private loadWatchlists(): void {
    this.watchlistService.getWatchlists()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (watchlists) => {
          this.watchlists = watchlists;
          if (watchlists.length > 0) {
            this.selectWatchlist(watchlists[0].watchlistId);
          }
        },
        error: () => {
          this.watchlists = [];
        }
      });
  }

  private loadMarketPulse(): void {
    this.isLoadingPulse = true;
    this.marketService.getBulkQuotes(this.pulseSymbols)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (quotes) => {
          this.marketPulse = quotes;
          this.isLoadingPulse = false;
        },
        error: () => {
          this.marketPulse = [];
          this.isLoadingPulse = false;
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
