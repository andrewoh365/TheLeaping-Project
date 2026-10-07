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
  AdminService
} from '../../../services/admin.service';

import { InternalNavComponent } from '../../internal-nav/internal-nav.component';

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
  }

  goBack(): void {
    this.router.navigate([
      '/admin/clients'
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

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}