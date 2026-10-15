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

export interface AdminAuditEvent {
  auditEventId: number;
  eventType: string;
  occurredAt: string | null;
  userId: number | null;
  orderId: number | null;
  executionAttemptId: number | null;
  tradeId: number | null;
  cashTransactionId: number | null;
  eventDetails: Record<string, unknown>;
}

export interface AdminPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first?: boolean;
  last?: boolean;
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
    filters: AdminClientFilters = {}
  ): Observable<AdminClientSummary[]> {

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

    return this.http.get<AdminClientSummary[]>(
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
    clientId: number
  ): Observable<AdminHolding[]> {

    return this.http.get<AdminHolding[]>(
      `${this.API_URL}/clients/${clientId}/holdings`
    );
  }

  getOrders(
    filters: AdminOrderFilters = {}
  ): Observable<AdminOrderLookup[]> {

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

    return this.http.get<AdminOrderLookup[]>(
      `${this.API_URL}/orders`,
      { params }
    );
  }

  getClientOrders(
    clientId: number
  ): Observable<AdminOrderLookup[]> {

    return this.http.get<AdminOrderLookup[]>(
      `${this.API_URL}/clients/${clientId}/orders`
    );
  }

  getOrder(
    orderId: number
  ): Observable<AdminOrderDetail> {

    return this.http.get<AdminOrderDetail>(
      `${this.API_URL}/orders/${orderId}`
    );
  }

  getClientActivity(
    clientId: number,
    page = 0,
    size = 10
  ): Observable<AdminPage<AdminAuditEvent>> {

    const params = new HttpParams()
      .set('page', page)
      .set('size', size);

    return this.http.get<AdminPage<AdminAuditEvent>>(
      `${this.API_URL}/clients/${clientId}/activity`,
      { params }
    );
  }

  getOrderAudit(
    orderId: number
  ): Observable<AdminAuditEvent[]> {

    return this.http.get<AdminAuditEvent[]>(
      `${this.API_URL}/orders/${orderId}/audit`
    );
  }
}