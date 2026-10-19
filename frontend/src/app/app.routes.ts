
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
    loadComponent: () => import('./components/login/login.component')
      .then(component => component.LoginComponent),
    canActivate: [NoAuthGuard]
  },

  {
    path: 'register',
    loadComponent: () => import('./components/register/register.component')
      .then(component => component.RegisterComponent),
    canActivate: [NoAuthGuard]
  },

  {
    path: 'dashboard',
    loadComponent: () => import('./components/dashboard/dashboard.component')
      .then(component => component.DashboardComponent),
    canActivate: [AuthGuard]
  },

  {
    path: 'analytics',
    loadComponent: () => import('./components/analytics-dashboard/analytics-dashboard.component')
      .then(component => component.AnalyticsDashboardComponent),
    canActivate: [RoleGuard],
    data: {
      roles: ['ADMIN', 'ANALYST']
    }
  },

  {
    path: 'admin',
    loadComponent: () => import('./components/analytics-dashboard/analytics-dashboard.component')
      .then(component => component.AnalyticsDashboardComponent),
    canActivate: [RoleGuard],
    data: {
      roles: ['ADMIN']
    }
  },

  {
    path: 'admin/clients',
    loadComponent: () => import('./components/admin/clients/clients.component')
      .then(component => component.ClientsComponent),
    canActivate: [RoleGuard],
    data: {
      roles: ['ADMIN']
    }
  },

  {
    path: 'admin/clients/:clientId',
    loadComponent: () => import('./components/admin/client-detail/client-detail.component')
      .then(component => component.ClientDetailComponent),
    canActivate: [RoleGuard],
    data: {
      roles: ['ADMIN']
    }
  },

  {
    path: 'admin/orders/:orderId',
    loadComponent: () => import('./components/admin/order-detail/order-detail.component')
      .then(component => component.OrderDetailComponent),
    canActivate: [RoleGuard],
    data: {
      roles: ['ADMIN']
    }
  },

  {
    path: 'admin/trade-lookup',
    loadComponent: () => import('./components/admin/trade-lookup/trade-lookup.component')
      .then(component => component.TradeLookupComponent),
    canActivate: [RoleGuard],
    data: {
      roles: ['ADMIN']
    }
  },

  {
    path: '**',
    redirectTo: '/dashboard'
  }
];
