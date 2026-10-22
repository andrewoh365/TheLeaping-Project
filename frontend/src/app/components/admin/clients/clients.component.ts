import { Component, OnDestroy, OnInit } from "@angular/core";

import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { Router } from "@angular/router";

import { MatIconModule } from "@angular/material/icon";
import { PageEvent } from "@angular/material/paginator";

import { Subject, Subscription } from "rxjs";
import { takeUntil } from "rxjs/operators";

import {
  AdminClientSummary,
  AdminClientPage,
  AdminService,
  ClientStatus,
} from "../../../services/admin.service";

import { InternalLayoutComponent } from "../../internal-layout/internal-layout.component";
import { OpsPagerComponent } from "../../internal-layout/ops-pager.component";
import { minimumLoadingTime } from "../../internal-layout/minimum-loading-time";

interface ClientStatusOption {
  label: string;
  value: ClientStatus | "";
}

@Component({
  selector: "app-clients",
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    InternalLayoutComponent,
    OpsPagerComponent,
  ],
  templateUrl: "./clients.component.html",
  styleUrls: ["./clients.component.scss"],
})
export class ClientsComponent implements OnInit, OnDestroy {
  clients: AdminClientSummary[] = [];

  readonly statusOptions: ClientStatusOption[] = [
    { label: "All", value: "" },
    { label: "Active", value: "ACTIVE" },
    { label: "Inactive", value: "INACTIVE" },
  ];

  readonly skeletonRows = [1, 2, 3, 4, 5, 6];

  search = "";
  private appliedSearch = "";
  status: ClientStatus | "" = "";

  loading = false;
  errorMessage = "";

  pageIndex = 0;
  pageSize = 100;
  totalClients = 0;

  private clientsRequest?: Subscription;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private adminService: AdminService,
    private router: Router,
  ) {}

  get hasActiveFilters(): boolean {
    return this.appliedSearch.length > 0 || this.status !== "";
  }

  ngOnInit(): void {
    this.loadClients();
  }

  applyFilters(): void {
    this.appliedSearch = this.search.trim();
    this.pageIndex = 0;
    this.loadClients();
  }

  setStatus(status: ClientStatus | ""): void {
    if (this.status === status) {
      return;
    }

    this.status = status;
    this.applyFilters();
  }

  clearSearch(): void {
    this.search = "";
    this.applyFilters();
  }

  resetFilters(): void {
    this.search = "";
    this.status = "";
    this.applyFilters();
  }

  retry(): void {
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

  fullName(client: AdminClientSummary): string {
    return [client.firstName, client.lastName]
      .map((part) => part?.trim() ?? "")
      .filter((part) => part.length > 0)
      .join(" ");
  }

  clientInitials(client: AdminClientSummary): string {
    const initials = [client.firstName, client.lastName]
      .map((part) => part?.trim().charAt(0) ?? "")
      .join("");

    if (initials) {
      return initials.toUpperCase();
    }

    const emailInitial = client.email?.trim().charAt(0) ?? "";

    return emailInitial ? emailInitial.toUpperCase() : "?";
  }

  trackByClientId(_index: number, client: AdminClientSummary): number {
    return client.clientId;
  }

  private loadClients(): void {
    this.loading = true;
    this.errorMessage = "";

    // Status filters apply on click, so drop any response still in flight.
    this.clientsRequest?.unsubscribe();

    this.clientsRequest = this.adminService
      .getClients(
        {
          search: this.appliedSearch,
          status: this.status,
        },
        this.pageIndex,
        this.pageSize,
      )
      .pipe(minimumLoadingTime(), takeUntil(this.destroy$))
      .subscribe({
        next: (response: AdminClientPage) => {
          const lastPage = Math.max(0, Math.ceil(response.totalElements / response.size) - 1);
          if (response.totalElements > 0 && response.page > lastPage) {
            this.pageIndex = lastPage;
            this.loadClients();
            return;
          }
          this.clients = response.clients;
          this.pageIndex = response.totalElements === 0 ? 0 : response.page;
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
