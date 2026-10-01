import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Quote {
  symbol: string;
  price: number;
  currencyCode: string;
  timestamp: string;
  sourceType: string;
  provider: string;
  stale: boolean;
  ageSeconds: number;
}

export interface Tradability {
  symbol: string;
  tradable: boolean;
  reason: string;
  marketOpen: boolean;
  alwaysOpen: boolean;
  priceAvailable: boolean;
  priceStale: boolean;
  message: string;
}

@Injectable({
  providedIn: 'root'
})
export class MarketService {
  private readonly API_URL = 'http://localhost:8080/api/market';

  constructor(private http: HttpClient) {}

  getQuote(symbol: string): Observable<Quote> {
    return this.http.get<Quote>(`${this.API_URL}/instruments/quote`, {
      params: { symbol }
    });
  }

  getTradability(symbol: string): Observable<Tradability> {
    return this.http.get<Tradability>(`${this.API_URL}/instruments/tradability`, {
      params: { symbol }
    });
  }

  getBulkQuotes(symbols: string[]): Observable<Quote[]> {
    let params = new HttpParams();
    symbols.forEach((symbol) => {
      params = params.append('symbols', symbol);
    });

    return this.http.get<Quote[]>(`${this.API_URL}/quotes`, { params });
  }
}
