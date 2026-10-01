import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface PortfolioSummary {
  portfolio_id: number;
  cash_balance_usd: number;
  total_holdings_value_usd: number;
  total_portfolio_value_usd: number;
  total_gain_loss_usd: number;
  total_gain_loss_percent: number;
  buying_power_usd: number;
}

export interface PortfolioHolding {
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

export interface PortfolioOverviewResponse {
  summary: PortfolioSummary;
  holdings: PortfolioHolding[];
  last_executed_trade_at: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class PortfolioService {
  private readonly API_URL = 'http://localhost:8080/api/portfolio';

  constructor(private http: HttpClient) {}

  getOverview(): Observable<PortfolioOverviewResponse> {
    return this.http.get<PortfolioOverviewResponse>(`${this.API_URL}/overview`);
  }
}