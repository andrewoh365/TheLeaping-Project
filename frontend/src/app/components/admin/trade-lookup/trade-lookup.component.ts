import { Component, OnDestroy, OnInit } from "@angular/core";

import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { Router } from "@angular/router";

import { MatIconModule } from "@angular/material/icon";
import { PageEvent } from "@angular/material/paginator";

import { Subject, Subscription } from "rxjs";
import { takeUntil } from "rxjs/operators";

import {
  AdminOrderLookup,
  AdminOrderPage,
  AdminService,
} from "../../../services/admin.service";

import { InternalLayoutComponent } from "../../internal-layout/internal-layout.component";
import { OpsPagerComponent } from "../../internal-layout/ops-pager.component";
import { minimumLoadingTime } from "../../internal-layout/minimum-loading-time";

interface OrderStatusOption {
  label: string;
  value: string;
}

@Component({
  selector: "app-trade-lookup",
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    InternalLayoutComponent,
    OpsPagerComponent,
  ],
  templateUrl: "./trade-lookup.component.html",
  styleUrls: ["./trade-lookup.component.scss"],
})
export class TradeLookupComponent implements OnInit, OnDestroy {
  orders: AdminOrderLookup[] = [];

  readonly statusOptions: OrderStatusOption[] = [
    { label: "All", value: "" },
    { label: "Submitted", value: "SUBMITTED" },
    { label: "Accepted", value: "ACCEPTED" },
    { label: "Filled", value: "FILLED" },
    { label: "Rejected", value: "REJECTED" },
    { label: "Cancelled", value: "CANCELLED" },
    { label: "Expired", value: "EXPIRED" },
  ];

  readonly skeletonRows = [1, 2, 3, 4, 5, 6];

  search = "";
  private appliedSearch = "";
  status = "";

  loading = false;
  errorMessage = "";

  pageIndex = 0;
  pageSize = 100;
  totalOrders = 0;

  private ordersRequest?: Subscription;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private adminService: AdminService,
    private router: Router,
  ) {}

  get hasActiveFilters(): boolean {
    return this.appliedSearch.length > 0 || this.status !== "";
  }

  ngOnInit(): void {
    this.loadOrders();
  }

  applyFilters(): void {
    this.appliedSearch = this.search.trim();
    this.pageIndex = 0;
    this.loadOrders();
  }

  setStatus(status: string): void {
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
    this.loadOrders();
  }

  changePage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    window.scrollTo({
      top: 0,
      behavior: "smooth",
    });
    this.loadOrders();
  }

  openOrder(order: AdminOrderLookup): void {
    this.router.navigate(["/admin/orders", order.orderId]);
  }

  clientName(order: AdminOrderLookup): string {
    return [order.firstName, order.lastName]
      .map((part) => part?.trim() ?? "")
      .filter((part) => part.length > 0)
      .join(" ");
  }

  trackByOrderId(_index: number, order: AdminOrderLookup): number {
    return order.orderId;
  }

  private loadOrders(): void {
    this.loading = true;
    this.errorMessage = "";

    // Status filters apply on click, so drop any response still in flight.
    this.ordersRequest?.unsubscribe();

    this.ordersRequest = this.adminService
      .getOrders(
        {
          search: this.appliedSearch,
          status: this.status,
        },
        this.pageIndex,
        this.pageSize,
      )
      .pipe(minimumLoadingTime(), takeUntil(this.destroy$))
      .subscribe({
        next: (response: AdminOrderPage) => {
          const lastPage = Math.max(0, Math.ceil(response.totalElements / response.size) - 1);
          if (response.totalElements > 0 && response.page > lastPage) {
            this.pageIndex = lastPage;
            this.loadOrders();
            return;
          }
          this.orders = response.orders;
          this.pageIndex = response.totalElements === 0 ? 0 : response.page;
          this.pageSize = response.size;
          this.totalOrders = response.totalElements;
          this.loading = false;
        },

        error: (error) => {
          console.error("Failed to load admin orders:", error);

          this.orders = [];
          this.totalOrders = 0;
          this.loading = false;

          if (error.status === 403) {
            this.errorMessage =
              "You do not have permission to view trade lookup.";
          } else {
            this.errorMessage = "Unable to load trade lookup.";
          }
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
