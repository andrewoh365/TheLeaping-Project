import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, Inject, NgZone, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { CommonModule } from '@angular/common';
import { Chart, ChartOptions, registerables } from 'chart.js';
import { InstrumentService, PricePointDto } from '../../services/instrumentService';
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
  isTradable: boolean;
}

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

  @ViewChild('priceChart') priceChartRef?: ElementRef;
  chartConfig?: ChartConfig;
  isLoading = false;
  chart?: Chart;
  
  chartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: true,
    animation: false,
    normalized: true,
    layout: {
      padding: 10
    },
    plugins: {
      legend: {
        display: true,
        position: 'top' as const,
        title: {
          color: '#fafafa'
          
        },
        labels: {
          color: '#fafafa'
        }
      }
    },
    scales: {
      x: {
        grid: {
          display: true,
          color: '#fafafa'
        },
        ticks: {
          color: '#fafafa'
        }
      },
      y: {
        beginAtZero: false,
        grid: {
          color: '#fafafa'
      },
      ticks: {
        color: '#fafafa'
      }
    }
  }
}

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: InstrumentExpandedData,
    private instrumentService: InstrumentService,
    private dialogRef: MatDialogRef<InstrumentExpandedComponent>,
    private ngZone: NgZone,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadPriceHistory();
  }

  loadPriceHistory(): void {
    this.isLoading = true;
    this.cdr.markForCheck();
    const requestVersion = ++this.requestVersion;

    const to = new Date();
    const from = new Date();
    from.setDate(from.getDate() - 30);

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

        const sampledPrices = this.downsamplePrices(prices, InstrumentExpandedComponent.MAX_CHART_POINTS);
        this.chartConfig = this.transformToChartData(sampledPrices);
        this.isLoading = false;
        this.cdr.markForCheck();
        // Render after the template updates and the canvas is present.
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

  private transformToChartData(prices: PricePointDto[]): ChartConfig {
    return {
      labels: prices.map(p => new Date(p.timestamp).toLocaleDateString()),
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

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();

    if (this.chart) {
      this.chart.destroy();
      this.chart = undefined;
    }
  }

}