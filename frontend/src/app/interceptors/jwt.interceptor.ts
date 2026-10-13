import { Injectable } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
  HttpErrorResponse
} from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

@Injectable()
export class JwtInterceptor implements HttpInterceptor {
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
        // Some backends return 403 for expired/invalid JWTs.
        if (
          (error.status === 401 || error.status === 403)
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
    return this.authService.refreshToken().pipe(
      switchMap(() => {
        const token = this.authService.getToken();
        if (token) {
          return next.handle(this.addToken(request, token));
        }
        this.forceReauth();
        return throwError(() => new Error('Token refresh failed'));
      }),
      catchError((err) => {
        this.forceReauth();
        return throwError(() => err);
      })
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