import { Injectable } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivate,
  Router,
  RouterStateSnapshot
} from '@angular/router';

import { AuthService } from '../services/auth.service';

@Injectable({
  providedIn: 'root'
})
export class NoAuthGuard implements CanActivate {

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  canActivate(): boolean {
    if (this.authService.isAuthenticated()) {
      const role = this.authService.getCurrentRole();

      const landingRoute =
        role === 'ADMIN'
          ? '/admin'
          : role === 'ANALYST'
            ? '/analytics'
            : '/dashboard';

      this.router.navigate([landingRoute]);
      return false;
    }

    return true;
  }
}