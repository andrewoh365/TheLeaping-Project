import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, of } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';

export type UserRole = 'ADMIN' | 'ANALYST' | 'CUSTOMER';

export interface AuthResponse {
  token: string;
  refreshToken?: string;
  expiresIn?: number;
  email?: string;
  role?: UserRole;
  message?: string;
  success?: boolean;
}

export interface User {
  id?: string;
  email: string;
  username?: string;
  role?: UserRole;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_URL = '/api';
  private readonly TOKEN_KEY = 'jwt_token';
  private readonly REFRESH_TOKEN_KEY = 'refresh_token';
  private readonly USER_KEY = 'user';

  private currentUserSubject =
    new BehaviorSubject<User | null>(this.getUserFromStorage());

  public currentUser$ =
    this.currentUserSubject.asObservable();

  private isAuthenticatedSubject =
    new BehaviorSubject<boolean>(this.hasValidToken());

  public isAuthenticated$ =
    this.isAuthenticatedSubject.asObservable();

  constructor(private http: HttpClient) {
    this.checkTokenExpiration();
  }

  /**
   * Register a new user
   */
  register(
    firstName: string,
    lastName: string,
    email: string,
    password: string,
    confirmPassword: string,
    dateOfBirth: string,
    taxId: string
  ): Observable<AuthResponse> {
    const body = {
      firstName,
      lastName,
      email,
      password,
      confirmPassword,
      dateOfBirth,
      taxId
    };

    return this.http
      .post<AuthResponse>(
        `${this.API_URL}/auth/register`,
        body
      )
      .pipe(
        tap(response => {
          this.storeToken(
            response.token,
            response.refreshToken
          );
        }),

        catchError(error => {
          console.error(
            'Registration error:',
            error
          );

          throw error;
        })
      );
  }

  /**
   * Login with email and password
   */
  login(
    email: string,
    password: string
  ): Observable<AuthResponse> {
    const body = {
      email,
      password
    };

    return this.http
      .post<AuthResponse>(
        `${this.API_URL}/auth/login`,
        body
      )
      .pipe(
        tap(response => {
          this.storeToken(
            response.token,
            response.refreshToken
          );

          this.isAuthenticatedSubject.next(true);

          /*
           * Preserve role information when the backend returns it.
           * If no role is returned, still keep the minimal user
           * record so existing login behavior continues to work.
           */
          const user: User = {
            email: response.email ?? email,
            ...(response.role
              ? { role: response.role }
              : {})
          };

          this.currentUserSubject.next(user);
          this.storeUser(user);
        }),

        catchError(error => {
          console.error(
            'Login error:',
            error
          );

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
    return localStorage.getItem(
      this.TOKEN_KEY
    );
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
   * Get current user's role
   */
  getCurrentRole(): UserRole | null {
    return (
      this.currentUserSubject.value?.role ??
      null
    );
  }

  /**
   * Check whether current user has
   * one of the provided roles.
   */
  hasRole(...roles: UserRole[]): boolean {
    const role = this.getCurrentRole();

    return (
      role !== null &&
      roles.includes(role)
    );
  }

  /**
   * Refresh the JWT token
   */
  refreshToken(): Observable<AuthResponse> {
    const refreshToken =
      localStorage.getItem(
        this.REFRESH_TOKEN_KEY
      );

    if (!refreshToken) {
      return of({} as AuthResponse).pipe(
        catchError(() => {
          this.logout();

          throw new Error(
            'No refresh token available'
          );
        })
      );
    }

    const body = {
      refreshToken
    };

    return this.http
      .post<AuthResponse>(
        `${this.API_URL}/auth/refresh`,
        body
      )
      .pipe(
        tap(response => {
          this.storeToken(
            response.token,
            response.refreshToken
          );
        }),

        catchError(() => {
          this.logout();

          throw new Error(
            'Token refresh failed'
          );
        })
      );
  }

  /**
   * Store token in localStorage
   */
  private storeToken(
    token: string,
    refreshToken?: string
  ): void {
    localStorage.setItem(
      this.TOKEN_KEY,
      token
    );

    if (refreshToken) {
      localStorage.setItem(
        this.REFRESH_TOKEN_KEY,
        refreshToken
      );
    }
  }

  /**
   * Clear tokens and user data
   */
  private clearToken(): void {
    localStorage.removeItem(
      this.TOKEN_KEY
    );

    localStorage.removeItem(
      this.REFRESH_TOKEN_KEY
    );

    localStorage.removeItem(
      this.USER_KEY
    );
  }

  /**
   * Check if token exists
   * and is not expired
   */
  private hasValidToken(): boolean {
    const token =
      localStorage.getItem(
        this.TOKEN_KEY
      );

    if (!token) {
      return false;
    }

    try {
      const payload =
        this.parseJwt(token);

      const expiresIn =
        payload.exp * 1000;

      return expiresIn > Date.now();
    } catch {
      return false;
    }
  }

  /**
   * Parse JWT token payload
   */
  private parseJwt(token: string): any {
    try {
      const base64Url =
        token.split('.')[1];

      const base64 =
        base64Url
          .replace(/-/g, '+')
          .replace(/_/g, '/');

      const jsonPayload =
        decodeURIComponent(
          atob(base64)
            .split('')
            .map(
              c =>
                '%' +
                (
                  '00' +
                  c
                    .charCodeAt(0)
                    .toString(16)
                ).slice(-2)
            )
            .join('')
        );

      return JSON.parse(
        jsonPayload
      );
    } catch {
      throw new Error(
        'Invalid token'
      );
    }
  }

  /**
   * Store user data
   */
  private storeUser(
    user: User
  ): void {
    localStorage.setItem(
      this.USER_KEY,
      JSON.stringify(user)
    );
  }

  /**
   * Retrieve user data
   */
  private getUserFromStorage():
    User | null {

    const userStr =
      localStorage.getItem(
        this.USER_KEY
      );

    return userStr
      ? JSON.parse(userStr)
      : null;
  }

  /**
   * Check token expiration
   * once per minute
   */
  private checkTokenExpiration(): void {
    setInterval(() => {
      if (
        !this.hasValidToken() &&
        this.isAuthenticatedSubject.value
      ) {
        this.logout();
      }
    }, 60000);
  }
}