import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';

export interface Instrument {
  instrumentId: string;
  symbol: string;
  name: string;
  instrumentType: string;
  priceCurrencyCode: string;
  isTradeable: boolean;
  isActive: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class InstrumentService {
  /*
   * Primary endpoint used by the existing instrument API.
   */
  private readonly API_URL = '/api/instruments';

  /*
   * Keep compatibility with the newer dev implementation.
   *
   * We use the relative /api path instead of hardcoding
   * localhost so requests still go through the project's
   * Angular proxy and standardized development environment.
   */
  private readonly DEV_API_URL = '/api/auth/instrument';

  constructor(private http: HttpClient) {}

  getAllInstruments(): Observable<Instrument[]> {
    return this.http
      .get<Instrument[]>(this.API_URL)
      .pipe(
        catchError(error => {
          if (error.status === 404) {
            return this.http.get<Instrument[]>(
              this.DEV_API_URL
            );
          }

          return throwError(() => error);
        })
      );
  }

  getInstrumentById(
    id: string
  ): Observable<Instrument> {
    return this.http
      .get<Instrument>(
        `${this.API_URL}/${id}`
      )
      .pipe(
        catchError(error => {
          if (error.status === 404) {
            return this.http.get<Instrument>(
              `${this.DEV_API_URL}/${id}`
            );
          }

          return throwError(() => error);
        })
      );
  }

  searchInstruments(
    query: string
  ): Observable<Instrument[]> {
    return this.http
      .get<Instrument[]>(
        `${this.API_URL}/search`,
        {
          params: { query }
        }
      )
      .pipe(
        catchError(error => {
          if (error.status === 404) {
            return this.http.get<Instrument[]>(
              `${this.DEV_API_URL}/search`,
              {
                params: { query }
              }
            );
          }

          return throwError(() => error);
        })
      );
  }
}