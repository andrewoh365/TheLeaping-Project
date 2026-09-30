import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatListModule } from '@angular/material/list';
import { Subject } from 'rxjs';
import { takeUntil, debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { AuthService, User } from '../../services/auth.service';
import { InstrumentService, Instrument } from '../../services/instrumentService';

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
    MatCardModule,
    MatDividerModule,
    MatListModule
  ],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit, OnDestroy {
  private readonly TRADE_API_URL = 'http://localhost:8080/api/trade';

  currentUser: User | null = null;
  searchQuery: string = '';
  searchResults: Instrument[] = [];
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

  private destroy$ = new Subject<void>();
  private searchSubject$ = new Subject<string>();

  constructor(
    private authService: AuthService,
    private router: Router,
    private instrumentService: InstrumentService,
    private http: HttpClient
  ) {}

  navigateToInvest(): void {
    this.router.navigate(['/invest']);
  }

  onSearchInput(): void {
    this.searchSubject$.next(this.searchQuery);
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

    this.http.post<OrderResponse | PlaceOrderSuccess | ApiError>(`${this.TRADE_API_URL}/orders`, this.orderForm)
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
            return;
          }

          if (orderOnly.order_id) {
            this.placedOrder = orderOnly;
            this.orderSubmitMessage = 'Order placed successfully.';
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

    this.http.get<OrderResponse>(`${this.TRADE_API_URL}/orders/${this.orderLookupId}`)
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

  private performSearch(query: string): void {
    this.instrumentService.searchInstruments(query)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (results) => {
          this.searchResults = results;
          console.log('Search results:', results);
        },
        error: (error) => {
          console.error('Search error:', error);
          this.searchResults = [];
        }
      });
  }
  ngOnInit(): void {
    this.authService.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe(user => {
        this.currentUser = user;
      });

    this.searchSubject$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        takeUntil(this.destroy$)
      )
      .subscribe(query => {
        this.performSearch(query);
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
