import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type TimeGrouping = 'DAILY' | 'WEEKLY' | 'MONTHLY';

export type AssetType =
  | 'STOCK'
  | 'CRYPTO'
  | 'FOREX';

export type OrderStatus =
  | 'SUBMITTED'
  | 'ACCEPTED'
  | 'FILLED'
  | 'REJECTED'
  | 'CANCELLED';

export type ClientSegment =
  | 'INACTIVE'
  | 'OCCASIONAL'
  | 'REGULAR'
  | 'FREQUENT';

export interface DashboardFilters {
  dateFrom?: string;
  dateTo?: string;
  assetType?: AssetType;
  market?: string;
  instrumentId?: number | string;
  orderStatus?: OrderStatus;
  clientSegment?: ClientSegment;
}

export interface DashboardSummary {
  trading_volume_usd: number;
  total_orders: number;
  active_clients: number;
  filled_orders_percentage: number;
}

export interface TradingVolumePoint {
  period: string;
  volume_usd: number;
}

export interface TradingVolumeTrend {
  grouping: TimeGrouping;
  points: TradingVolumePoint[];
}

export interface OrderOutcome {
  status: OrderStatus;
  count: number;
  percentage: number;
}

export interface InstrumentActivity {
  symbol: string;
  order_count: number;
}

export interface ClientActivityPoint {
  period: string;
  active_clients: number;
}

export interface ClientActivityTrend {
  grouping: TimeGrouping;
  points: ClientActivityPoint[];
}

export interface RejectionReason {
  reason: string;
  count: number;
}

export interface ClientSegmentBreakdown {
  segment: ClientSegment;
  total_clients: number;
  active_clients: number;
  order_count: number;
  trading_volume_usd: number;
}

export interface SelectedInstrument {
  instrument_id: number;
  symbol: string;
  name: string;
  instrument_type: string;
  market_name: string;
  is_active: boolean;
}

export interface ReportingPeriod {
  date_from: string;
  date_to: string;
  timezone: 'UTC';
}

export interface DashboardResponse {
  summary: DashboardSummary;
  trading_volume_over_time: TradingVolumeTrend;
  order_outcomes: OrderOutcome[];
  most_active_instruments: InstrumentActivity[];
  client_activity_trend: ClientActivityTrend;
  top_rejection_reasons: RejectionReason[];
  client_segment_breakdown: ClientSegmentBreakdown[];
  selected_instrument: SelectedInstrument | null;
  reporting_period: ReportingPeriod;
}

@Injectable({
  providedIn: 'root'
})
export class AnalyticsService {

  private readonly API_URL =
  '/api/analytics';

  constructor(private http: HttpClient) {}

  getDashboard(
    filters: DashboardFilters = {}
  ): Observable<DashboardResponse> {

    let params = new HttpParams();

    if (filters.dateFrom) {
      params = params.set(
        'date_from',
        filters.dateFrom
      );
    }

    if (filters.dateTo) {
      params = params.set(
        'date_to',
        filters.dateTo
      );
    }

    if (filters.assetType) {
      params = params.set(
        'asset_type',
        filters.assetType
      );
    }

    if (filters.market) {
      params = params.set(
        'market',
        filters.market
      );
    }

    if (
      filters.instrumentId !== undefined
      && filters.instrumentId !== null
      && filters.instrumentId !== ''
    ) {
      params = params.set(
        'instrument_id',
        String(filters.instrumentId)
      );
    }

    if (filters.orderStatus) {
      params = params.set(
        'order_status',
        filters.orderStatus
      );
    }

    if (filters.clientSegment) {
      params = params.set(
        'client_segment',
        filters.clientSegment
      );
    }

    return this.http.get<DashboardResponse>(
      `${this.API_URL}/dashboard`,
      { params }
    );
  }
}