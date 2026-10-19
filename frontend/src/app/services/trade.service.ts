import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type OrderAction = 'BUY' | 'SELL';

export interface PlaceOrderRequest {
  instrument_id: number;
  order_action: OrderAction;
  order_type: 'MARKET' | 'LIMIT';
  quantity: number | null;
  limit_price: number | null;
  time_in_force: 'DAY' | 'GTC';
}

export interface OrderResponse {
  order_id: number;
  instrument_id?: number;
  instrument_symbol: string;
  order_action: string;
  order_type: string;
  order_status: string;
  quantity: number;
  limit_price?: number;
  /** Market price when the order was submitted */
  reference_price?: number | null;
  submitted_at?: string;
  accepted_at?: string;
  filled_at?: string;
  rejected_at?: string;
  rejection_reason?: string;
  cancelled_at?: string;
  instrument_name?: string;
  execution_price?: number | null;
  total_usd_value?: number | null;
}

export interface OrderHistoryPage {
  orders: OrderResponse[];
  page: number;
  size: number;
  total_elements: number;
  has_more: boolean;
}

export interface TradeApiError {
  message: string;
  order?: OrderResponse;
}

/** Pre-trade preview: indicative price, estimated total and whether the order would be accepted. */
export interface OrderPreview {
  instrument_id: number;
  symbol: string;
  name: string;
  instrument_type: string | null;
  market_name: string | null;
  order_action: OrderAction;
  quantity: number | null;
  /** Indicative price for this side: the ask when buying, the bid when selling. */
  indicative_price_usd: number | null;
  bid_usd: number | null;
  ask_usd: number | null;
  spread_usd: number | null;
  estimated_total_usd: number | null;
  cash_available_usd: number;
  quantity_owned: number;
  max_quantity: number | null;
  tradable: boolean;
  market_open: boolean;
  can_submit: boolean;
  message: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class TradeService {
  private readonly apiUrl = '/api/trade';

  constructor(private http: HttpClient) {}

  previewOrder(instrumentId: number, action: OrderAction, quantity: number | null): Observable<OrderPreview> {
    let params = new HttpParams()
      .set('instrumentId', instrumentId)
      .set('action', action);
    if (quantity !== null && quantity > 0) {
      params = params.set('quantity', quantity);
    }
    return this.http.get<OrderPreview>(`${this.apiUrl}/preview`, { params });
  }

  /** Returns the order once accepted (202); it then executes asynchronously, so poll getOrder for the outcome. */
  placeOrder(request: PlaceOrderRequest): Observable<OrderResponse> {
    return this.http.post<OrderResponse>(`${this.apiUrl}/orders`, request);
  }

  getOrderHistory(page = 0, size = 20): Observable<OrderHistoryPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<OrderHistoryPage>(`${this.apiUrl}/orders`, { params });
  }

  getOrder(orderId: number): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`${this.apiUrl}/orders/${orderId}`);
  }
}
