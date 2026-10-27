import { fakeAsync, tick } from "@angular/core/testing";
import { ActivatedRoute, Router, convertToParamMap } from "@angular/router";

import { of, throwError } from "rxjs";

import {
  AdminOrderDetail,
  AdminService,
} from "../../../services/admin.service";

import { OrderDetailComponent } from "./order-detail.component";

describe("OrderDetailComponent", () => {
  let adminService: jasmine.SpyObj<AdminService>;
  let router: jasmine.SpyObj<Router>;
  let component: OrderDetailComponent;

  const order = { orderId: 7 } as AdminOrderDetail;

  function auditPage(content: unknown[], totalElements: number, number = 0) {
    return of({ content, totalElements, number } as any);
  }

  function createFor(orderId: string | null): void {
    const route = {
      snapshot: { paramMap: convertToParamMap(orderId ? { orderId } : {}) },
    } as unknown as ActivatedRoute;
    component = new OrderDetailComponent(route, router, adminService);
  }

  beforeEach(() => {
    adminService = jasmine.createSpyObj<AdminService>("AdminService", [
      "getOrder",
      "getOrderAudit",
    ]);
    adminService.getOrder.and.returnValue(of(order));
    adminService.getOrderAudit.and.returnValue(auditPage([], 0));
    router = jasmine.createSpyObj<Router>("Router", ["navigate"]);
    spyOn(console, "error");
  });

  afterEach(() => component.ngOnDestroy());

  it("rejects an invalid order id without calling the API", () => {
    createFor("abc");
    component.ngOnInit();

    expect(component.errorMessage).toBe("Invalid order ID.");
    expect(component.loading).toBeFalse();
    expect(adminService.getOrder).not.toHaveBeenCalled();
  });

  it("shows loading, then the order, then loads the audit trail", fakeAsync(() => {
    adminService.getOrderAudit.and.returnValue(auditPage([{}, {}], 5));
    createFor("7");

    component.ngOnInit();
    expect(component.loading).toBeTrue();
    expect(component.order).toBeNull();

    tick(150);
    expect(component.loading).toBeFalse();
    expect(component.order).toBe(order);
    expect(component.auditLoading).toBeTrue();
    expect(adminService.getOrderAudit).toHaveBeenCalledWith(7);

    tick(150);
    expect(component.auditLoading).toBeFalse();
    expect(component.auditEvents.length).toBe(2);
    expect(component.hasMoreAudit).toBeTrue();
  }));

  it("does not load the audit trail when the order fails", fakeAsync(() => {
    adminService.getOrder.and.returnValue(throwError(() => ({ status: 404 })));
    createFor("7");

    component.ngOnInit();
    tick(150);

    expect(component.order).toBeNull();
    expect(component.errorMessage).toBe("Order not found.");
    expect(adminService.getOrderAudit).not.toHaveBeenCalled();
  }));

  it("maps permission and server errors to messages", fakeAsync(() => {
    adminService.getOrder.and.returnValue(throwError(() => ({ status: 403 })));
    createFor("7");
    component.ngOnInit();
    tick(150);
    expect(component.errorMessage).toBe(
      "You do not have permission to view this order.",
    );

    adminService.getOrder.and.returnValue(throwError(() => ({ status: 500 })));
    component.retryOrder();
    tick(150);
    expect(component.errorMessage).toBe("Unable to load order details.");
  }));

  it("recovers from an order error on retry", fakeAsync(() => {
    adminService.getOrder.and.returnValue(throwError(() => ({ status: 500 })));
    createFor("7");
    component.ngOnInit();
    tick(150);

    adminService.getOrder.and.returnValue(of(order));
    component.retryOrder();
    expect(component.errorMessage).toBe("");
    tick(300);

    expect(component.order).toBe(order);
    expect(adminService.getOrder).toHaveBeenCalledTimes(2);
  }));

  it("ignores retry while the order is still loading", fakeAsync(() => {
    createFor("7");
    component.ngOnInit();

    component.retryOrder();
    tick(300);

    expect(adminService.getOrder).toHaveBeenCalledTimes(1);
  }));

  it("keeps the order visible when only the audit trail fails, and retries it", fakeAsync(() => {
    adminService.getOrderAudit.and.returnValue(throwError(() => ({ status: 500 })));
    createFor("7");
    component.ngOnInit();
    tick(300);

    expect(component.order).toBe(order);
    expect(component.errorMessage).toBe("");
    expect(component.auditErrorMessage).toBe("Unable to load the audit trail.");
    expect(component.auditEvents).toEqual([]);

    adminService.getOrderAudit.and.returnValue(auditPage([{}], 1));
    component.retryAudit();
    tick(150);

    expect(component.auditErrorMessage).toBe("");
    expect(component.auditEvents.length).toBe(1);
    expect(component.hasMoreAudit).toBeFalse();
    expect(adminService.getOrder).toHaveBeenCalledTimes(1);
  }));

  it("returns to trade lookup", () => {
    createFor("7");

    component.goBack();

    expect(router.navigate).toHaveBeenCalledWith(["/admin/trade-lookup"]);
  });
});
