import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, throwError } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';

export interface AuthResponse {
  token: string | null;
  refreshToken?: string;
  success?: boolean;
  message?: string;
  role?: string;
  email?: string;
  expiresIn?: number;
}

export interface User {
  id: string;
  email: string;
  firstName?: string;
  lastName?: string;
}

interface LoginApiResponse {
  token: string | null;
  email?: string;
  message: string;
  success: boolean;
  role?: string;
  refreshToken?: string;
}

interface RegisterApiResponse {
  token: string | null;
  email?: string;
  firstName?: string;
  lastName?: string;
  message: string;
  success: boolean;
  refreshToken?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_URL = 'http://localhost:8080/api';
  private readonly TOKEN_KEY = 'jwt_token';
  private readonly REFRESH_TOKEN_KEY = 'refresh_token';
  private readonly USER_KEY = 'user';

  private currentUserSubject = new BehaviorSubject<User | null>(this.getUserFromStorage());
  public currentUser$ = this.currentUserSubject.asObservable();

  private isAuthenticatedSubject = new BehaviorSubject<boolean>(this.hasValidToken());
  public isAuthenticated$ = this.isAuthenticatedSubject.asObservable();

  constructor(private http: HttpClient) {
    this.checkTokenExpiration();
  }

  register(firstName: string, lastName: string, email: string, password: string, confirmPassword: string, dateOfBirth: string, taxId: string): Observable<AuthResponse> {
    const body = { firstName, lastName, email, password, confirmPassword, dateOfBirth, taxId };
    return this.http.post<RegisterApiResponse>(`${this.API_URL}/auth/register`, body).pipe(
      map((response) => this.normalizeAuthResponse(response)),
      tap(response => {
        if (response.token) {
          this.storeToken(response.token, response.refreshToken);
          this.isAuthenticatedSubject.next(true);
          this.fetchUserDetails();
        }
      }),
      catchError(error => {
        return throwError(() => error);
      })
    );
  }

  login(email: string, password: string): Observable<AuthResponse> {
    const body = { email, password };
    return this.http.post<LoginApiResponse>(`${this.API_URL}/auth/login`, body).pipe(
      map((response) => {
        const normalized = this.normalizeAuthResponse(response);
        if (!response.success) {
          throw new Error(response.message || 'Login failed');
        }
        return normalized;
      }),
      tap(response => {
        if (response.token) {
          this.storeToken(response.token, response.refreshToken);
          this.isAuthenticatedSubject.next(true);
          this.fetchUserDetails();
        }
      }),
      catchError(error => {
        return throwError(() => error);
      })
    );
  }

  logout(): void {
    this.clearToken();
    this.currentUserSubject.next(null);
    this.isAuthenticatedSubject.next(false);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    return this.hasValidToken();
  }

  getCurrentUser(): Observable<User> {
    return this.http.get<User>(`${this.API_URL}/auth/me`).pipe(
      tap((user) => {
        this.currentUserSubject.next(user);
        this.storeUser(user);
      })
    );
  } 

  refreshToken(): Observable<AuthResponse> {
    const refreshTokenValue = localStorage.getItem(this.REFRESH_TOKEN_KEY);
    if (!refreshTokenValue) {
      this.logout();
      return throwError(() => new Error('No refresh token available'));
    }

    const body = { refreshToken: refreshTokenValue };
    return this.http.post<LoginApiResponse>(`${this.API_URL}/auth/refresh`, body).pipe(
      map((response) => {
        const normalized = this.normalizeAuthResponse(response);
        if (!response.success) {
          throw new Error(response.message || 'Token refresh failed');
        }
        return normalized;
      }),
      tap(response => {
        if (response.token) {
          this.storeToken(response.token, response.refreshToken);
        }
      }),
      catchError((error) => {
        this.logout();
        return throwError(() => error);
      })
    );
  }

  private fetchUserDetails(): void {
    this.http.get<User>(`${this.API_URL}/auth/me`).subscribe({
      next: (user) => {
        this.currentUserSubject.next(user);
        this.storeUser(user);
      },
      error: () => {
        this.currentUserSubject.next(this.getUserFromStorage());
      }
    });
  }

  private storeToken(token: string, refreshToken?: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
    if (refreshToken) {
      localStorage.setItem(this.REFRESH_TOKEN_KEY, refreshToken);
    }
  }

  private clearToken(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
  }

  private hasValidToken(): boolean {
    const token = localStorage.getItem(this.TOKEN_KEY);
    if (!token) {
      return false;
    }

    try {
      const payload = this.parseJwt(token);
      const expiresIn = payload.exp * 1000;
      return expiresIn > Date.now();
    } catch {
      return false;
    }
  }

  private parseJwt(token: string): { exp: number } {
    try {
      const base64Url = token.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      return JSON.parse(jsonPayload) as { exp: number };
    } catch {
      throw new Error('Invalid token');
    }
  }

  private storeUser(user: User): void {
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
  }

  private getUserFromStorage(): User | null {
    const userStr = localStorage.getItem(this.USER_KEY);
    return userStr ? JSON.parse(userStr) : null;
  }

  private checkTokenExpiration(): void {
    setInterval(() => {
      if (!this.hasValidToken() && this.isAuthenticatedSubject.value) {
        this.logout();
      }
    }, 60000);
  }

  private normalizeAuthResponse(response: LoginApiResponse | RegisterApiResponse): AuthResponse {
    return {
      token: response.token,
      refreshToken: response.refreshToken,
      success: response.success,
      message: response.message,
      role: 'role' in response ? response.role : undefined,
      email: response.email
    };
  }
}