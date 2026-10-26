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
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';

import {
    Chart,
    ChartConfiguration,
    registerables
} from 'chart.js';

import { EMPTY, Subject } from 'rxjs';
import {
    catchError,
    switchMap,
    takeUntil
} from 'rxjs/operators';

import { AuthService } from '../../services/auth.service';
import {
    Instrument,
    InstrumentService
} from '../../services/instrument.service';

import {
    AnalyticsService,
    AssetType,
    ClientSegment,
    DashboardFilters,
    DashboardResponse,
    OrderStatus
} from '../../services/analytics.service';


import { InternalNavComponent } from '../internal-nav/internal-nav.component';

Chart.register(...registerables);

@Component({
    selector: 'app-analytics-dashboard',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        MatButtonModule,
        MatAutocompleteModule,
        MatCardModule,
        MatFormFieldModule,
        MatIconModule,
        MatInputModule,
        MatProgressSpinnerModule,
        MatSelectModule,
        MatTooltipModule,
        InternalNavComponent
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

    @ViewChild('segmentsChart')
    segmentsChartRef?: ElementRef<HTMLCanvasElement>;

    @ViewChild('rejectionsChart')
    rejectionsChartRef?: ElementRef<HTMLCanvasElement>;

    dashboard: DashboardResponse | null = null;
    // Filters that produced the current dashboard, as opposed to the
    // form values the user may have changed without applying.
    private dashboardFilters: DashboardFilters = {};

    loading = false;
    errorMessage = '';


    // ADMIN reuses analytics with additional operations navigation.
    isAdmin = false;
    dateFrom = '';
    dateTo = '';

    assetType: AssetType | '' = '';
    market = '';
    instrumentSearch = '';
    selectedInstrumentId: number | null = null;
    selectedInstrument: Instrument | null = null;
    orderStatus: OrderStatus | '' = '';
    clientSegment: ClientSegment | '' = '';

    allInstruments: Instrument[] = [];
    filteredInstruments: Instrument[] = [];

    readonly markets = [
        'NASDAQ',
        'NYSE',
        'LSE',
        'NSE',
        'FOREX',
        'CRYPTO'
    ];

    private readonly destroy$ = new Subject<void>();
    private readonly dashboardRequests$ =
        new Subject<DashboardFilters | null>();

    private volumeChart?: Chart;
    private outcomesChart?: Chart;
    private instrumentsChart?: Chart;
    private clientsChart?: Chart;
    private segmentsChart?: Chart;
    private rejectionsChart?: Chart;

    constructor(
        private analyticsService: AnalyticsService,
        private instrumentService: InstrumentService,
        private authService: AuthService,
        private router: Router,
        private changeDetectorRef: ChangeDetectorRef
    ) { }

    ngOnInit(): void {
        this.isAdmin = this.authService.hasRole('ADMIN');
        this.setupDashboardRequests();
        this.loadInstruments();
        this.loadDashboard();
    }

    applyFilters(): void {
        this.loadDashboard();
    }

    retryDashboard(): void {
        this.loadDashboard();
    }

    get dateRangeInvalid(): boolean {
        return !!this.dateFrom
            && !!this.dateTo
            && this.dateFrom > this.dateTo;
    }

    // Typed search text only filters once an option is picked; applying
    // with unmatched text would silently show all instruments.
    get instrumentSelectionPending(): boolean {
        return !!this.instrumentSearch.trim()
            && this.selectedInstrumentId === null;
    }

    resetFilters(): void {
        this.dateFrom = '';
        this.dateTo = '';
        this.assetType = '';
        this.market = '';
        this.instrumentSearch = '';
        this.selectedInstrumentId = null;
        this.selectedInstrument = null;
        this.filteredInstruments = this.allInstruments;
        this.orderStatus = '';
        this.clientSegment = '';

        this.loadDashboard();
    }

    onInstrumentSearchChange(value: string): void {
        this.instrumentSearch = value || '';

        const selectedLabel = this.selectedInstrument
            ? this.instrumentLabel(this.selectedInstrument)
            : '';

        if (
            this.selectedInstrumentId !== null
            && this.instrumentSearch !== selectedLabel
        ) {
            this.selectedInstrumentId = null;
            this.selectedInstrument = null;
        }

        this.filteredInstruments = this.filterInstruments(
            this.instrumentSearch
        );
    }

    onInstrumentSelected(instrumentId: string | number): void {
        if (instrumentId === '') {
            this.clearInstrumentSelection();
            return;
        }

        const instrument = this.allInstruments.find(
            candidate => String(candidate.instrumentId) === String(instrumentId)
        );

        if (!instrument) {
            return;
        }

        const normalizedId = Number(instrument.instrumentId);
        if (!Number.isInteger(normalizedId) || normalizedId <= 0) {
            return;
        }

        this.selectedInstrumentId = normalizedId;
        this.selectedInstrument = instrument;
        this.instrumentSearch = this.instrumentLabel(instrument);
        this.filteredInstruments = [instrument];
    }

    clearInstrumentSelection(): void {
        this.instrumentSearch = '';
        this.selectedInstrumentId = null;
        this.selectedInstrument = null;
        this.filteredInstruments = this.allInstruments;
    }

    logout(): void {
        this.authService.logout();
        this.router.navigate(['/login']);
    }

    private loadDashboard(): void {
        const filters: DashboardFilters = {};

        if (this.dateRangeInvalid) {
            this.dashboardRequests$.next(null);
            this.clearDashboardState();
            this.errorMessage =
                'The From date cannot be after the To date.';
            return;
        }

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

        if (this.selectedInstrumentId !== null) {
            filters.instrumentId = this.selectedInstrumentId;
        }

        if (this.orderStatus) {
            filters.orderStatus = this.orderStatus;
        }

        if (this.clientSegment) {
            filters.clientSegment = this.clientSegment;
        }

        this.dashboardRequests$.next(filters);
    }

    private setupDashboardRequests(): void {
        this.dashboardRequests$
            .pipe(
                switchMap(filters => {
                    if (filters === null) {
                        return EMPTY;
                    }

                    this.loading = true;
                    this.errorMessage = '';
                    this.dashboard = null;
                    this.dashboardFilters = filters;
                    this.destroyCharts();

                    return this.analyticsService
                        .getDashboard(filters)
                        .pipe(
                            catchError(error => {
                                this.handleDashboardError(error);
                                return EMPTY;
                            })
                        );
                }),
                takeUntil(this.destroy$)
            )
            .subscribe(response => {
                this.dashboard = response;
                this.loading = false;

                this.changeDetectorRef.detectChanges();
                this.renderCharts();
            });
    }

    private handleDashboardError(error: any): void {
        console.error(
            'Failed to load analytics dashboard:',
            error
        );

        this.loading = false;

        const backendMessage =
            typeof error.error === 'string'
                ? error.error
                : error.error?.message;

        if (error.status === 503) {
            this.errorMessage =
                'Analytics temporarily unavailable. Please retry when '
                + 'reporting recovers.';
            return;
        }

        this.errorMessage =
            `Unable to load analytics data. ` +
            `HTTP ${error.status || 0}` +
            (backendMessage ? ` - ${backendMessage}` : '');
    }

    private clearDashboardState(): void {
        this.loading = false;
        this.dashboard = null;
        this.destroyCharts();
    }

    private loadInstruments(): void {
        this.instrumentService
            .getAllInstruments()
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: (instruments: Instrument[]) => {
                    this.allInstruments = instruments;
                    this.filteredInstruments = instruments;
                },
                error: (error: unknown) => {
                    console.error(
                        'Failed to load instruments for analytics:',
                        error
                    );
                    this.allInstruments = [];
                    this.filteredInstruments = [];
                }
            });
    }

    private filterInstruments(query: string): Instrument[] {
        const normalizedQuery = query.trim().toLowerCase();

        if (!normalizedQuery) {
            return this.allInstruments;
        }

        return this.allInstruments.filter(instrument =>
            instrument.symbol.toLowerCase().includes(normalizedQuery)
            || instrument.name.toLowerCase().includes(normalizedQuery)
        );
    }

    private instrumentLabel(instrument: Instrument): string {
        return `${instrument.symbol} - ${instrument.name} `
            + `(ID ${instrument.instrumentId})`;
    }

    formatReportingPeriod(): string {
        const period = this.dashboard?.reporting_period;
        if (!period) {
            return '';
        }

        const dateFrom = this.formatCalendarDate(period.date_from);
        const dateTo = this.formatCalendarDate(period.date_to);

        return `${dateFrom} - ${dateTo} (${period.timezone})`;
    }

    rejectionEmptyMessage(): string {
        const orderStatus = this.dashboardFilters.orderStatus;

        if (orderStatus && orderStatus !== 'REJECTED') {
            return 'Rejection reasons are not applicable to the selected '
                + 'order status.';
        }

        return 'No matching activity for these filters.';
    }

    private formatCalendarDate(value: string): string {
        const [year, month, day] = value
            .split('-')
            .map(Number);

        return new Intl.DateTimeFormat('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
            timeZone: 'UTC'
        }).format(new Date(Date.UTC(year, month - 1, day)));
    }

    hasTradingVolumeActivity(): boolean {
        return this.dashboard?.trading_volume_over_time.points.some(
            point => point.volume_usd > 0
        ) ?? false;
    }

    hasOrderOutcomeActivity(): boolean {
        return this.dashboard?.order_outcomes.some(
            outcome => outcome.count > 0
        ) ?? false;
    }

    hasInstrumentActivity(): boolean {
        return this.dashboard?.most_active_instruments.some(
            instrument => instrument.order_count > 0
        ) ?? false;
    }

    hasClientActivity(): boolean {
        return this.dashboard?.client_activity_trend.points.some(
            point => point.active_clients > 0
        ) ?? false;
    }

    hasRejectionActivity(): boolean {
        return this.dashboard?.top_rejection_reasons.some(
            reason => reason.count > 0
        ) ?? false;
    }

    hasSegmentActivity(): boolean {
        return this.dashboard?.client_segment_breakdown.some(
            segment =>
                segment.active_clients > 0
                || segment.order_count > 0
                || segment.trading_volume_usd > 0
        ) ?? false;
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
        this.renderSegmentsChart();
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

    private renderSegmentsChart(): void {
        if (!this.segmentsChartRef || !this.dashboard) {
            return;
        }

        const configuration: ChartConfiguration = {
            type: 'bar',
            data: {
                labels:
                    this.dashboard.client_segment_breakdown
                        .map(segment => segment.segment),

                datasets: [
                    {
                        label: 'Orders',
                        data:
                            this.dashboard.client_segment_breakdown
                                .map(segment => segment.order_count)
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false
            }
        };

        this.segmentsChart = new Chart(
            this.segmentsChartRef.nativeElement,
            configuration
        );
    }

    private destroyCharts(): void {
        this.volumeChart?.destroy();
        this.outcomesChart?.destroy();
        this.instrumentsChart?.destroy();
        this.clientsChart?.destroy();
        this.segmentsChart?.destroy();
        this.rejectionsChart?.destroy();

        this.volumeChart = undefined;
        this.outcomesChart = undefined;
        this.instrumentsChart = undefined;
        this.clientsChart = undefined;
        this.segmentsChart = undefined;
        this.rejectionsChart = undefined;
    }

    ngOnDestroy(): void {
        this.destroyCharts();

        this.dashboardRequests$.complete();
        this.destroy$.next();
        this.destroy$.complete();
    }
}