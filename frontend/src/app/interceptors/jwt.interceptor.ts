import { Injectable } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
  HttpErrorResponse
} from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { catchError, finalize, map, shareReplay, switchMap } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

@Injectable()
export class JwtInterceptor implements HttpInterceptor {
  private refreshRequest$: Observable<string> | null = null;

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    // Add token to request headers
    const token = this.authService.getToken();
    if (token && !this.isExcludedUrl(request.url)) {
      request = this.addToken(request, token);
    }

    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        // Refresh only for an unauthenticated request. A 403 means the
        // session is known but the user is not permitted to access a route.
        if (
          error.status === 401
          && !this.isRefreshUrl(request.url)
          && !this.isExcludedUrl(request.url)
        ) {
          return this.handleAuthError(request, next);
        }
        return throwError(() => error);
      })
    );
  }

  private addToken(request: HttpRequest<any>, token: string): HttpRequest<any> {
    return request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  private handleAuthError(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (!this.refreshRequest$) {
      this.refreshRequest$ = this.authService.refreshToken().pipe(
        map(() => this.authService.getToken()),
        switchMap(token => token
          ? of(token)
          : throwError(() => new Error('Token refresh failed'))
        ),
        catchError((err) => {
          this.forceReauth();
          return throwError(() => err);
        }),
        finalize(() => {
          this.refreshRequest$ = null;
        }),
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }

    return this.refreshRequest$.pipe(
      switchMap(token => next.handle(this.addToken(request, token)))
    );
  }

  private forceReauth(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  private isExcludedUrl(url: string): boolean {
    const excludedUrls = ['/auth/login', '/auth/register'];
    return excludedUrls.some(excluded => url.includes(excluded));
  }

  private isRefreshUrl(url: string): boolean {
    return url.includes('/auth/refresh');
  }

}