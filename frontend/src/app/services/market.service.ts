import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface MarketStatusResponse {
  marketName: string;
  timezone: string;
  marketTime: string;
  open: boolean;
  alwaysOpen: boolean;
  nextOpen: string | null;
  nextClose: string | null;
}

/** Testing switch that holds every market open (local development only). */
export interface MarketOverrideStatus {
  allowed: boolean;
  enabled: boolean;
}

export interface NewsItem {
  title: string;
  source: string | null;
  url: string;
  publishedAt: string | null;
}

export interface MarketNews {
  items: NewsItem[];
  provider: string;
  fetchedAt: string | null;
  stale: boolean;
}

export interface MarketMover {
  symbol: string;
  name: string;
  price: number | null;
  change: number | null;
  changePercent: number;
  instrumentId: number | null;
}

export interface MarketMovers {
  gainers: MarketMover[];
  losers: MarketMover[];
  asOf: string | null;
  universe: string | null;
  provider: string;
  fetchedAt: string | null;
  stale: boolean;
}

export interface PricePointResponse {
  symbol: string;
  price: number;
  currencyCode: string;
  timestamp: string;
  sourceType: string;
  provider: string;
}

@Injectable({
  providedIn: 'root'
})
export class MarketService {
  private readonly apiUrl = '/api/market';

  constructor(private http: HttpClient) {}

  getMarketStatus(marketName: string): Observable<MarketStatusResponse> {
    return this.http.get<MarketStatusResponse>(`${this.apiUrl}/exchanges/${marketName}/status`);
  }

  getMarketOverride(): Observable<MarketOverrideStatus> {
    return this.http.get<MarketOverrideStatus>(`${this.apiUrl}/override`);
  }

  setMarketOverride(enabled: boolean): Observable<MarketOverrideStatus> {
    return this.http.put<MarketOverrideStatus>(`${this.apiUrl}/override`, { enabled });
  }

  getPriceHistory(symbol: string, from: string, to: string): Observable<PricePointResponse[]> {
    const params = new HttpParams()
      .set('symbol', symbol)
      .set('from', from)
      .set('to', to);

    return this.http.get<PricePointResponse[]>(`${this.apiUrl}/instruments/price/history`, { params });
  }

  getNews(): Observable<MarketNews> {
    return this.http.get<MarketNews>(`${this.apiUrl}/news`);
  }

  getMovers(): Observable<MarketMovers> {
    return this.http.get<MarketMovers>(`${this.apiUrl}/movers`);
  }
}
