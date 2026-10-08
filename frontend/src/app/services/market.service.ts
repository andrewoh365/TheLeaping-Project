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
  private readonly apiUrl = 'http://localhost:8080/api/market';

  constructor(private http: HttpClient) {}

  getMarketStatus(marketName: string): Observable<MarketStatusResponse> {
    return this.http.get<MarketStatusResponse>(`${this.apiUrl}/exchanges/${marketName}/status`);
  }

  getPriceHistory(symbol: string, from: string, to: string): Observable<PricePointResponse[]> {
    const params = new HttpParams()
      .set('symbol', symbol)
      .set('from', from)
      .set('to', to);

    return this.http.get<PricePointResponse[]>(`${this.apiUrl}/instruments/price/history`, { params });
  }
}
