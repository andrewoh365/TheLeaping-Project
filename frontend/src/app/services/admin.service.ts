import { Injectable } from '@angular/core';
import {
  HttpClient,
  HttpParams
} from '@angular/common/http';

import { Observable } from 'rxjs';

export type ClientStatus =
  | 'ACTIVE'
  | 'INACTIVE';

export interface AdminClientSummary {
  clientId: number;
  firstName: string | null;
  lastName: string | null;
  email: string | null;
  status: string | null;
}

export interface AdminClientPage {
  clients: AdminClientSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface AdminOrderPage {
  orders: AdminOrderLookup[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface AdminClientDetail {
  clientId: number;
  firstName: string | null;
  lastName: string | null;
  email: string | null;
  status: string | null;

  createdAt: string | null;
  lastLogin: string | null;

  portfolioId: number | null;
  cashBalanceUsd: number | null;
}

export interface AdminHolding {
  holding_id: number;
  instrument_id: number;

  symbol: string;
  instrument_name: string;

  quantity: number;
  average_cost_usd: number;

  current_price: number;
  current_value_usd: number;

  gain_loss_usd: number;
  gain_loss_percent: number;
}

export interface AdminHoldingPage {
  holdings: AdminHolding[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface AdminClientFilters {
  search?: string;
  status?: ClientStatus | '';
}

export interface AdminOrderLookup {
  orderId: number;
  tradeId: number | null;

  clientId: number | null;
  firstName: string | null;
  lastName: string | null;

  instrumentId: number | null;
  symbol: string | null;
  instrumentName: string | null;

  side: string | null;
  quantity: number | null;
  status: string | null;

  submittedAt: string | null;
  executedAt: string | null;
}

export interface AdminOrderDetail {
  orderId: number;

  status: string | null;
  side: string | null;
  orderType: string | null;
  timeInForce: string | null;

  quantity: number | null;
  limitPriceUsd: number | null;

  clientId: number | null;
  firstName: string | null;
  lastName: string | null;
  portfolioId: number | null;

  instrumentId: number | null;
  symbol: string | null;
  instrumentName: string | null;

  submittedAt: string | null;
  acceptedAt: string | null;
  rejectedAt: string | null;
  rejectionReason: string | null;
  cancelledAt: string | null;
  filledAt: string | null;

  tradeId: number | null;

  executionPrice: number | null;
  executionPriceCurrencyCode: string | null;
  exchangeRateToUsdAtExecution: number | null;
  executionPriceUsd: number | null;
  totalUsdValue: number | null;

  executedAt: string | null;
}

export interface AdminOrderFilters {
  search?: string;
  status?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AdminService {

  private readonly API_URL =
    '/api/admin';

  constructor(
    private http: HttpClient
  ) {}

  getClients(
    filters: AdminClientFilters = {},
    page = 0,
    size = 100
  ): Observable<AdminClientPage> {

    let params = new HttpParams();

    if (filters.search?.trim()) {
      params = params.set(
        'search',
        filters.search.trim()
      );
    }

    if (filters.status) {
      params = params.set(
        'status',
        filters.status
      );
    }

    params = params.set('page', page);
    params = params.set('size', size);

    return this.http.get<AdminClientPage>(
      `${this.API_URL}/clients`,
      { params }
    );
  }

  getClient(
    clientId: number
  ): Observable<AdminClientDetail> {

    return this.http.get<AdminClientDetail>(
      `${this.API_URL}/clients/${clientId}`
    );
  }

  getClientHoldings(
    clientId: number,
    page = 0,
    size = 100
  ): Observable<AdminHoldingPage> {

    let params = new HttpParams()
      .set('page', page)
      .set('size', size);

    return this.http.get<AdminHoldingPage>(
      `${this.API_URL}/clients/${clientId}/holdings`,
      { params }
    );
  }

  getOrders(
    filters: AdminOrderFilters = {},
    page = 0,
    size = 100
  ): Observable<AdminOrderPage> {

    let params = new HttpParams();

    if (filters.search?.trim()) {
      params = params.set(
        'search',
        filters.search.trim()
      );
    }

    if (filters.status) {
      params = params.set(
        'status',
        filters.status
      );
    }

    params = params.set('page', page);
    params = params.set('size', size);

    return this.http.get<AdminOrderPage>(
      `${this.API_URL}/orders`,
      { params }
    );
  }

  getClientOrders(
    clientId: number,
    page = 0,
    size = 100
  ): Observable<AdminOrderPage> {

    const params = new HttpParams()
      .set('page', page)
      .set('size', size);

    return this.http.get<AdminOrderPage>(
      `${this.API_URL}/clients/${clientId}/orders`,
      { params }
    );
  }

  getOrder(
    orderId: number
  ): Observable<AdminOrderDetail> {

    return this.http.get<AdminOrderDetail>(
      `${this.API_URL}/orders/${orderId}`
    );
  }
}