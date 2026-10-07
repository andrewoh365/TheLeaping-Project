import {
  Component,
  OnDestroy,
  OnInit
} from '@angular/core';

import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';

import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  AdminOrderLookup,
  AdminService
} from '../../../services/admin.service';

import { InternalNavComponent } from '../../internal-nav/internal-nav.component';

@Component({
  selector: 'app-trade-lookup',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
    InternalNavComponent
  ],
  templateUrl: './trade-lookup.component.html',
  styleUrls: ['./trade-lookup.component.scss']
})
export class TradeLookupComponent
  implements OnInit, OnDestroy {

  orders: AdminOrderLookup[] = [];

  displayedColumns: string[] = [
    'orderId',
    'tradeId',
    'client',
    'instrument',
    'side',
    'quantity',
    'status',
    'submittedAt'
  ];

  search = '';
  status = '';

  loading = false;
  errorMessage = '';

  private readonly destroy$ =
    new Subject<void>();

  constructor(
    private adminService: AdminService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadOrders();
  }

  applyFilters(): void {
    this.loadOrders();
  }

  resetFilters(): void {
    this.search = '';
    this.status = '';

    this.loadOrders();
  }

  openOrder(
    order: AdminOrderLookup
  ): void {

    this.router.navigate([
      '/admin/orders',
      order.orderId
    ]);
  }

  private loadOrders(): void {
    this.loading = true;
    this.errorMessage = '';

    this.adminService
      .getOrders({
        search: this.search,
        status: this.status
      })
      .pipe(
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: orders => {
          this.orders = orders;
          this.loading = false;
        },

        error: error => {
          console.error(
            'Failed to load admin orders:',
            error
          );

          this.orders = [];
          this.loading = false;

          if (error.status === 403) {
            this.errorMessage =
              'You do not have permission to view trade lookup.';
          } else {
            this.errorMessage =
              'Unable to load trade lookup.';
          }
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}