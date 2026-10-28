import { PageEvent } from '@angular/material/paginator';

import { OpsPagerComponent } from './ops-pager.component';

describe('OpsPagerComponent', () => {
  let pager: OpsPagerComponent;
  let emitted: PageEvent[];

  beforeEach(() => {
    pager = new OpsPagerComponent();
    pager.length = 250;
    pager.pageSize = 100;
    pager.pageIndex = 0;
    emitted = [];
    pager.page.subscribe(event => emitted.push(event));
  });

  it('reports the visible range and page count', () => {
    expect(pager.pageCount).toBe(3);
    expect(pager.rangeStart).toBe(1);
    expect(pager.rangeEnd).toBe(100);

    pager.pageIndex = 2;
    expect(pager.rangeStart).toBe(201);
    expect(pager.rangeEnd).toBe(250);
    expect(pager.hasNext).toBeFalse();
    expect(pager.hasPrevious).toBeTrue();
  });

  it('shows an empty range with a single page when there are no results', () => {
    pager.length = 0;

    expect(pager.pageCount).toBe(1);
    expect(pager.rangeStart).toBe(0);
    expect(pager.rangeEnd).toBe(0);
    expect(pager.hasPrevious).toBeFalse();
    expect(pager.hasNext).toBeFalse();
  });

  it('clamps an out-of-range page index for display', () => {
    pager.pageIndex = 9;

    expect(pager.currentPageIndex).toBe(2);
    expect(pager.rangeStart).toBe(201);
  });

  it('emits a mat-paginator compatible event when moving pages', () => {
    pager.goTo(1);

    expect(emitted).toEqual([
      { pageIndex: 1, pageSize: 100, length: 250, previousPageIndex: 0 }
    ]);
  });

  it('clamps navigation targets and ignores the current page', () => {
    pager.goTo(-1);
    expect(emitted.length).toBe(0);

    pager.goTo(99);
    expect(emitted[0].pageIndex).toBe(2);
  });

  it('does not emit while disabled', () => {
    pager.disabled = true;

    pager.goTo(1);
    pager.changePageSize('25');

    expect(emitted.length).toBe(0);
  });

  it('keeps the first visible row on screen when the page size changes', () => {
    pager.pageIndex = 1;

    pager.changePageSize('25');

    expect(emitted[0].pageIndex).toBe(4);
    expect(emitted[0].pageSize).toBe(25);
  });

  it('ignores invalid or unchanged page sizes', () => {
    pager.changePageSize('100');
    pager.changePageSize('abc');
    pager.changePageSize('0');

    expect(emitted.length).toBe(0);
  });
});
