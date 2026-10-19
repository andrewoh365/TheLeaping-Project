import { Component, OnDestroy, OnInit } from "@angular/core";

import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { Router } from "@angular/router";

import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatProgressSpinnerModule } from "@angular/material/progress-spinner";
import { MatSelectModule } from "@angular/material/select";
import { MatTableModule } from "@angular/material/table";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";

import { Subject } from "rxjs";
import { takeUntil } from "rxjs/operators";

import {
  AdminClientSummary,
  AdminClientPage,
  AdminService,
  ClientStatus,
} from "../../../services/admin.service";

import { InternalNavComponent } from "../../internal-nav/internal-nav.component";

@Component({
  selector: "app-clients",
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
    InternalNavComponent,
  ],
  templateUrl: "./clients.component.html",
  styleUrls: ["./clients.component.scss"],
})
export class ClientsComponent implements OnInit, OnDestroy {
  clients: AdminClientSummary[] = [];

  displayedColumns: string[] = ["clientId", "client", "email", "status"];

  search = "";
  status: ClientStatus | "" = "";

  loading = false;
  errorMessage = "";

  pageIndex = 0;
  pageSize = 100;
  totalClients = 0;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private adminService: AdminService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.loadClients();
  }

  applyFilters(): void {
    this.pageIndex = 0;
    this.loadClients();
  }

  resetFilters(): void {
    this.search = "";
    this.status = "";
    this.pageIndex = 0;

    this.loadClients();
  }

  changePage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;

    window.scrollTo({
      top: 0,
      behavior: "smooth",
    });

    this.loadClients();
  }

  openClient(client: AdminClientSummary): void {
    this.router.navigate(["/admin/clients", client.clientId]);
  }

  private loadClients(): void {
    this.loading = true;
    this.errorMessage = "";

    this.adminService
      .getClients(
        {
          search: this.search,
          status: this.status,
        },
        this.pageIndex,
        this.pageSize,
      )
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response: AdminClientPage) => {
          this.clients = response.clients;
          this.pageIndex = response.page;
          this.pageSize = response.size;
          this.totalClients = response.totalElements;
          this.loading = false;
        },

        error: (error) => {
          console.error("Failed to load admin clients:", error);

          this.clients = [];
          this.totalClients = 0;
          this.loading = false;

          if (error.status === 403) {
            this.errorMessage =
              "You do not have permission to view client data.";
          } else {
            this.errorMessage = "Unable to load clients.";
          }
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
