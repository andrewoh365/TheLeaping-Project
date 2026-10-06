import {
    ChangeDetectorRef,
    Component,
    ElementRef,
    OnDestroy,
    OnInit,
    ViewChild
} from '@angular/core';

import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import {
    Chart,
    ChartConfiguration,
    registerables
} from 'chart.js';

import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { AuthService } from '../../services/auth.service';

import {
    AnalyticsService,
    AssetType,
    DashboardFilters,
    DashboardResponse,
    OrderStatus
} from '../../services/analytics.service';

Chart.register(...registerables);

@Component({
    selector: 'app-analytics-dashboard',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        MatButtonModule,
        MatCardModule,
        MatFormFieldModule,
        MatInputModule,
        MatProgressSpinnerModule,
        MatSelectModule
    ],
    templateUrl: './analytics-dashboard.component.html',
    styleUrls: ['./analytics-dashboard.component.scss']
})
export class AnalyticsDashboardComponent
    implements OnInit, OnDestroy {

    @ViewChild('volumeChart')
    volumeChartRef?: ElementRef<HTMLCanvasElement>;

    @ViewChild('outcomesChart')
    outcomesChartRef?: ElementRef<HTMLCanvasElement>;

    @ViewChild('instrumentsChart')
    instrumentsChartRef?: ElementRef<HTMLCanvasElement>;

    @ViewChild('clientsChart')
    clientsChartRef?: ElementRef<HTMLCanvasElement>;

    @ViewChild('rejectionsChart')
    rejectionsChartRef?: ElementRef<HTMLCanvasElement>;

    dashboard: DashboardResponse | null = null;

    loading = false;
    errorMessage = '';

    dateFrom = '';
    dateTo = '';

    assetType: AssetType | '' = '';
    market = '';
    orderStatus: OrderStatus | '' = '';

    readonly markets = [
        'NASDAQ',
        'NYSE',
        'LSE',
        'NSE',
        'FOREX',
        'CRYPTO'
    ];

    private readonly destroy$ = new Subject<void>();

    private volumeChart?: Chart;
    private outcomesChart?: Chart;
    private instrumentsChart?: Chart;
    private clientsChart?: Chart;
    private rejectionsChart?: Chart;

    constructor(
        private analyticsService: AnalyticsService,
        private authService: AuthService,
        private router: Router,
        private changeDetectorRef: ChangeDetectorRef
    ) { }

    ngOnInit(): void {
        this.loadDashboard();
    }

    applyFilters(): void {
        this.loadDashboard();
    }

    resetFilters(): void {
        this.dateFrom = '';
        this.dateTo = '';
        this.assetType = '';
        this.market = '';
        this.orderStatus = '';

        this.loadDashboard();
    }

    logout(): void {
        this.authService.logout();
        this.router.navigate(['/login']);
    }

    private loadDashboard(): void {
        this.loading = true;
        this.errorMessage = '';

        const filters: DashboardFilters = {};

        if (this.dateFrom) {
            filters.dateFrom = this.dateFrom;
        }

        if (this.dateTo) {
            filters.dateTo = this.dateTo;
        }

        if (this.assetType) {
            filters.assetType = this.assetType;
        }

        if (this.market.trim()) {
            filters.market = this.market.trim();
        }

        if (this.orderStatus) {
            filters.orderStatus = this.orderStatus;
        }

        this.analyticsService
            .getDashboard(filters)
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: response => {
                    this.dashboard = response;
                    this.loading = false;

                    this.changeDetectorRef.detectChanges();
                    this.renderCharts();
                },

                error: error => {
                    console.error(
                        'Failed to load analytics dashboard:',
                        error
                    );

                    this.loading = false;

                    const backendMessage =
                        typeof error.error === 'string'
                            ? error.error
                            : error.error?.message;

                    this.errorMessage =
                        `Unable to load analytics data. ` +
                        `HTTP ${error.status || 0}` +
                        (backendMessage ? ` - ${backendMessage}` : '');
                }
            });
    }

    private renderCharts(): void {
        if (!this.dashboard) {
            return;
        }

        this.destroyCharts();

        this.renderVolumeChart();
        this.renderOutcomesChart();
        this.renderInstrumentsChart();
        this.renderClientsChart();
        this.renderRejectionsChart();
    }

    private renderVolumeChart(): void {
        if (!this.volumeChartRef || !this.dashboard) {
            return;
        }

        const configuration: ChartConfiguration = {
            type: 'line',
            data: {
                labels:
                    this.dashboard.trading_volume_over_time.points
                        .map(point => point.period),

                datasets: [
                    {
                        label: 'Trading Volume (USD)',
                        data:
                            this.dashboard.trading_volume_over_time.points
                                .map(point => point.volume_usd),
                        tension: 0.25
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false
            }
        };

        this.volumeChart = new Chart(
            this.volumeChartRef.nativeElement,
            configuration
        );
    }

    private renderOutcomesChart(): void {
        if (!this.outcomesChartRef || !this.dashboard) {
            return;
        }

        const configuration: ChartConfiguration = {
            type: 'doughnut',
            data: {
                labels:
                    this.dashboard.order_outcomes
                        .map(outcome => outcome.status),

                datasets: [
                    {
                        label: 'Orders',
                        data:
                            this.dashboard.order_outcomes
                                .map(outcome => outcome.count)
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false
            }
        };

        this.outcomesChart = new Chart(
            this.outcomesChartRef.nativeElement,
            configuration
        );
    }

    private renderInstrumentsChart(): void {
        if (!this.instrumentsChartRef || !this.dashboard) {
            return;
        }

        const configuration: ChartConfiguration = {
            type: 'bar',
            data: {
                labels:
                    this.dashboard.most_active_instruments
                        .map(instrument => instrument.symbol),

                datasets: [
                    {
                        label: 'Orders',
                        data:
                            this.dashboard.most_active_instruments
                                .map(instrument => instrument.order_count)
                    }
                ]
            },
            options: {
                indexAxis: 'y',
                responsive: true,
                maintainAspectRatio: false
            }
        };

        this.instrumentsChart = new Chart(
            this.instrumentsChartRef.nativeElement,
            configuration
        );
    }

    private renderClientsChart(): void {
        if (!this.clientsChartRef || !this.dashboard) {
            return;
        }

        const configuration: ChartConfiguration = {
            type: 'line',
            data: {
                labels:
                    this.dashboard.client_activity_trend.points
                        .map(point => point.period),

                datasets: [
                    {
                        label: 'Active Clients',
                        data:
                            this.dashboard.client_activity_trend.points
                                .map(point => point.active_clients),
                        tension: 0.25
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false
            }
        };

        this.clientsChart = new Chart(
            this.clientsChartRef.nativeElement,
            configuration
        );
    }

    private renderRejectionsChart(): void {
        if (!this.rejectionsChartRef || !this.dashboard) {
            return;
        }

        const configuration: ChartConfiguration = {
            type: 'bar',
            data: {
                labels:
                    this.dashboard.top_rejection_reasons
                        .map(reason => reason.reason),

                datasets: [
                    {
                        label: 'Rejected Orders',
                        data:
                            this.dashboard.top_rejection_reasons
                                .map(reason => reason.count)
                    }
                ]
            },
            options: {
                indexAxis: 'y',
                responsive: true,
                maintainAspectRatio: false
            }
        };

        this.rejectionsChart = new Chart(
            this.rejectionsChartRef.nativeElement,
            configuration
        );
    }

    private destroyCharts(): void {
        this.volumeChart?.destroy();
        this.outcomesChart?.destroy();
        this.instrumentsChart?.destroy();
        this.clientsChart?.destroy();
        this.rejectionsChart?.destroy();

        this.volumeChart = undefined;
        this.outcomesChart = undefined;
        this.instrumentsChart = undefined;
        this.clientsChart = undefined;
        this.rejectionsChart = undefined;
    }

    ngOnDestroy(): void {
        this.destroyCharts();

        this.destroy$.next();
        this.destroy$.complete();
    }
}