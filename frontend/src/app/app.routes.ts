import { Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { RegisterComponent } from './components/register/register.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { AuthGuard } from './guards/auth.guard';
import { NoAuthGuard } from './guards/no-auth.guard';
import { InvestComponent } from './components/invest/invest.component';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/invest',
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
    component: DashboardComponent
  },
  {
    path: 'invest',
    component: InvestComponent
  },
  {
    path: '**',
    redirectTo: '/dashboard'
  }
];
