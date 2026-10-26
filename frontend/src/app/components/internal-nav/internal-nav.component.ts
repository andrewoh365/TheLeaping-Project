import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  Router,
  RouterLink,
  RouterLinkActive
} from '@angular/router';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

import { Subject, of, timer } from 'rxjs';
import { catchError, switchMap, takeUntil } from 'rxjs/operators';

import { AuthService, User } from '../../services/auth.service';
import { MarketService, MarketStatusResponse } from '../../services/market.service';

interface InternalNavLink {
  label: string;
  route: string;
  exact: boolean;
}

@Component({
  selector: 'app-internal-nav',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    RouterLinkActive,
    MatDividerModule,
    MatIconModule,
    MatMenuModule
  ],
  templateUrl: './internal-nav.component.html',
  styleUrls: ['./internal-nav.component.scss']
})
export class InternalNavComponent implements OnInit, OnDestroy {

  currentUser: User | null = null;
  isAdmin = false;
  links: InternalNavLink[] = [];
  marketStatus: MarketStatusResponse | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private authService: AuthService,
    private marketService: MarketService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.isAdmin = this.authService.hasRole('ADMIN');
    this.links = this.isAdmin
      ? [
          { label: 'Overview', route: '/admin', exact: true },
          { label: 'Clients', route: '/admin/clients', exact: false },
          { label: 'Trade lookup', route: '/admin/trade-lookup', exact: false }
        ]
      : [{ label: 'Analytics', route: '/analytics', exact: true }];

    this.authService.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe(user => {
        this.currentUser = user;
      });

    // Same NASDAQ indicator as the customer header, refreshed every minute.
    timer(0, 60_000)
      .pipe(
        switchMap(() =>
          this.marketService
            .getMarketStatus('NASDAQ')
            .pipe(catchError(() => of(null)))
        ),
        takeUntil(this.destroy$)
      )
      .subscribe(status => {
        if (status) {
          this.marketStatus = status;
        }
      });
  }

  get roleLabel(): string {
    return this.isAdmin ? 'Operations' : 'Analytics';
  }

  get displayName(): string {
    const firstName = this.currentUser?.firstName?.trim();
    const lastName = this.currentUser?.lastName?.trim();
    if (firstName && lastName) {
      return `${firstName} ${lastName}`;
    }
    if (firstName) {
      return firstName;
    }

    const emailName = this.currentUser?.email
      ?.split('@')[0]
      ?.replace(/[._-]+/g, ' ')
      .trim();
    if (!emailName) {
      return this.isAdmin ? 'Administrator' : 'Analyst';
    }

    return emailName.replace(/\b\w/g, match => match.toUpperCase());
  }

  get marketStatusLabel(): string {
    return this.marketStatus?.open ? 'Market open' : 'Market closed';
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
