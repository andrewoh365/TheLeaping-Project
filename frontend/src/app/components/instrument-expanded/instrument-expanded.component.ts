import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, HostListener, Inject, OnDestroy, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { InstrumentDetail, InstrumentService, PricePointDto } from '../../services/instrument.service';
import { WatchlistResponse, WatchlistService } from '../../services/watchlist.service';
import { forkJoin, Observable, of, Subject } from 'rxjs';
import { catchError, finalize, map, switchMap, takeUntil } from 'rxjs/operators';

export interface InstrumentExpandedData {
  instrumentId: number;
  symbol: string;
  isActive: boolean;
  name: string;
  instrumentType: string;
  priceCurrencyCode: string;
  price?: number | null;
  lastUpdated?: string | null;
  marketName?: string;
  country?: string;
  timezone?: string;
  openTime?: string;
  closeTime?: string;
  isTradeable: boolean;
}

export interface InstrumentTradeAction {
  action: 'BUY' | 'SELL';
  instrument: InstrumentExpandedData;
}

interface WatchlistMembership {
  watchlist: WatchlistResponse;
  contains: boolean;
}

interface AxisTick {
  position: number;
  label: string;
  y?: number;
}

type ChartRange = '1D' | '1W' | '1M';

const CHART_WIDTH = 1000;
const CHART_HEIGHT = 260;
const CHART_INSET = 10;

@Component({
  selector: 'app-instrument-expanded',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule],
  templateUrl: './instrument-expanded.component.html',
  styleUrl: './instrument-expanded.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class InstrumentExpandedComponent implements OnInit, OnDestroy {
  private static readonly MAX_CHART_POINTS = 240;
  private readonly destroy$ = new Subject<void>();
  private requestVersion = 0;
  private domainMin = 0;
  private domainMax = 1;
  readonly chartRanges: ChartRange[] = ['1D', '1W', '1M'];
  activeChartRange: ChartRange = '1M';

  selectedPrice: number | null = null;
  selectedLabel = '';
  rangeChange: number | null = null;
  rangeChangePercent: number | null = null;
  isLoading = false;
  hasChart = false;
  linePath = '';
  areaPath = '';
  yTicks: AxisTick[] = [];
  xTicks: AxisTick[] = [];
  markerX: number | null = null;
  private chartPrices: PricePointDto[] = [];

  // Watchlist menu: which of the user's lists hold this instrument
  watchlistMemberships: WatchlistMembership[] = [];
  watchlistMenuOpen = false;
  watchlistLoaded = false;
  watchlistBusyId: number | null = null;
  watchlistCreating = false;
  newWatchlistName = '';
  watchlistError = '';

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: InstrumentExpandedData,
    private instrumentService: InstrumentService,
    private watchlistService: WatchlistService,
    private dialogRef: MatDialogRef<InstrumentExpandedComponent>,
    private cdr: ChangeDetectorRef,
    private host: ElementRef<HTMLElement>
  ) {}

  ngOnInit(): void {
    this.loadInstrumentDetail();
    this.loadPriceHistory();
    this.loadWatchlistMemberships();
  }

  get onAnyWatchlist(): boolean {
    return this.watchlistMemberships.some((membership) => membership.contains);
  }

  toggleWatchlistMenu(): void {
    this.watchlistMenuOpen = !this.watchlistMenuOpen;
    this.watchlistError = '';
    this.newWatchlistName = '';
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.watchlistMenuOpen) {
      return;
    }
    const menu = this.host.nativeElement.querySelector('.watchlist-control');
    if (menu && !menu.contains(event.target as Node)) {
      this.watchlistMenuOpen = false;
      this.cdr.markForCheck();
    }
  }

  /** Add this instrument to the list, or take it off if it's already there. */
  toggleWatchlist(membership: WatchlistMembership): void {
    if (this.watchlistBusyId !== null) {
      return;
    }
    const watchlistId = membership.watchlist.watchlistId;
    const request: Observable<unknown> = membership.contains
      ? this.watchlistService.removeInstrument(watchlistId, this.data.instrumentId)
      : this.watchlistService.addInstrument(watchlistId, this.data.instrumentId);

    this.watchlistBusyId = watchlistId;
    this.watchlistError = '';
    request
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.watchlistBusyId = null;
          this.cdr.markForCheck();
        })
      )
      .subscribe({
        next: () => {
          membership.contains = !membership.contains;
          this.watchlistService.notifyChanged();
        },
        error: () => {
          this.watchlistError = membership.contains
            ? `Couldn't remove ${this.data.symbol} from ${membership.watchlist.watchlistName}.`
            : `Couldn't add ${this.data.symbol} to ${membership.watchlist.watchlistName}.`;
        }
      });
  }

  /** Create a new list and put this instrument straight on it. */
  createWatchlistWithInstrument(): void {
    const name = this.newWatchlistName.trim() || 'My Watchlist';
    if (this.watchlistCreating) {
      return;
    }
    this.watchlistCreating = true;
    this.watchlistError = '';
    this.watchlistService.createWatchlist({ watchlistName: name })
      .pipe(
        switchMap((watchlist) => this.watchlistService.addInstrument(watchlist.watchlistId, this.data.instrumentId)
          .pipe(map(() => watchlist))),
        takeUntil(this.destroy$),
        finalize(() => {
          this.watchlistCreating = false;
          this.cdr.markForCheck();
        })
      )
      .subscribe({
        next: (watchlist) => {
          this.watchlistMemberships = [...this.watchlistMemberships, { watchlist, contains: true }];
          this.newWatchlistName = '';
          this.watchlistService.notifyChanged();
        },
        error: () => {
          this.watchlistError = "Couldn't create that watchlist. Try a different name.";
        }
      });
  }

  private loadWatchlistMemberships(): void {
    this.watchlistService.getWatchlists()
      .pipe(
        switchMap((watchlists) => watchlists.length === 0
          ? of<WatchlistMembership[]>([])
          : forkJoin(watchlists.map((watchlist) =>
              this.watchlistService.getWatchlistInstruments(watchlist.watchlistId).pipe(
                map((instruments) => ({
                  watchlist,
                  contains: instruments.some((instrument) => instrument.instrumentId === this.data.instrumentId)
                })),
                catchError(() => of({ watchlist, contains: false }))
              )))),
        catchError(() => of<WatchlistMembership[]>([])),
        takeUntil(this.destroy$)
      )
      .subscribe((memberships) => {
        this.watchlistMemberships = memberships;
        this.watchlistLoaded = true;
        this.cdr.markForCheck();
      });
  }

  setChartRange(range: ChartRange): void {
    if (this.activeChartRange === range) {
      return;
    }

    this.activeChartRange = range;
    this.clearSelection();
    this.rangeChange = null;
    this.rangeChangePercent = null;
    this.clearChart();
    this.loadPriceHistory();
  }

  get rangeFell(): boolean {
    return (this.rangeChange ?? 0) < 0;
  }

  get rangeCaption(): string {
    switch (this.activeChartRange) {
      case '1D': return 'today';
      case '1W': return 'past week';
      case '1M': return 'past month';
    }
  }

  get sessionLabel(): string {
    const open = this.formatSessionTime(this.data.openTime);
    const close = this.formatSessionTime(this.data.closeTime);
    if (open && close) {
      return `${open} – ${close}`;
    }
    return open || close || '—';
  }

  formatSignedMoney(value: number | null): string {
    if (value === null || !Number.isFinite(value)) {
      return '';
    }
    const formatted = new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(Math.abs(value));
    if (value > 0) {
      return `+${formatted}`;
    }
    if (value < 0) {
      return `−${formatted}`;
    }
    return formatted;
  }

  formatSignedPercent(value: number | null): string {
    if (value === null || !Number.isFinite(value)) {
      return '';
    }
    const formatted = Math.abs(value).toFixed(2);
    if (value > 0) {
      return `+${formatted}%`;
    }
    if (value < 0) {
      return `−${formatted}%`;
    }
    return `${formatted}%`;
  }

  selectChartPoint(event: MouseEvent): void {
    if (this.chartPrices.length < 2) {
      return;
    }
    const svg = event.currentTarget as SVGSVGElement;
    const bounds = svg.getBoundingClientRect();
    if (bounds.width <= 0) {
      return;
    }
    const ratio = Math.min(1, Math.max(0, (event.clientX - bounds.left) / bounds.width));
    const index = Math.round(ratio * (this.chartPrices.length - 1));
    const point = this.chartPrices[index];
    this.selectedPrice = point.price;
    this.selectedLabel = this.formatChartLabel(point.timestamp);
    this.markerX = (index / (this.chartPrices.length - 1)) * CHART_WIDTH;
    this.cdr.markForCheck();
  }

  private loadInstrumentDetail(): void {
    this.instrumentService.getInstrumentById(String(this.data.instrumentId))
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (detail: InstrumentDetail) => {
          // Detail price is the stored native print. The chart and header use the USD history.
          Object.assign(this.data, detail, {
            price: this.data.price,
            lastUpdated: this.data.lastUpdated
          });
          this.cdr.markForCheck();
        },
        error: (error) => {
          console.error('Failed to load instrument detail', error);
        }
      });
  }

  loadPriceHistory(): void {
    this.isLoading = true;
    this.cdr.markForCheck();
    const requestVersion = ++this.requestVersion;

    const to = new Date();
    const from = new Date();
    from.setDate(from.getDate() - this.getRangeDays(this.activeChartRange));

    this.instrumentService.getPriceHistory(
      this.data.symbol,
      from.toISOString(),
      to.toISOString()
    )
    .pipe(takeUntil(this.destroy$))
    .subscribe({
      next: (prices: PricePointDto[]) => {
        if (requestVersion !== this.requestVersion) {
          return;
        }

        const rangePrices = this.activeChartRange === '1D'
          ? prices
          : this.keepLatestPricePerDay(prices);
        const sampledPrices = this.downsamplePrices(rangePrices, InstrumentExpandedComponent.MAX_CHART_POINTS);
        this.chartPrices = sampledPrices;
        this.setRangeChange(sampledPrices);
        const latestPrice = prices[prices.length - 1];
        if (latestPrice) {
          this.data.price = latestPrice.price;
          this.data.lastUpdated = latestPrice.timestamp;
        }
        this.paintChart(sampledPrices);
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (requestVersion !== this.requestVersion) {
          return;
        }

        console.error('Failed to load price history', err);
        this.clearChart();
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  private getRangeDays(range: ChartRange): number {
    switch (range) {
      case '1D': return 1;
      case '1W': return 7;
      case '1M': return 30;
    }
  }

  private paintChart(prices: PricePointDto[]): void {
    if (prices.length < 2) {
      this.clearChart();
      return;
    }

    const values = prices.map((point) => point.price);
    const ticks = this.niceTicks(Math.min(...values), Math.max(...values));
    this.domainMin = ticks[0];
    this.domainMax = ticks[ticks.length - 1];
    const step = ticks.length > 1 ? ticks[1] - ticks[0] : 1;
    this.yTicks = ticks.map((tick) => {
      const y = this.chartY(tick);
      return {
        y,
        position: y / CHART_HEIGHT,
        label: this.formatAxisPrice(tick, step)
      };
    });
    this.xTicks = this.buildXTicks(prices);
    this.linePath = values.map((value, index) => {
      const x = index / (values.length - 1) * CHART_WIDTH;
      return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${this.chartY(value).toFixed(2)}`;
    }).join(' ');
    this.areaPath = `${this.linePath} L ${CHART_WIDTH} ${CHART_HEIGHT} L 0 ${CHART_HEIGHT} Z`;
    this.hasChart = true;
  }

  private clearChart(): void {
    this.hasChart = false;
    this.linePath = '';
    this.areaPath = '';
    this.yTicks = [];
    this.xTicks = [];
    this.chartPrices = [];
    this.clearSelection();
  }

  private clearSelection(): void {
    this.selectedPrice = null;
    this.selectedLabel = '';
    this.markerX = null;
  }

  private chartY(value: number): number {
    const fraction = this.domainMax > this.domainMin
      ? (this.domainMax - value) / (this.domainMax - this.domainMin)
      : 0.5;
    return CHART_INSET + fraction * (CHART_HEIGHT - 2 * CHART_INSET);
  }

  private niceTicks(min: number, max: number, targetSteps = 4): number[] {
    if (max - min < 1e-9) {
      const pad = Math.max(Math.abs(max) * 0.001, 1);
      min -= pad;
      max += pad;
    }
    const rawStep = (max - min) / targetSteps;
    const magnitude = 10 ** Math.floor(Math.log10(rawStep));
    const step = [1, 2, 2.5, 5, 10]
      .map((multiple) => multiple * magnitude)
      .find((candidate) => candidate >= rawStep) ?? 10 * magnitude;
    const decimals = Math.max(0, -Math.floor(Math.log10(step)) + 1);
    const first = Math.floor(min / step) * step;
    const ticks: number[] = [];
    for (let tick = first; tick < max + step - 1e-9; tick += step) {
      ticks.push(Number(tick.toFixed(decimals)));
    }
    return ticks;
  }

  private formatAxisPrice(value: number, step: number): string {
    const compact = Math.abs(value) >= 100_000;
    const unit = !compact ? 1 : Math.abs(value) >= 1e9 ? 1e9 : Math.abs(value) >= 1e6 ? 1e6 : 1e3;
    const digits = Math.min(2, Math.max(0, Math.ceil(-Math.log10(step / unit) - 1e-9)));
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      notation: compact ? 'compact' : 'standard',
      minimumFractionDigits: digits,
      maximumFractionDigits: digits
    }).format(value);
  }

  private buildXTicks(prices: PricePointDto[]): AxisTick[] {
    const count = prices.length;
    const ticks: AxisTick[] = [];
    for (let slot = 0; slot < 5; slot++) {
      const index = Math.round(slot * (count - 1) / 4);
      const position = index / (count - 1);
      if (position < 0.06 || position > 0.94) {
        continue;
      }
      ticks.push({
        position,
        label: this.formatChartLabel(prices[index].timestamp)
      });
    }
    return ticks;
  }

  private downsamplePrices(prices: PricePointDto[], maxPoints: number): PricePointDto[] {
    if (prices.length <= maxPoints || maxPoints < 2) {
      return prices;
    }

    const step = (prices.length - 1) / (maxPoints - 1);
    const sampled: PricePointDto[] = [];

    for (let index = 0; index < maxPoints; index++) {
      const sourceIndex = Math.round(index * step);
      sampled.push(prices[sourceIndex]);
    }

    return sampled;
  }

  private keepLatestPricePerDay(prices: PricePointDto[]): PricePointDto[] {
    const latestByDay = new Map<string, PricePointDto>();

    for (const price of prices) {
      const day = new Date(price.timestamp).toISOString().slice(0, 10);
      latestByDay.set(day, price);
    }

    return Array.from(latestByDay.values()).sort((left, right) => {
      return new Date(left.timestamp).getTime() - new Date(right.timestamp).getTime();
    });
  }

  private setRangeChange(prices: PricePointDto[]): void {
    if (prices.length < 2) {
      this.rangeChange = null;
      this.rangeChangePercent = null;
      return;
    }
    const first = prices[0].price;
    const last = prices[prices.length - 1].price;
    this.rangeChange = last - first;
    this.rangeChangePercent = first ? ((last - first) / first) * 100 : null;
  }

  private formatSessionTime(value?: string): string {
    if (!value) {
      return '';
    }
    const match = /^(\d{1,2}):(\d{2})/.exec(value);
    if (!match) {
      return value;
    }
    const hours = Number(match[1]);
    const suffix = hours >= 12 ? 'PM' : 'AM';
    const hour12 = hours % 12 || 12;
    return `${hour12}:${match[2]} ${suffix}`;
  }

  private formatChartLabel(timestamp: string): string {
    const date = new Date(timestamp);

    if (this.activeChartRange === '1D') {
      return date.toLocaleTimeString('en-US', {
        hour: 'numeric',
        minute: '2-digit'
      });
    }

    return date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric'
    });
  }

  onClose(): void {
    this.dialogRef.close();
  }

  startTrade(action: 'BUY' | 'SELL'): void {
    this.dialogRef.close({
      action,
      instrument: this.data
    } satisfies InstrumentTradeAction);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
