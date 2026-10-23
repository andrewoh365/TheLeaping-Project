import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, of, throwError } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';

export type UserRole =
  | 'ADMIN'
  | 'ANALYST'
  | 'CUSTOMER';

export interface AuthResponse {
  token: string;
  refreshToken?: string;
  expiresIn?: number;
  email?: string;
  firstName?: string;
  lastName?: string;
  role?: UserRole;
  message?: string;
  success?: boolean;
}

export interface User {
  id?: string;
  email: string;
  username?: string;
  firstName?: string;
  lastName?: string;
  role?: UserRole;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private readonly API_URL = '/api';

  private readonly TOKEN_KEY =
    'jwt_token';

  private readonly REFRESH_TOKEN_KEY =
    'refresh_token';

  private readonly USER_KEY =
    'user';

  private currentUserSubject =
    new BehaviorSubject<User | null>(
      this.getUserFromStorage()
    );

  public currentUser$ =
    this.currentUserSubject.asObservable();

  private isAuthenticatedSubject =
    new BehaviorSubject<boolean>(
      this.hasValidToken()
    );

  public isAuthenticated$ =
    this.isAuthenticatedSubject.asObservable();

  constructor(
    private http: HttpClient
  ) {
    this.checkTokenExpiration();
  }

  /**
   * Register a new user.
   */
  register(
    firstName: string,
    lastName: string,
    email: string,
    password: string,
    confirmPassword: string,
    dateOfBirth: string
  ): Observable<AuthResponse> {

    const body = {
      firstName,
      lastName,
      email,
      password,
      confirmPassword,
      dateOfBirth
    };

    return this.http
      .post<AuthResponse>(
        `${this.API_URL}/auth/register`,
        body
      )
      .pipe(
        tap(response => this.initializeSession(response, email)),

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
   * Login with email and password.
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
          this.initializeSession(response, email);
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
   * Load the currently authenticated user's
   * full profile from GET /api/auth/me.
   *
   * The backend returns:
   * - id
   * - email
   * - firstName
   * - lastName
   *
   * Role is preserved from the login response,
   * because /auth/me currently does not return it.
   */
  fetchUserDetails(): void {

    this.http
      .get<User>(
        `${this.API_URL}/auth/me`
      )
      .pipe(
        catchError(error => {
          console.error(
            'Failed to fetch user details:',
            error
          );

          return of(null);
        })
      )
      .subscribe(userDetails => {

        if (!userDetails) {
          return;
        }

        const existingUser =
          this.currentUserSubject.value;

        const mergedUser: User = {
          ...(existingUser ?? {}),
          ...userDetails,
          email:
            userDetails.email ??
            existingUser?.email ??
            ''
        };

        this.currentUserSubject
          .next(mergedUser);

        this.storeUser(mergedUser);
      });
  }

  /**
   * Logout the current user.
   */
  logout(): void {

    const token =
      this.getToken();

    const refreshToken =
      localStorage.getItem(
        this.REFRESH_TOKEN_KEY
      );

    if (token || refreshToken) {

      const headers:
        Record<string, string> = {};

      if (token) {
        headers['Authorization'] =
          `Bearer ${token}`;
      }

      this.http
        .post(
          `${this.API_URL}/auth/logout`,
          { refreshToken },
          { headers }
        )
        .subscribe({
          error: () => undefined
        });
    }

    this.clearLocalSession();
  }

  /**
   * Get the current JWT token.
   */
  getToken(): string | null {

    return localStorage.getItem(
      this.TOKEN_KEY
    );
  }

  /**
   * Check whether user is authenticated.
   */
  isAuthenticated(): boolean {

    return this.hasValidToken();
  }

  /**
   * Get current user.
   */
  getCurrentUser(): User | null {

    return this.currentUserSubject.value;
  }

  /**
   * Email of the account whose token is
   * stored, read from the token itself.
   *
   * Unlike getCurrentUser(), this is set
   * straight after registering too.
   */
  getSignedInEmail(): string | null {

    const token =
      this.getToken();

    if (!token) {
      return null;
    }

    try {

      const subject =
        this.parseJwt(token).sub;

      return typeof subject === 'string'
        ? subject.trim().toLowerCase()
        : null;

    } catch {

      return null;
    }
  }

  /**
   * Get current user's role.
   */
  getCurrentRole(): UserRole | null {

    return (
      this.currentUserSubject.value?.role ??
      null
    );
  }

  /**
   * Establish the authenticated session and load the user's full profile.
   */
  private initializeSession(
    response: AuthResponse,
    fallbackEmail: string
  ): void {
    this.storeToken(
      response.token,
      response.refreshToken
    );

    this.isAuthenticatedSubject.next(true);

    const user: User = {
      email: response.email ?? fallbackEmail,
      ...(response.firstName ? { firstName: response.firstName } : {}),
      ...(response.lastName ? { lastName: response.lastName } : {}),
      ...(response.role ? { role: response.role } : {})
    };

    this.currentUserSubject.next(user);
    this.storeUser(user);
    this.fetchUserDetails();
  }

  /**
   * Check whether current user has
   * one of the supplied roles.
   */
  hasRole(
    ...roles: UserRole[]
  ): boolean {

    const role =
      this.getCurrentRole();

    return (
      role !== null &&
      roles.includes(role)
    );
  }

  /**
   * Refresh the JWT token.
   */
  refreshToken():
    Observable<AuthResponse> {

    const refreshToken =
      localStorage.getItem(
        this.REFRESH_TOKEN_KEY
      );

    if (!refreshToken) {

      this.logout();

      return throwError(
        () => new Error(
          'No refresh token available'
        )
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

          return throwError(
            () => new Error(
              'Token refresh failed'
            )
          );
        })
      );
  }

  /**
   * Store JWT and refresh token.
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
   * Drop the signed-in session in the browser.
   * The server revoke is requested separately.
   */
  private clearLocalSession(): void {

    this.clearToken();

    this.currentUserSubject
      .next(null);

    this.isAuthenticatedSubject
      .next(false);
  }

  /**
   * Clear authentication data.
   */
  private clearToken(): void {

    localStorage.removeItem(
      this.TOKEN_KEY
    );

    localStorage.removeItem(
      'token'
    );

    localStorage.removeItem(
      this.REFRESH_TOKEN_KEY
    );

    localStorage.removeItem(
      this.USER_KEY
    );
  }

  /**
   * Check that a JWT exists and
   * has not expired.
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

      const expiresAt =
        typeof payload.exp === 'number'
          ? payload.exp * 1000
          : NaN;

      return Number.isFinite(expiresAt) &&
        expiresAt > Date.now();

    } catch {

      return false;
    }
  }

  /**
   * Parse JWT payload.
   */
  private parseJwt(
    token: string
  ): any {

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
   * Store current user.
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
   * Retrieve stored user.
   */
  private getUserFromStorage():
    User | null {

    const userStr =
      localStorage.getItem(
        this.USER_KEY
      );

    if (!userStr) {
      return null;
    }

    try {
      const user = JSON.parse(userStr) as User;

      return user && typeof user.email === 'string'
        ? user
        : null;
    } catch {
      localStorage.removeItem(this.USER_KEY);
      return null;
    }
  }

  /**
   * Check JWT expiration once per minute.
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