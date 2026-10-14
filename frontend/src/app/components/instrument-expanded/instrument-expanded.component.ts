import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, Inject, NgZone, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { CommonModule } from '@angular/common';
import { Chart, ChartOptions, registerables } from 'chart.js';
import { InstrumentDetail, InstrumentService, PricePointDto } from '../../services/instrumentService';
import { ChartConfig } from '../../models/chart.model';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

Chart.register(...registerables);

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
  isTradable: boolean;
}

export interface InstrumentTradeAction {
  action: 'BUY' | 'SELL';
  instrument: InstrumentExpandedData;
}

type ChartRange = '1D' | '1W' | '1M';

@Component({
  selector: 'app-instrument-expanded',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  templateUrl: './instrument-expanded.component.html',
  styleUrl: './instrument-expanded.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class InstrumentExpandedComponent implements OnInit, OnDestroy {
  private static readonly MAX_CHART_POINTS = 240;
  private readonly destroy$ = new Subject<void>();
  private requestVersion = 0;
  readonly chartRanges: ChartRange[] = ['1D', '1W', '1M'];
  activeChartRange: ChartRange = '1M';

  @ViewChild('priceChart') priceChartRef?: ElementRef;
  chartConfig?: ChartConfig;
  selectedPrice: number | null = null;
  isLoading = false;
  chart?: Chart;
  private chartPrices: PricePointDto[] = [];
  
  chartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    normalized: true,
    interaction: {
      mode: 'nearest',
      intersect: false
    },
    layout: {
      padding: 10
    },
    plugins: {
      legend: {
        display: true,
        position: 'top' as const,
        labels: {
          color: '#fafafa',
          usePointStyle: true,
          boxWidth: 8,
          boxHeight: 8
        }
      }
    },
    onClick: (_event, elements) => {
      const element = elements[0];
      if (!element || !this.chartPrices[element.index]) {
        return;
      }

      this.ngZone.run(() => {
        this.selectedPrice = this.chartPrices[element.index].price;
        this.cdr.markForCheck();
      });
    },
    scales: {
      x: {
        grid: {
          display: true,
          color: 'rgba(232, 245, 239, 0.18)'
        },
        ticks: {
    
          color: '#fafafa',
          maxRotation: 0,
          autoSkip: true,
          maxTicksLimit: 8
        }
      },
      y: {
        beginAtZero: false,
        grid: {
          color: '#2c2c2c'
        },
        ticks: {
          color: '#fafafa'
        }
      }
    }
  };

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: InstrumentExpandedData,
    private instrumentService: InstrumentService,
    private dialogRef: MatDialogRef<InstrumentExpandedComponent>,
    private ngZone: NgZone,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadInstrumentDetail();
    this.loadPriceHistory();
  }

  setChartRange(range: ChartRange): void {
    if (this.activeChartRange === range) {
      return;
    }

    this.activeChartRange = range;
    this.selectedPrice = null;
    this.loadPriceHistory();
  }

  private loadInstrumentDetail(): void {
    this.instrumentService.getInstrumentById(String(this.data.instrumentId))
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (detail: InstrumentDetail) => {
          Object.assign(this.data, detail);
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

        const dailyPrices = this.keepLatestPricePerDay(prices);
        const sampledPrices = this.downsamplePrices(dailyPrices, InstrumentExpandedComponent.MAX_CHART_POINTS);
        this.chartPrices = sampledPrices;
        const latestPrice = prices[prices.length - 1];
        if (latestPrice) {
          this.data.price = latestPrice.price;
          this.data.lastUpdated = latestPrice.timestamp;
        }
        this.chartConfig = this.transformToChartData(sampledPrices);
        this.isLoading = false;
        this.cdr.markForCheck();
        requestAnimationFrame(() => {
          this.ngZone.runOutsideAngular(() => this.renderChart());
        });
      },
      error: (err) => {
        if (requestVersion !== this.requestVersion) {
          return;
        }

        console.error('Failed to load price history', err);
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

  private renderChart(): void {
    if (!this.priceChartRef || !this.chartConfig) {
      console.warn('Missing priceChartRef or chartConfig');
      return;
    }

    if (this.chart) {
      this.chart.destroy();
    }

    const canvas = this.priceChartRef.nativeElement;
    this.chart = new Chart(canvas, {
      type: 'line',
      data: {
        labels: this.chartConfig.labels,
        datasets: this.chartConfig.datasets.map(ds => ({
          label: ds.label,
          data: ds.data,
          borderColor: ds.borderColor,
          backgroundColor: ds.backgroundColor,
          tension: ds.tension,
          pointRadius: 0,
          pointHitRadius: 14,
          borderWidth: 2,
          fill: true
        }))
      },
      options: this.chartOptions
    });
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

  private transformToChartData(prices: PricePointDto[]): ChartConfig {
    return {
      labels: prices.map(p => new Date(p.timestamp).toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric'
      })),
      datasets: [{
        label: `${this.data.symbol} Price (${this.data.priceCurrencyCode})`,
        data: prices.map(p => p.price),
        borderColor: '#1a1a1a',
        backgroundColor: '#26d07c',
        tension: 0.3,
        fill: true
      }]
    };
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

    if (this.chart) {
      this.chart.destroy();
      this.chart = undefined;
    }
  }

}