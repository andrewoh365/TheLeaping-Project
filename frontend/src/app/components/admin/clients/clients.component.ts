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
  AdminClientSummary,
  AdminService,
  ClientStatus
} from '../../../services/admin.service';

import { InternalNavComponent } from '../../internal-nav/internal-nav.component';

@Component({
  selector: 'app-clients',
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
  templateUrl: './clients.component.html',
  styleUrls: ['./clients.component.scss']
})
export class ClientsComponent
  implements OnInit, OnDestroy {

  clients: AdminClientSummary[] = [];

  displayedColumns: string[] = [
    'clientId',
    'client',
    'email',
    'status'
  ];

  search = '';
  status: ClientStatus | '' = '';

  loading = false;
  errorMessage = '';

  private readonly destroy$ =
    new Subject<void>();

  constructor(
    private adminService: AdminService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadClients();
  }

  applyFilters(): void {
    this.loadClients();
  }

  resetFilters(): void {
    this.search = '';
    this.status = '';

    this.loadClients();
  }

  openClient(
    client: AdminClientSummary
  ): void {

    this.router.navigate([
      '/admin/clients',
      client.clientId
    ]);
  }

  private loadClients(): void {
    this.loading = true;
    this.errorMessage = '';

    this.adminService
      .getClients({
        search: this.search,
        status: this.status
      })
      .pipe(
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: clients => {
          this.clients = clients;
          this.loading = false;
        },

        error: error => {
          console.error(
            'Failed to load admin clients:',
            error
          );

          this.clients = [];
          this.loading = false;

          if (error.status === 403) {
            this.errorMessage =
              'You do not have permission to view client data.';
          } else {
            this.errorMessage =
              'Unable to load clients.';
          }
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}