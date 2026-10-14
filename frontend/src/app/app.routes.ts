
import { Routes } from '@angular/router';

import { AuthGuard } from './guards/auth.guard';
import { NoAuthGuard } from './guards/no-auth.guard';
import { RoleGuard } from './guards/role.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/login',
    pathMatch: 'full'
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./components/login/login.component')
        .then(m => m.LoginComponent),
    canActivate: [NoAuthGuard]
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./components/register/register.component')
        .then(m => m.RegisterComponent),
    canActivate: [NoAuthGuard]
  },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./components/dashboard/dashboard.component')
        .then(m => m.DashboardComponent),
    canActivate: [AuthGuard]
  },
  {
    path: 'analytics',
    loadComponent: () =>
      import('./components/analytics-dashboard/analytics-dashboard.component')
        .then(m => m.AnalyticsDashboardComponent),
    canActivate: [RoleGuard],
    data: { roles: ['ADMIN', 'ANALYST'] }
  },
  {
    path: 'admin',
    loadComponent: () =>
      import('./components/analytics-dashboard/analytics-dashboard.component')
        .then(m => m.AnalyticsDashboardComponent),
    canActivate: [RoleGuard],
    data: { roles: ['ADMIN'] }
  },
  {
    path: 'admin/clients',
    loadComponent: () =>
      import('./components/admin/clients/clients.component')
        .then(m => m.ClientsComponent),
    canActivate: [RoleGuard],
    data: { roles: ['ADMIN'] }
  },
  {
    path: 'admin/clients/:clientId',
    loadComponent: () =>
      import('./components/admin/client-detail/client-detail.component')
        .then(m => m.ClientDetailComponent),
    canActivate: [RoleGuard],
    data: { roles: ['ADMIN'] }
  },
  {
    path: 'admin/orders/:orderId',
    loadComponent: () =>
      import('./components/admin/order-detail/order-detail.component')
        .then(m => m.OrderDetailComponent),
    canActivate: [RoleGuard],
    data: { roles: ['ADMIN'] }
  },
  {
    path: 'admin/trade-lookup',
    loadComponent: () =>
      import('./components/admin/trade-lookup/trade-lookup.component')
        .then(m => m.TradeLookupComponent),
    canActivate: [RoleGuard],
    data: { roles: ['ADMIN'] }
  },
  {
    path: '**',
    redirectTo: '/dashboard'
  }
];
