import { Injectable } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivate,
  Router,
  RouterStateSnapshot
} from '@angular/router';

import { AuthService, UserRole } from '../services/auth.service';

@Injectable({
  providedIn: 'root'
})
export class RoleGuard implements CanActivate {

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): boolean {

    if (!this.authService.isAuthenticated()) {
      this.router.navigate(
        ['/login'],
        { queryParams: { returnUrl: state.url } }
      );

      return false;
    }

    const allowedRoles =
      route.data['roles'] as UserRole[] | undefined;

    if (
      allowedRoles &&
      this.authService.hasRole(...allowedRoles)
    ) {
      return true;
    }

    const currentRole =
      this.authService.getCurrentRole();

    if (currentRole === 'CUSTOMER') {
      this.router.navigate(['/dashboard']);
    } else {
      this.router.navigate(['/login']);
    }

    return false;
  }
}