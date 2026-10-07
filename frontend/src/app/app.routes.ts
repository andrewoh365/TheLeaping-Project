import { Routes } from '@angular/router';

import { LoginComponent } from './components/login/login.component';
import { RegisterComponent } from './components/register/register.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { AnalyticsDashboardComponent } from './components/analytics-dashboard/analytics-dashboard.component';
import { ClientsComponent } from './components/admin/clients/clients.component';
import { ClientDetailComponent } from './components/admin/client-detail/client-detail.component';
import { TradeLookupComponent } from './components/admin/trade-lookup/trade-lookup.component';

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
    component: LoginComponent,
    canActivate: [NoAuthGuard]
  },

  {
    path: 'register',
    component: RegisterComponent,
    canActivate: [NoAuthGuard]
  },

  {
    path: 'dashboard',
    component: DashboardComponent,
    canActivate: [AuthGuard]
  },

  {
    path: 'analytics',
    component: AnalyticsDashboardComponent,
    canActivate: [RoleGuard],
    data: {
      roles: ['ADMIN', 'ANALYST']
    }
  },

  {
    path: 'admin',
    component: AnalyticsDashboardComponent,
    canActivate: [RoleGuard],
    data: {
      roles: ['ADMIN']
    }
  },

  {
    path: 'admin/clients',
    component: ClientsComponent,
    canActivate: [RoleGuard],
    data: {
      roles: ['ADMIN']
    }
  },

  {
    path: 'admin/clients/:clientId',
    component: ClientDetailComponent,
    canActivate: [RoleGuard],
    data: {
      roles: ['ADMIN']
    }
  },

  {
    path: 'admin/trade-lookup',
    component: TradeLookupComponent,
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