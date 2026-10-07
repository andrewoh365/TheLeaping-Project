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
}