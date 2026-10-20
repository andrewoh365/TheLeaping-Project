import { Component, OnDestroy, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { ActivatedRoute, Router } from "@angular/router";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatProgressSpinnerModule } from "@angular/material/progress-spinner";
import { MatPaginatorModule, PageEvent } from "@angular/material/paginator";
import { MatTabsModule } from "@angular/material/tabs";
import { Subject } from "rxjs";
import { takeUntil } from "rxjs/operators";
import {
  AdminClientDetail,
  AdminAuditEvent,
  AdminHolding,
  AdminHoldingPage,
  AdminOrderLookup,
  AdminOrderPage,
  AdminService,
} from "../../../services/admin.service";
import { InternalNavComponent } from "../../internal-nav/internal-nav.component";
@Component({
  selector: "app-client-detail",
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatTabsModule,
    InternalNavComponent,
  ],
  templateUrl: "./client-detail.component.html",
  styleUrls: ["./client-detail.component.scss"],
})
export class ClientDetailComponent implements OnInit, OnDestroy {
  client: AdminClientDetail | null = null;
  holdings: AdminHolding[] = [];
  orders: AdminOrderLookup[] = [];
  activity: AdminAuditEvent[] = [];
  activityPage = 0;
  activityPageSize = 10;
  activityTotalElements = 0;
  activityHasMore = false;
  loading = false;
  holdingsLoading = false;
  ordersLoading = false;
  activityLoading = false;
  holdingsPageIndex = 0;
  holdingsPageSize = 100;
  totalHoldings = 0;
  ordersPageIndex = 0;
  ordersPageSize = 100;
  totalOrders = 0;
  private clientId = 0;
  errorMessage = "";
  holdingsErrorMessage = "";
  ordersErrorMessage = "";
  activityErrorMessage = "";
  private readonly destroy$ = new Subject<void>();
  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private adminService: AdminService,
  ) {}
  ngOnInit(): void {
    const clientId = Number(this.route.snapshot.paramMap.get("clientId"));
    if (!clientId) {
      this.errorMessage = "Invalid client ID.";
      return;
    }
    this.clientId = clientId;
    this.loadClient(clientId);
    this.loadHoldings(clientId);
    this.loadOrders(clientId);
    this.loadActivity(clientId);
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
    this.router.navigate(["/admin/clients"]);
  }
  openOrder(order: AdminOrderLookup): void {
    this.router.navigate(["/admin/orders", order.orderId]);
  }
  openActivityOrder(orderId: number | null): void {
    if (orderId !== null) {
      this.router.navigate(["/admin/orders", orderId]);
    }
  }
  loadMoreActivity(): void {
    if (!this.activityLoading && this.activityHasMore) {
      const clientId = Number(this.route.snapshot.paramMap.get("clientId"));
      this.loadActivity(clientId, this.activityPage + 1, true);
    }
  }
  activityLabel(eventType: string): string {
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
  activityInstrument(event: AdminAuditEvent): string {
    const symbol = this.detailValue(event, "symbol");
    const instrumentId = this.detailValue(event, "instrumentId");
    if (typeof symbol === "string" && symbol.trim()) {
      return symbol;
    }
    return instrumentId !== undefined
      ? `Instrument ${instrumentId}`
      : "General account activity";
  }
  activityDetailEntries(event: AdminAuditEvent): Array<{
    label: string;
    value: string;
  }> {
    const fields: Array<[string, string]> = [
      ["status", "Status"],
      ["previousStatus", "Previous status"],
      ["newStatus", "New status"],
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
      .slice(0, 6);
  }
  private loadClient(clientId: number): void {
    this.loading = true;
    this.errorMessage = "";
    this.adminService
      .getClient(clientId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (client) => {
          this.client = client;
          this.loading = false;
        },
        error: (error) => {
          console.error("Failed to load client details:", error);
          this.client = null;
          this.loading = false;
          if (error.status === 404) {
            this.errorMessage = "Client not found.";
          } else if (error.status === 403) {
            this.errorMessage =
              "You do not have permission to view this client.";
          } else {
            this.errorMessage = "Unable to load client details.";
          }
        },
      });
  }
  private loadHoldings(clientId: number): void {
    this.holdingsLoading = true;
    this.holdingsErrorMessage = "";
    this.adminService
      .getClientHoldings(
        clientId,
        this.holdingsPageIndex,
        this.holdingsPageSize,
      )
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response: AdminHoldingPage) => {
          this.holdings = response.holdings;
          this.holdingsPageIndex = response.page;
          this.holdingsPageSize = response.size;
          this.totalHoldings = response.totalElements;
          this.holdingsLoading = false;
        },
        error: (error) => {
          console.error("Failed to load client holdings:", error);
          this.holdings = [];
          this.totalHoldings = 0;
          this.holdingsLoading = false;
          if (error.status === 403) {
            this.holdingsErrorMessage =
              "You do not have permission to view these holdings.";
          } else {
            this.holdingsErrorMessage = "Unable to load client holdings.";
          }
        },
      });
  }
  private loadOrders(clientId: number): void {
    this.ordersLoading = true;
    this.ordersErrorMessage = "";
    this.adminService
      .getClientOrders(clientId, this.ordersPageIndex, this.ordersPageSize)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response: AdminOrderPage) => {
          this.orders = response.orders;
          this.ordersPageIndex = response.page;
          this.ordersPageSize = response.size;
          this.totalOrders = response.totalElements;
          this.ordersLoading = false;
        },
        error: (error) => {
          console.error("Failed to load client orders:", error);
          this.orders = [];
          this.totalOrders = 0;
          this.ordersLoading = false;
          if (error.status === 403) {
            this.ordersErrorMessage =
              "You do not have permission to view these orders.";
          } else {
            this.ordersErrorMessage = "Unable to load client orders.";
          }
        },
      });
  }
  private loadActivity(clientId: number, page = 0, append = false): void {
    this.activityLoading = true;
    this.activityErrorMessage = "";
    this.adminService
      .getClientActivity(clientId, page, this.activityPageSize)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (activityPage) => {
          this.activity = append
            ? [...this.activity, ...activityPage.content]
            : activityPage.content;
          this.activityPage = activityPage.number;
          this.activityTotalElements = activityPage.totalElements;
          this.activityHasMore =
            activityPage.number + 1 < activityPage.totalPages;
          this.activityLoading = false;
        },
        error: (error) => {
          console.error("Failed to load client activity:", error);
          if (!append) {
            this.activity = [];
          }
          this.activityLoading = false;
          if (error.status === 403) {
            this.activityErrorMessage =
              "You do not have permission to view client activity.";
          } else {
            this.activityErrorMessage = "Unable to load client activity.";
          }
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
  private scrollToTop(): void {
    window.scrollTo({
      top: 0,
      behavior: "smooth",
    });
  }
  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
