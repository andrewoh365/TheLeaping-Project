import { fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';

import { BehaviorSubject, of, throwError } from 'rxjs';

import { AuthService, User, UserRole } from '../../services/auth.service';
import { MarketService, MarketStatusResponse } from '../../services/market.service';

import { InternalNavComponent } from './internal-nav.component';

describe('InternalNavComponent', () => {
  let currentUser$: BehaviorSubject<User | null>;
  let authService: jasmine.SpyObj<AuthService>;
  let marketService: jasmine.SpyObj<MarketService>;
  let router: jasmine.SpyObj<Router>;
  let component: InternalNavComponent;

  function createAs(role: UserRole): void {
    authService.hasRole.and.callFake((...roles: UserRole[]) => roles.includes(role));
    component = new InternalNavComponent(authService, marketService, router);
  }

  beforeEach(() => {
    currentUser$ = new BehaviorSubject<User | null>(null);
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['hasRole', 'logout'], {
      currentUser$: currentUser$.asObservable()
    });
    marketService = jasmine.createSpyObj<MarketService>('MarketService', ['getMarketStatus']);
    marketService.getMarketStatus.and.returnValue(of({ open: true } as MarketStatusResponse));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
  });

  afterEach(() => component.ngOnDestroy());

  it('shows the operations links to admins', () => {
    createAs('ADMIN');
    component.ngOnInit();

    expect(component.isAdmin).toBeTrue();
    expect(component.roleLabel).toBe('Operations');
    expect(component.links.map(link => link.route)).toEqual([
      '/admin',
      '/admin/clients',
      '/admin/trade-lookup'
    ]);
  });

  it('shows analysts only the analytics link', () => {
    createAs('ANALYST');
    component.ngOnInit();

    expect(component.isAdmin).toBeFalse();
    expect(component.roleLabel).toBe('Analytics');
    expect(component.links.map(link => link.route)).toEqual(['/analytics']);
    expect(component.links.some(link => link.route.startsWith('/admin'))).toBeFalse();
  });

  it('logs out and returns to the login page', () => {
    createAs('ANALYST');

    component.logout();

    expect(authService.logout).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('builds the display name from the user, falling back to the email', () => {
    createAs('ADMIN');
    component.ngOnInit();

    currentUser$.next({ email: 'x@example.com', firstName: 'Ada', lastName: 'Lovelace' } as User);
    expect(component.displayName).toBe('Ada Lovelace');

    currentUser$.next({ email: 'grace.hopper@example.com' } as User);
    expect(component.displayName).toBe('Grace Hopper');

    currentUser$.next(null);
    expect(component.displayName).toBe('Administrator');
  });

  it('refreshes the market status every minute and keeps the last value on errors', fakeAsync(() => {
    createAs('ADMIN');
    component.ngOnInit();
    tick();
    expect(component.marketStatusLabel).toBe('Market open');

    marketService.getMarketStatus.and.returnValue(throwError(() => new Error('down')));
    tick(60_000);

    expect(marketService.getMarketStatus).toHaveBeenCalledTimes(2);
    expect(component.marketStatusLabel).toBe('Market open');

    component.ngOnDestroy();
  }));
});
