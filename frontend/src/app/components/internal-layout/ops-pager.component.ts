import {
  Component,
  EventEmitter,
  Input,
  Output
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { PageEvent } from '@angular/material/paginator';

/**
 * Compact pager for operations tables. Emits the same PageEvent shape as
 * mat-paginator so existing page handlers work unchanged.
 */
@Component({
  selector: 'app-ops-pager',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './ops-pager.component.html',
  styleUrls: ['./ops-pager.component.scss']
})
export class OpsPagerComponent {

  @Input() length = 0;
  @Input() pageIndex = 0;
  @Input() pageSize = 100;
  @Input() pageSizeOptions: number[] = [25, 50, 100];
  @Input() itemLabel = 'results';
  @Input() disabled = false;

  @Output() page = new EventEmitter<PageEvent>();

  get currentPageIndex(): number {
    return Math.min(Math.max(this.pageIndex, 0), this.pageCount - 1);
  }

  get pageCount(): number {
    return Math.max(1, Math.ceil(this.length / this.pageSize));
  }

  get rangeStart(): number {
    return this.length === 0 ? 0 : this.currentPageIndex * this.pageSize + 1;
  }

  get rangeEnd(): number {
    return Math.min(this.length, (this.currentPageIndex + 1) * this.pageSize);
  }

  get hasPrevious(): boolean {
    return this.currentPageIndex > 0;
  }

  get hasNext(): boolean {
    return this.currentPageIndex + 1 < this.pageCount;
  }

  goTo(pageIndex: number): void {
    const target = Math.min(Math.max(pageIndex, 0), this.pageCount - 1);
    if (this.disabled || target === this.pageIndex) {
      return;
    }

    this.emit(target, this.pageSize);
  }

  changePageSize(value: string): void {
    const pageSize = Number(value);
    if (this.disabled || !Number.isInteger(pageSize) || pageSize <= 0 || pageSize === this.pageSize) {
      return;
    }

    // Keep the first visible row on screen after the size change.
    const firstRow = this.currentPageIndex * this.pageSize;
    this.emit(Math.floor(firstRow / pageSize), pageSize);
  }

  private emit(pageIndex: number, pageSize: number): void {
    this.page.emit({
      pageIndex,
      pageSize,
      length: this.length,
      previousPageIndex: this.pageIndex
    });
  }
}
