import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface HoldingResponse {
  instrumentSymbol: string;
  instrumentName: string;
  quantity: number;
  averageCostUsd: number | null;
  currentPriceUsd: number | null;
  marketValueUsd: number | null;
  gainLossUsd: number | null;
  gainLossPercent: number | null;
}

export interface PortfolioResponse {
  portfolioId: number;
  cashBalanceUsd: number;
  holdings: HoldingResponse[];
}

@Injectable({
  providedIn: 'root'
})
export class PortfolioService {
  private readonly apiUrl = 'http://localhost:8080/api/portfolio';

  constructor(private http: HttpClient) {}

  getMyPortfolio(): Observable<PortfolioResponse> {
    return this.http.get<PortfolioResponse>(this.apiUrl);
  }
}
