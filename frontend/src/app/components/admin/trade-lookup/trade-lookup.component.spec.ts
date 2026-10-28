import { fakeAsync, tick } from "@angular/core/testing";
import { Router } from "@angular/router";

import { Observable, Subject, of, throwError } from "rxjs";

import {
  AdminOrderLookup,
  AdminOrderPage,
  AdminService,
} from "../../../services/admin.service";

import { TradeLookupComponent } from "./trade-lookup.component";

function orderPage(overrides: Partial<AdminOrderPage> = {}): AdminOrderPage {
  return {
    orders: [{ orderId: 10, firstName: "Ada", lastName: null } as AdminOrderLookup],
    page: 0,
    size: 100,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
    ...overrides,
  };
}

describe("TradeLookupComponent", () => {
  let adminService: jasmine.SpyObj<AdminService>;
  let router: jasmine.SpyObj<Router>;
  let component: TradeLookupComponent;

  beforeEach(() => {
    adminService = jasmine.createSpyObj<AdminService>("AdminService", [
      "getOrders",
    ]);
    adminService.getOrders.and.returnValue(of(orderPage()));
    router = jasmine.createSpyObj<Router>("Router", ["navigate"]);
    spyOn(window, "scrollTo");

    component = new TradeLookupComponent(adminService, router);
  });

  afterEach(() => component.ngOnDestroy());

  function lastRequest(): [unknown, number, number] {
    return adminService.getOrders.calls.mostRecent().args as [
      unknown,
      number,
      number,
    ];
  }

  it("returns to the first page when the search or status changes", fakeAsync(() => {
    component.changePage({ pageIndex: 2, pageSize: 50, length: 300 });
    tick(150);

    component.search = " 42 ";
    component.applyFilters();
    tick(150);
    expect(lastRequest()).toEqual([{ search: "42", status: "" }, 0, 100]);

    component.pageIndex = 3;
    component.setStatus("FILLED");
    tick(150);
    expect(lastRequest()).toEqual([{ search: "42", status: "FILLED" }, 0, 100]);
    expect(component.hasActiveFilters).toBeTrue();
  }));

  it("restores the default query when filters are reset", fakeAsync(() => {
    component.search = "AAPL";
    component.setStatus("REJECTED");
    tick(150);

    component.resetFilters();
    tick(150);

    expect(lastRequest()).toEqual([{ search: "", status: "" }, 0, 100]);
    expect(component.hasActiveFilters).toBeFalse();
  }));

  it("moves to the last real page when the requested page is past the end", fakeAsync(() => {
    adminService.getOrders.and.returnValues(
      of(orderPage({ page: 4, size: 50, totalElements: 120, orders: [] })),
      of(orderPage({ page: 2, size: 50, totalElements: 120 })),
    );

    component.changePage({ pageIndex: 4, pageSize: 50, length: 250 });
    tick(150);
    tick(150);

    expect(lastRequest()).toEqual([{ search: "", status: "" }, 2, 50]);
    expect(component.pageIndex).toBe(2);
    expect(component.orders.length).toBe(1);
  }));

  it("ignores a slower earlier response once a newer filter is applied", fakeAsync(() => {
    const slow = new Subject<AdminOrderPage>();
    adminService.getOrders.and.returnValues(
      slow as Observable<AdminOrderPage>,
      of(orderPage({ totalElements: 3 })),
    );

    component.setStatus("FILLED");
    component.setStatus("CANCELLED");
    tick(150);

    slow.next(orderPage({ totalElements: 999 }));
    slow.complete();
    tick(150);

    expect(component.totalOrders).toBe(3);
  }));

  it("shows an error, then recovers on retry", fakeAsync(() => {
    adminService.getOrders.and.returnValue(throwError(() => ({ status: 500 })));
    spyOn(console, "error");

    component.ngOnInit();
    tick(150);
    expect(component.loading).toBeFalse();
    expect(component.orders).toEqual([]);
    expect(component.errorMessage).not.toBe("");

    adminService.getOrders.and.returnValue(of(orderPage()));
    component.retry();
    tick(150);

    expect(component.errorMessage).toBe("");
    expect(component.orders.length).toBe(1);
  }));

  it("opens the order detail page", () => {
    component.openOrder(orderPage().orders[0]);

    expect(router.navigate).toHaveBeenCalledWith(["/admin/orders", 10]);
  });

  it("builds the client name from partial data", () => {
    expect(component.clientName(orderPage().orders[0])).toBe("Ada");
  });
});
