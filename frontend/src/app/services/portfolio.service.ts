import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

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

// Holding shape as returned by the backend (snake_case JSON from holdings/HoldingResponse.java)
interface ApiHoldingResponse {
  holding_id: number;
  instrument_id: number;
  symbol: string;
  instrument_name: string;
  quantity: number;
  average_cost_usd: number | null;
  current_price: number | null;
  current_value_usd: number | null;
  gain_loss_usd: number | null;
  gain_loss_percent: number | null;
}

interface ApiPortfolioResponse {
  portfolioId: number;
  cashBalanceUsd: number;
  holdings: ApiHoldingResponse[];
}

@Injectable({
  providedIn: 'root'
})
export class PortfolioService {
  private readonly apiUrl = 'http://localhost:8080/api/portfolio';

  constructor(private http: HttpClient) {}

  getMyPortfolio(): Observable<PortfolioResponse> {
    return this.http.get<ApiPortfolioResponse>(this.apiUrl).pipe(
      map((portfolio) => ({
        portfolioId: portfolio.portfolioId,
        cashBalanceUsd: portfolio.cashBalanceUsd,
        holdings: (portfolio.holdings ?? []).map((holding) => ({
          instrumentSymbol: holding.symbol,
          instrumentName: holding.instrument_name,
          quantity: holding.quantity,
          averageCostUsd: holding.average_cost_usd,
          currentPriceUsd: holding.current_price,
          marketValueUsd: holding.current_value_usd,
          gainLossUsd: holding.gain_loss_usd,
          gainLossPercent: holding.gain_loss_percent
        }))
      }))
    );
  }
}
