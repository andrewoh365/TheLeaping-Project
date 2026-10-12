import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface WatchlistResponse {
  watchlistId: number;
  watchlistName: string;
  createdAt: string;
  updatedAt: string;
}

export interface WatchlistInstrumentResponse {
  instrumentId: number;
  symbol: string;
  name: string;
  instrumentType: string;
}

@Injectable({
  providedIn: 'root'
})
export class WatchlistService {
  private readonly apiUrl = 'http://localhost:8080/api/watchlists';

  constructor(private http: HttpClient) {}

  getWatchlists(): Observable<WatchlistResponse[]> {
    return this.http.get<WatchlistResponse[]>(this.apiUrl);
  }

  getWatchlistInstruments(watchlistId: number): Observable<WatchlistInstrumentResponse[]> {
    return this.http.get<WatchlistInstrumentResponse[]>(`${this.apiUrl}/${watchlistId}/instruments`);
  }
}
