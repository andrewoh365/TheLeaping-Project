import { Component, Inject, OnInit, ViewChild, ElementRef } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';
import { Chart, Colors, registerables, Ticks } from 'chart.js';
import { InstrumentService, PricePointDto } from '../../services/instrumentService';
import { ChartConfig } from '../../models/chart.model';

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
  imports: [CommonModule, MatDialogModule],
  templateUrl: './instrument-expanded.component.html',
  styleUrl: './instrument-expanded.component.scss'
})
export class InstrumentExpandedComponent implements OnInit {
  @ViewChild('priceChart') priceChartRef?: ElementRef;
  chartConfig?: ChartConfig;
  isLoading = false;
  chart?: Chart;
  
  chartOptions = {
    responsive: true,
    maintainAspectRatio: true,
    layout: {
      padding: 10
    },
    plugins: {
      legend: {
        display: true,
        color: '#fafafa',
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
    private instrumentService: InstrumentService
  ) {}

  ngOnInit(): void {
    this.loadPriceHistory();
  }

  loadPriceHistory(): void {
    this.isLoading = true;
    const to = new Date();
    const from = new Date();
    from.setDate(from.getDate() - 30);

    this.instrumentService.getPriceHistory(
      this.data.symbol,
      from.toISOString(),
      to.toISOString()
    ).subscribe({
      next: (prices: PricePointDto[]) => {
        this.chartConfig = this.transformToChartData(prices);

        setTimeout(() => this.renderChart(), 100);
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Failed to load price history', err);
        this.isLoading = false;
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
          fill: true
        }))
      },
      options: this.chartOptions
    });
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
}