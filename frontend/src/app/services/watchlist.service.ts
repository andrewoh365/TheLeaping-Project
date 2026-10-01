import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Watchlist {
  watchlistId: number;
  watchlistName: string;
  createdAt: string;
  updatedAt: string;
}

export interface WatchlistInstrument {
  instrumentId: number;
  symbol: string;
  name: string;
  instrumentType: string;
}

@Injectable({
  providedIn: 'root'
})
export class WatchlistService {
  private readonly API_URL = 'http://localhost:8080/api/watchlists';

  constructor(private http: HttpClient) {}

  getWatchlists(): Observable<Watchlist[]> {
    return this.http.get<Watchlist[]>(this.API_URL);
  }

  createWatchlist(watchlistName: string): Observable<Watchlist> {
    return this.http.post<Watchlist>(this.API_URL, { watchlistName });
  }

  getWatchlistInstruments(watchlistId: number): Observable<WatchlistInstrument[]> {
    return this.http.get<WatchlistInstrument[]>(`${this.API_URL}/${watchlistId}/instruments`);
  }

  addInstrument(watchlistId: number, instrumentId: number): Observable<WatchlistInstrument> {
    return this.http.post<WatchlistInstrument>(`${this.API_URL}/${watchlistId}/instruments/${instrumentId}`, {});
  }
}
