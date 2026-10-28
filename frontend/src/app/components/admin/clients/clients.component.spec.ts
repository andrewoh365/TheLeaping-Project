import { fakeAsync, tick } from "@angular/core/testing";
import { Router } from "@angular/router";

import { Observable, Subject, of, throwError } from "rxjs";

import {
  AdminClientPage,
  AdminService,
} from "../../../services/admin.service";

import { ClientsComponent } from "./clients.component";

function clientPage(overrides: Partial<AdminClientPage> = {}): AdminClientPage {
  return {
    clients: [
      {
        clientId: 1,
        firstName: "Ada",
        lastName: "Lovelace",
        email: "ada@example.com",
        status: "ACTIVE",
      },
    ],
    page: 0,
    size: 100,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
    ...overrides,
  };
}

describe("ClientsComponent", () => {
  let adminService: jasmine.SpyObj<AdminService>;
  let router: jasmine.SpyObj<Router>;
  let component: ClientsComponent;

  beforeEach(() => {
    adminService = jasmine.createSpyObj<AdminService>("AdminService", [
      "getClients",
    ]);
    adminService.getClients.and.returnValue(of(clientPage()));
    router = jasmine.createSpyObj<Router>("Router", ["navigate"]);
    spyOn(window, "scrollTo");

    component = new ClientsComponent(adminService, router);
  });

  afterEach(() => component.ngOnDestroy());

  function lastRequest(): [unknown, number, number] {
    return adminService.getClients.calls.mostRecent().args as [
      unknown,
      number,
      number,
    ];
  }

  it("loads the first page with no filters on init", fakeAsync(() => {
    component.ngOnInit();
    expect(component.loading).toBeTrue();

    tick(150);

    expect(lastRequest()).toEqual([{ search: "", status: "" }, 0, 100]);
    expect(component.loading).toBeFalse();
    expect(component.clients.length).toBe(1);
    expect(component.totalClients).toBe(1);
    expect(component.hasActiveFilters).toBeFalse();
  }));

  it("returns to the first page when the search is applied", fakeAsync(() => {
    adminService.getClients.and.returnValue(
      of(clientPage({ page: 2, totalElements: 500 })),
    );
    component.changePage({ pageIndex: 2, pageSize: 100, length: 500 });
    tick(150);
    expect(component.pageIndex).toBe(2);

    adminService.getClients.and.returnValue(of(clientPage()));
    component.search = "  ada  ";
    component.applyFilters();
    tick(150);

    expect(lastRequest()).toEqual([{ search: "ada", status: "" }, 0, 100]);
    expect(component.pageIndex).toBe(0);
    expect(component.hasActiveFilters).toBeTrue();
  }));

  it("returns to the first page when the status filter changes", fakeAsync(() => {
    component.pageIndex = 3;

    component.setStatus("INACTIVE");
    tick(150);

    expect(lastRequest()).toEqual([{ search: "", status: "INACTIVE" }, 0, 100]);
    expect(component.hasActiveFilters).toBeTrue();
  }));

  it("does not reload when the same status is selected again", fakeAsync(() => {
    component.setStatus("ACTIVE");
    tick(150);
    adminService.getClients.calls.reset();

    component.setStatus("ACTIVE");

    expect(adminService.getClients).not.toHaveBeenCalled();
  }));

  it("only sends search text once it is applied", fakeAsync(() => {
    component.search = "typed but not applied";
    component.setStatus("ACTIVE");
    tick(150);

    expect(lastRequest()[0]).toEqual({
      search: "typed but not applied",
      status: "ACTIVE",
    });

    component.search = "draft";
    component.changePage({ pageIndex: 0, pageSize: 50, length: 1 });
    tick(150);

    expect(lastRequest()[0]).toEqual({
      search: "typed but not applied",
      status: "ACTIVE",
    });
  }));

  it("restores the default query when filters are reset", fakeAsync(() => {
    component.search = "ada";
    component.setStatus("ACTIVE");
    tick(150);
    component.pageIndex = 4;

    component.resetFilters();
    tick(150);

    expect(component.search).toBe("");
    expect(component.status).toBe("");
    expect(lastRequest()).toEqual([{ search: "", status: "" }, 0, 100]);
    expect(component.hasActiveFilters).toBeFalse();
  }));

  it("clears only the search text and keeps the status filter", fakeAsync(() => {
    component.search = "ada";
    component.setStatus("INACTIVE");
    tick(150);

    component.clearSearch();
    tick(150);

    expect(lastRequest()).toEqual([{ search: "", status: "INACTIVE" }, 0, 100]);
  }));

  it("requests the page and size chosen in the pager", fakeAsync(() => {
    component.changePage({ pageIndex: 1, pageSize: 25, length: 60 });

    expect(lastRequest()).toEqual([{ search: "", status: "" }, 1, 25]);
    expect(window.scrollTo).toHaveBeenCalled();
    tick(150);
  }));

  it("moves to the last real page when the requested page is past the end", fakeAsync(() => {
    adminService.getClients.and.returnValues(
      of(clientPage({ page: 5, size: 100, totalElements: 150, clients: [] })),
      of(clientPage({ page: 1, size: 100, totalElements: 150 })),
    );

    component.changePage({ pageIndex: 5, pageSize: 100, length: 600 });
    tick(150);
    tick(150);

    expect(adminService.getClients.calls.count()).toBe(2);
    expect(lastRequest()[1]).toBe(1);
    expect(component.pageIndex).toBe(1);
    expect(component.clients.length).toBe(1);
  }));

  it("resets to the first page when there are no results", fakeAsync(() => {
    adminService.getClients.and.returnValue(
      of(clientPage({ page: 3, totalElements: 0, clients: [] })),
    );

    component.changePage({ pageIndex: 3, pageSize: 100, length: 400 });
    tick(150);

    expect(component.pageIndex).toBe(0);
    expect(component.clients).toEqual([]);
    expect(component.totalClients).toBe(0);
  }));

  it("ignores a slower earlier response once a newer filter is applied", fakeAsync(() => {
    const slow = new Subject<AdminClientPage>();
    adminService.getClients.and.returnValues(
      slow as Observable<AdminClientPage>,
      of(clientPage({ totalElements: 7 })),
    );

    component.setStatus("ACTIVE");
    component.setStatus("INACTIVE");
    tick(150);

    slow.next(clientPage({ totalElements: 999 }));
    slow.complete();
    tick(150);

    expect(component.totalClients).toBe(7);
    expect(component.status).toBe("INACTIVE");
  }));

  it("shows a permission message on 403 and clears the table", fakeAsync(() => {
    adminService.getClients.and.returnValue(throwError(() => ({ status: 403 })));
    spyOn(console, "error");

    component.ngOnInit();
    tick(150);

    expect(component.loading).toBeFalse();
    expect(component.clients).toEqual([]);
    expect(component.totalClients).toBe(0);
    expect(component.errorMessage).toBe(
      "You do not have permission to view client data.",
    );
  }));

  it("retries with the same filters after an error", fakeAsync(() => {
    adminService.getClients.and.returnValue(throwError(() => ({ status: 500 })));
    spyOn(console, "error");
    component.setStatus("ACTIVE");
    tick(150);
    expect(component.errorMessage).toBe("Unable to load clients.");

    adminService.getClients.and.returnValue(of(clientPage()));
    component.retry();
    expect(component.errorMessage).toBe("");
    tick(150);

    expect(lastRequest()).toEqual([{ search: "", status: "ACTIVE" }, 0, 100]);
    expect(component.clients.length).toBe(1);
  }));

  it("opens the client detail page", () => {
    component.openClient(clientPage().clients[0]);

    expect(router.navigate).toHaveBeenCalledWith(["/admin/clients", 1]);
  });

  it("builds names and initials from partial client data", () => {
    const base = clientPage().clients[0];

    expect(component.fullName({ ...base, lastName: null })).toBe("Ada");
    expect(component.clientInitials(base)).toBe("AL");
    expect(
      component.clientInitials({ ...base, firstName: null, lastName: null }),
    ).toBe("A");
    expect(
      component.clientInitials({
        ...base,
        firstName: null,
        lastName: null,
        email: null,
      }),
    ).toBe("?");
  });
});
