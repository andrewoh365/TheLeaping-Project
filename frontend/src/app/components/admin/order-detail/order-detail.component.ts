import { Component, OnDestroy, OnInit } from "@angular/core";

import { CommonModule } from "@angular/common";
import { ActivatedRoute, Router } from "@angular/router";

import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatProgressSpinnerModule } from "@angular/material/progress-spinner";

import { Subject } from "rxjs";
import { takeUntil } from "rxjs/operators";

import {
  AdminAuditEvent,
  AdminOrderDetail,
  AdminService,
} from "../../../services/admin.service";

import { InternalNavComponent } from "../../internal-nav/internal-nav.component";

@Component({
  selector: "app-order-detail",
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    InternalNavComponent,
  ],
  templateUrl: "./order-detail.component.html",
  styleUrls: ["./order-detail.component.scss"],
})
export class OrderDetailComponent implements OnInit, OnDestroy {
  order: AdminOrderDetail | null = null;
  auditEvents: AdminAuditEvent[] = [];
  auditTotal = 0;
  private auditPage = 0;
  private auditOrderId: number | null = null;

  loading = false;
  auditLoading = false;
  auditLoadingMore = false;
  auditMoreError = "";

  errorMessage = "";
  auditErrorMessage = "";

  private readonly destroy$ = new Subject<void>();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private adminService: AdminService,
  ) {}

  ngOnInit(): void {
    const orderId = Number(this.route.snapshot.paramMap.get("orderId"));

    if (!orderId || Number.isNaN(orderId)) {
      this.errorMessage = "Invalid order ID.";
      return;
    }

    this.loadOrder(orderId);
  }

  goBack(): void {
    this.router.navigate(["/admin/trade-lookup"]);
  }

  private loadOrder(orderId: number): void {
    this.loading = true;
    this.errorMessage = "";

    this.adminService
      .getOrder(orderId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (order) => {
          this.order = order;
          this.loading = false;

          // AUDIT: Load the order's event history.
          this.loadAudit(orderId);
        },

        error: (error) => {
          console.error("Failed to load order detail:", error);

          this.order = null;
          this.loading = false;

          if (error.status === 404) {
            this.errorMessage = "Order not found.";
          } else if (error.status === 403) {
            this.errorMessage =
              "You do not have permission to view this order.";
          } else {
            this.errorMessage = "Unable to load order details.";
          }
        },
      });
  }

  // AUDIT: Human-readable event labels.
  auditLabel(eventType: string): string {
    const labels: Record<string, string> = {
      ORDER_SUBMITTED: "Order submitted",
      ORDER_ACCEPTED: "Order accepted",
      ORDER_VALIDATED: "Order validated",
      ORDER_REJECTED: "Order rejected",
      ORDER_CANCELLED: "Order cancelled",
      ORDER_EXPIRED: "Order expired",
      PRICE_CAPTURED: "Price captured",
      PRICE_CAPTURE_FAILED: "Price capture failed",
      TRADE_FILLED: "Trade filled",
      CASH_TRANSACTION_RECORDED: "Cash transaction recorded",
      CASH_BALANCE_CHANGED: "Cash balance changed",
      HOLDING_CHANGED: "Holding changed",
    };

    return labels[eventType] || this.humanizeEventType(eventType);
  }

  // AUDIT: Format event details for display.
  auditDetailEntries(event: AdminAuditEvent): Array<{
    label: string;
    value: string;
  }> {
    const fields: Array<[string, string]> = [
      ["status", "Status"],
      ["previousStatus", "Previous status"],
      ["newStatus", "New status"],
      ["attemptStatus", "Attempt status"],
      ["action", "Action"],
      ["transactionType", "Transaction"],
      ["quantity", "Quantity"],
      ["quantityBefore", "Quantity before"],
      ["quantityAfter", "Quantity after"],
      ["amountUsd", "Amount"],
      ["balanceBeforeUsd", "Balance before"],
      ["balanceAfterUsd", "Balance after"],
      ["quotedPrice", "Quoted price"],
      ["quotedPriceUsd", "Quoted price (USD)"],
      ["executionPrice", "Execution price"],
      ["executionPriceUsd", "Execution price (USD)"],
      ["totalUsdValue", "Total value"],
      ["averageCostUsdBefore", "Average cost before"],
      ["averageCostUsdAfter", "Average cost after"],
      ["reason", "Reason"],
      ["sourceType", "Source"],
      ["providerName", "Provider"],
    ];

    return fields
      .map(([key, label]) => ({
        key,
        label,
        value: this.detailValue(event, key),
      }))
      .filter((entry) => entry.value !== undefined && entry.value !== null)
      .map((entry) => ({
        label: entry.label,
        value: this.formatAuditValue(entry.value, entry.key),
      }))
      .slice(0, 8);
  }

  /** Older events are loaded first, so more remain when the trail is longer than one page. */
  get hasMoreAudit(): boolean {
    return this.auditEvents.length < this.auditTotal;
  }

  // AUDIT: Fetch the audit trail for this order (paged, oldest first).
  private loadAudit(orderId: number): void {
    this.auditLoading = true;
    this.auditErrorMessage = "";
    this.auditOrderId = orderId;
    this.auditPage = 0;

    this.adminService
      .getOrderAudit(orderId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (page) => {
          this.auditEvents = page.content;
          this.auditTotal = page.totalElements;
          this.auditPage = page.number;
          this.auditLoading = false;
        },
        error: (error) => {
          console.error("Failed to load order audit:", error);
          this.auditEvents = [];
          this.auditLoading = false;

          if (error.status === 403) {
            this.auditErrorMessage =
              "You do not have permission to view this audit trail.";
          } else {
            this.auditErrorMessage = "Unable to load the audit trail.";
          }
        },
      });
  }

  /** Append the next page of audit events. */
  loadMoreAudit(): void {
    if (this.auditOrderId === null || this.auditLoadingMore || !this.hasMoreAudit) {
      return;
    }
    this.auditLoadingMore = true;
    this.auditMoreError = "";

    this.adminService
      .getOrderAudit(this.auditOrderId, this.auditPage + 1)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (page) => {
          this.auditEvents = [...this.auditEvents, ...page.content];
          this.auditTotal = page.totalElements;
          this.auditPage = page.number;
          this.auditLoadingMore = false;
        },
        error: (error) => {
          console.error("Failed to load more audit events:", error);
          this.auditLoadingMore = false;
          // Keep the events already shown; just report that the next page failed
          this.auditMoreError = "Couldn't load more events. Try again.";
        },
      });
  }

  private detailValue(event: AdminAuditEvent, key: string): unknown {
    return event.eventDetails?.[key];
  }

  private formatAuditValue(value: unknown, key: string): string {
    if (typeof value === "number") {
      const normalizedKey = key.toLowerCase();
      const isUsdMoney =
        normalizedKey.includes("usd") ||
        normalizedKey.includes("amount") ||
        normalizedKey.includes("balance") ||
        normalizedKey.includes("value") ||
        normalizedKey.includes("cost");

      return isUsdMoney
        ? new Intl.NumberFormat("en-US", {
            style: "currency",
            currency: "USD",
            maximumFractionDigits: 2,
          }).format(value)
        : new Intl.NumberFormat("en-US", {
            maximumFractionDigits: 8,
          }).format(value);
    }

    if (
      typeof value === "string" &&
      (key.toLowerCase().endsWith("at") ||
        key.toLowerCase().includes("timestamp"))
    ) {
      const date = new Date(value);
      if (!Number.isNaN(date.getTime())) {
        return date.toLocaleString();
      }
    }

    return String(value);
  }

  private humanizeEventType(eventType: string): string {
    return eventType
      .toLowerCase()
      .split("_")
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(" ");
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
