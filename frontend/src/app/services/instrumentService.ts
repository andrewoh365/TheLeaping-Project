import { Injectable } from '@angular/core';
import { HttpClient} from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Instrument {
  instrumentId: string;
  symbol: string;
  name: string;
  instrumentType: string;
  priceCurrencyCode: string;
  isTradeable: boolean;
  isActive: boolean;
}

export interface PricePointDto {
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
export class InstrumentService {
  private readonly API_URL = 'http://localhost:8080/api/auth/instrument';

  constructor(private http: HttpClient) {}

  getAllInstruments(): Observable<Instrument[]> {
    return this.http.get<Instrument[]>(this.API_URL);
  }

  getInstrumentById(id: string): Observable<Instrument> {
    return this.http.get<Instrument>(`${this.API_URL}/${id}`);
  }

  searchInstruments(query: string): Observable<Instrument[]> {
    return this.http.get<Instrument[]>(`${this.API_URL}/search`, {
      params: { query }
    });
  }

  getPriceHistory(symbol: string, from: string, to: string): Observable<PricePointDto[]> {
    return this.http.get<PricePointDto[]>(
      'http://localhost:8080/api/market/instruments/price/history',
      {
        params: { symbol, from, to }
      }
    );
  }
}