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
import {
  MatPaginatorModule,
  PageEvent
} from '@angular/material/paginator';
import { MatTabsModule } from '@angular/material/tabs';

import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  AdminClientDetail,
  AdminHolding,
  AdminHoldingPage,
  AdminOrderLookup,
  AdminOrderPage,
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
    MatPaginatorModule,
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

  holdingsPageIndex = 0;
  holdingsPageSize = 100;
  totalHoldings = 0;

  ordersPageIndex = 0;
  ordersPageSize = 100;
  totalOrders = 0;

  private clientId = 0;

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

    this.clientId = clientId;

    this.loadClient(clientId);
    this.loadHoldings(clientId);
    this.loadOrders(clientId);
  }

  changeHoldingsPage(event: PageEvent): void {
    this.holdingsPageIndex = event.pageIndex;
    this.holdingsPageSize = event.pageSize;
    this.scrollToTop();
    this.loadHoldings(this.clientId);
  }

  changeOrdersPage(event: PageEvent): void {
    this.ordersPageIndex = event.pageIndex;
    this.ordersPageSize = event.pageSize;
    this.scrollToTop();
    this.loadOrders(this.clientId);
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
      .getClientHoldings(
        clientId,
        this.holdingsPageIndex,
        this.holdingsPageSize
      )
      .pipe(
        takeUntil(this.destroy$)
      )
      .subscribe({

        next: (response: AdminHoldingPage) => {
          this.holdings = response.holdings;
          this.holdingsPageIndex = response.page;
          this.holdingsPageSize = response.size;
          this.totalHoldings = response.totalElements;
          this.holdingsLoading = false;
        },

        error: error => {

          console.error(
            'Failed to load client holdings:',
            error
          );

          this.holdings = [];
          this.totalHoldings = 0;
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
      .getClientOrders(
        clientId,
        this.ordersPageIndex,
        this.ordersPageSize
      )
      .pipe(
        takeUntil(this.destroy$)
      )
      .subscribe({

        next: (response: AdminOrderPage) => {
          this.orders = response.orders;
          this.ordersPageIndex = response.page;
          this.ordersPageSize = response.size;
          this.totalOrders = response.totalElements;
          this.ordersLoading = false;
        },

        error: error => {

          console.error(
            'Failed to load client orders:',
            error
          );

          this.orders = [];
          this.totalOrders = 0;
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

  private scrollToTop(): void {
    window.scrollTo({
      top: 0,
      behavior: 'smooth'
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}