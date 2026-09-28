import { Injectable } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
  HttpErrorResponse
} from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

@Injectable()
export class JwtInterceptor implements HttpInterceptor {
  constructor(private authService: AuthService) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    // Add token to request headers
    const token = this.authService.getToken();
    if (token && !this.isExcludedUrl(request.url)) {
      request = this.addToken(request, token);
    }

    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        // Handle 401 Unauthorized - try to refresh token
        if (error.status === 401 && !this.isRefreshUrl(request.url)) {
          return this.handle401Error(request, next);
        } else if (error.status === 403) {
          // Forbidden - clear auth and redirect to login
          this.authService.logout();
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

  private handle401Error(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return this.authService.refreshToken().pipe(
      switchMap(() => {
        const token = this.authService.getToken();
        if (token) {
          return next.handle(this.addToken(request, token));
        }
        this.authService.logout();
        return throwError(() => new Error('Token refresh failed'));
      }),
      catchError((err) => {
        this.authService.logout();
        return throwError(() => err);
      })
    );
  }

  private isExcludedUrl(url: string): boolean {
    const excludedUrls = ['/auth/login', '/auth/register'];
    return excludedUrls.some(excluded => url.includes(excluded));
  }

  private isRefreshUrl(url: string): boolean {
    return url.includes('/auth/refresh');
  }
}
