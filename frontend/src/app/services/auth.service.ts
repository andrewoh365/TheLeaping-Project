import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';
import { of } from 'rxjs';

export interface AuthResponse {
  token: string;
  refreshToken?: string;
  expiresIn?: number;
}

export interface User {
  id?: string;
  email: string;
  username?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_URL = 'http://localhost:8080/api'; // Change this based on your backend
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

  /**
   * Register a new user
   */
  register(email: string, password: string, username: string): Observable<AuthResponse> {
    const body = { email, password, username };
    return this.http.post<AuthResponse>(`${this.API_URL}/auth/register`, body).pipe(
      tap(response => {
        this.storeToken(response.token, response.refreshToken);
        // After registration, you might want to fetch user details
      }),
      catchError(error => {
        console.error('Registration error:', error);
        throw error;
      })
    );
  }

  /**
   * Login with email and password
   */
  login(email: string, password: string): Observable<AuthResponse> {
    const body = { email, password };
    return this.http.post<AuthResponse>(`${this.API_URL}/auth/login`, body).pipe(
      tap(response => {
        this.storeToken(response.token, response.refreshToken);
        this.isAuthenticatedSubject.next(true);
        // Optionally fetch user details after login
        this.fetchUserDetails();
      }),
      catchError(error => {
        console.error('Login error:', error);
        throw error;
      })
    );
  }

  /**
   * Logout the current user
   */
  logout(): void {
    this.clearToken();
    this.currentUserSubject.next(null);
    this.isAuthenticatedSubject.next(false);
  }

  /**
   * Get the current JWT token
   */
  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  /**
   * Check if user is authenticated
   */
  isAuthenticated(): boolean {
    return this.hasValidToken();
  }

  /**
   * Get current user
   */
  getCurrentUser(): User | null {
    return this.currentUserSubject.value;
  }

  /**
   * Refresh the JWT token
   */
  refreshToken(): Observable<AuthResponse> {
    const refreshToken = localStorage.getItem(this.REFRESH_TOKEN_KEY);
    if (!refreshToken) {
      return of({} as AuthResponse).pipe(
        catchError(() => {
          this.logout();
          throw new Error('No refresh token available');
        })
      );
    }

    const body = { refreshToken };
    return this.http.post<AuthResponse>(`${this.API_URL}/auth/refresh`, body).pipe(
      tap(response => {
        this.storeToken(response.token, response.refreshToken);
      }),
      catchError(() => {
        this.logout();
        throw new Error('Token refresh failed');
      })
    );
  }

  /**
   * Fetch user details from the backend
   */
  private fetchUserDetails(): void {
    this.http.get<User>(`${this.API_URL}/auth/me`).subscribe({
      next: (user) => {
        this.currentUserSubject.next(user);
        this.storeUser(user);
      },
      error: (error) => {
        console.error('Failed to fetch user details:', error);
      }
    });
  }

  /**
   * Store token in localStorage
   */
  private storeToken(token: string, refreshToken?: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
    if (refreshToken) {
      localStorage.setItem(this.REFRESH_TOKEN_KEY, refreshToken);
    }
  }

  /**
   * Clear tokens from localStorage
   */
  private clearToken(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
  }

  /**
   * Check if token exists and is not expired
   */
  private hasValidToken(): boolean {
    const token = localStorage.getItem(this.TOKEN_KEY);
    if (!token) return false;

    try {
      const payload = this.parseJwt(token);
      const expiresIn = payload.exp * 1000; // Convert to milliseconds
      return expiresIn > Date.now();
    } catch {
      return false;
    }
  }

  /**
   * Parse JWT token to get payload
   */
  private parseJwt(token: string): any {
    try {
      const base64Url = token.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      return JSON.parse(jsonPayload);
    } catch (error) {
      throw new Error('Invalid token');
    }
  }

  /**
   * Store user data in localStorage
   */
  private storeUser(user: User): void {
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
  }

  /**
   * Retrieve user data from localStorage
   */
  private getUserFromStorage(): User | null {
    const userStr = localStorage.getItem(this.USER_KEY);
    return userStr ? JSON.parse(userStr) : null;
  }

  /**
   * Check token expiration periodically
   */
  private checkTokenExpiration(): void {
    setInterval(() => {
      if (!this.hasValidToken() && this.isAuthenticatedSubject.value) {
        this.logout();
      }
    }, 60000); // Check every minute
  }
}
