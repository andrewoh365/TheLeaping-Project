import {
  Component,
  OnDestroy,
  OnInit
} from '@angular/core';

import { CommonModule } from '@angular/common';
import {
  ActivatedRoute,
  Router
} from '@angular/router';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';

import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  AdminClientDetail,
  AdminHolding,
  AdminOrderLookup,
  AdminService
} from '../../../services/admin.service';

import {
  InternalNavComponent
} from '../../internal-nav/internal-nav.component';

@Component({
  selector: 'app-client-detail',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatTabsModule,
    InternalNavComponent
  ],
  templateUrl: './client-detail.component.html',
  styleUrls: ['./client-detail.component.scss']
})
export class ClientDetailComponent
  implements OnInit, OnDestroy {

  client: AdminClientDetail | null = null;

  holdings: AdminHolding[] = [];

  orders: AdminOrderLookup[] = [];

  loading = false;
  holdingsLoading = false;
  ordersLoading = false;

  errorMessage = '';
  holdingsErrorMessage = '';
  ordersErrorMessage = '';

  private readonly destroy$ =
    new Subject<void>();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private adminService: AdminService
  ) {}

  ngOnInit(): void {

    const clientId =
      Number(
        this.route.snapshot.paramMap.get('clientId')
      );

    if (!clientId) {
      this.errorMessage =
        'Invalid client ID.';
      return;
    }

    this.loadClient(clientId);
    this.loadHoldings(clientId);
    this.loadOrders(clientId);
  }

  goBack(): void {
    this.router.navigate([
      '/admin/clients'
    ]);
  }

  openOrder(
    order: AdminOrderLookup
  ): void {
    this.router.navigate([
      '/admin/orders',
      order.orderId
    ]);
  }

  private loadClient(
    clientId: number
  ): void {

    this.loading = true;
    this.errorMessage = '';

    this.adminService
      .getClient(clientId)
      .pipe(
        takeUntil(this.destroy$)
      )
      .subscribe({

        next: client => {
          this.client = client;
          this.loading = false;
        },

        error: error => {

          console.error(
            'Failed to load client details:',
            error
          );

          this.client = null;
          this.loading = false;

          if (error.status === 404) {
            this.errorMessage =
              'Client not found.';
          } else if (error.status === 403) {
            this.errorMessage =
              'You do not have permission to view this client.';
          } else {
            this.errorMessage =
              'Unable to load client details.';
          }
        }
      });
  }

  private loadHoldings(
    clientId: number
  ): void {

    this.holdingsLoading = true;
    this.holdingsErrorMessage = '';

    this.adminService
      .getClientHoldings(clientId)
      .pipe(
        takeUntil(this.destroy$)
      )
      .subscribe({

        next: holdings => {
          this.holdings = holdings;
          this.holdingsLoading = false;
        },

        error: error => {

          console.error(
            'Failed to load client holdings:',
            error
          );

          this.holdings = [];
          this.holdingsLoading = false;

          if (error.status === 403) {
            this.holdingsErrorMessage =
              'You do not have permission to view these holdings.';
          } else {
            this.holdingsErrorMessage =
              'Unable to load client holdings.';
          }
        }
      });
  }

  private loadOrders(
    clientId: number
  ): void {

    this.ordersLoading = true;
    this.ordersErrorMessage = '';

    this.adminService
      .getClientOrders(clientId)
      .pipe(
        takeUntil(this.destroy$)
      )
      .subscribe({

        next: orders => {
          this.orders = orders;
          this.ordersLoading = false;
        },

        error: error => {

          console.error(
            'Failed to load client orders:',
            error
          );

          this.orders = [];
          this.ordersLoading = false;

          if (error.status === 403) {
            this.ordersErrorMessage =
              'You do not have permission to view these orders.';
          } else {
            this.ordersErrorMessage =
              'Unable to load client orders.';
          }
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}