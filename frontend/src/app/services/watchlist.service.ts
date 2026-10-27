import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';

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

export interface WatchlistNameRequest {
  watchlistName: string;
}

@Injectable({
  providedIn: 'root'
})
export class WatchlistService {
  private readonly apiUrl = '/api/watchlists';
  private readonly changedSubject = new Subject<void>();

  /** Emits when watchlists are changed outside the dashboard (e.g. from the instrument view). */
  readonly changed$ = this.changedSubject.asObservable();

  notifyChanged(): void {
    this.changedSubject.next();
  }

  constructor(private http: HttpClient) {}

  getWatchlists(): Observable<WatchlistResponse[]> {
    return this.http.get<WatchlistResponse[]>(this.apiUrl);
  }

  createWatchlist(request: WatchlistNameRequest): Observable<WatchlistResponse> {
    return this.http.post<WatchlistResponse>(this.apiUrl, request);
  }

  renameWatchlist(watchlistId: number, request: WatchlistNameRequest): Observable<WatchlistResponse> {
    return this.http.put<WatchlistResponse>(`${this.apiUrl}/${watchlistId}`, request);
  }

  deleteWatchlist(watchlistId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${watchlistId}`);
  }

  getWatchlistInstruments(watchlistId: number): Observable<WatchlistInstrumentResponse[]> {
    return this.http.get<WatchlistInstrumentResponse[]>(`${this.apiUrl}/${watchlistId}/instruments`);
  }

  addInstrument(watchlistId: number, instrumentId: number): Observable<WatchlistInstrumentResponse> {
    return this.http.post<WatchlistInstrumentResponse>(`${this.apiUrl}/${watchlistId}/instruments/${instrumentId}`, {});
  }

  removeInstrument(watchlistId: number, instrumentId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${watchlistId}/instruments/${instrumentId}`);
  }
}
