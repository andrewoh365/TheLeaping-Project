import {
  Component,
  OnDestroy,
  OnInit
} from '@angular/core';

import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  AdminOrderDetail,
  AdminService
} from '../../../services/admin.service';

import {
  InternalNavComponent
} from '../../internal-nav/internal-nav.component';

@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    InternalNavComponent
  ],
  templateUrl: './order-detail.component.html',
  styleUrls: ['./order-detail.component.scss']
})
export class OrderDetailComponent
  implements OnInit, OnDestroy {

  order: AdminOrderDetail | null = null;

  loading = false;
  errorMessage = '';

  private readonly destroy$ =
    new Subject<void>();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private adminService: AdminService
  ) {}

  ngOnInit(): void {

    const orderId =
      Number(
        this.route.snapshot.paramMap.get('orderId')
      );

    if (!orderId || Number.isNaN(orderId)) {
      this.errorMessage =
        'Invalid order ID.';
      return;
    }

    this.loadOrder(orderId);
  }

  goBack(): void {
    this.router.navigate([
      '/admin/trade-lookup'
    ]);
  }

  private loadOrder(
    orderId: number
  ): void {

    this.loading = true;
    this.errorMessage = '';

    this.adminService
      .getOrder(orderId)
      .pipe(
        takeUntil(this.destroy$)
      )
      .subscribe({

        next: order => {
          this.order = order;
          this.loading = false;
        },

        error: error => {

          console.error(
            'Failed to load order detail:',
            error
          );

          this.order = null;
          this.loading = false;

          if (error.status === 404) {
            this.errorMessage =
              'Order not found.';
          } else if (error.status === 403) {
            this.errorMessage =
              'You do not have permission to view this order.';
          } else {
            this.errorMessage =
              'Unable to load order details.';
          }
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}