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

import { MatIconModule } from '@angular/material/icon';

import {
    Chart,
    ChartConfiguration,
    ChartDataset,
    ChartOptions,
    Plugin,
    Scale,
    ScriptableContext,
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
    ClientSegmentBreakdown,
    DashboardFilters,
    DashboardResponse,
    InstrumentActivity,
    OrderOutcome,
    OrderStatus,
    TimeGrouping
} from '../../services/analytics.service';

import { InternalLayoutComponent } from '../internal-layout/internal-layout.component';

Chart.register(...registerables);

// Chart theme: matches the operations design system on the #2a2a2a card.
const CHART_FONT_FAMILY = '\'Inter\', sans-serif';
const CHART_TICK_COLOR = '#9e9e9e';
const CHART_LABEL_COLOR = '#bdbdbd';
const CHART_GRID_COLOR = 'rgba(255, 255, 255, 0.06)';
const CHART_CROSSHAIR_COLOR = 'rgba(255, 255, 255, 0.18)';
const CHART_SURFACE = '#2a2a2a';
const CHART_GREEN = '#00ab68';
const CHART_GREEN_HOVER = '#26d07c';
const CHART_RED = '#e66767';
const CHART_RED_HOVER = '#ee8a8a';

const CHART_TICK_FONT = { family: CHART_FONT_FAMILY, size: 12 };
const CHART_LABEL_FONT = { family: CHART_FONT_FAMILY, size: 12, weight: 500 };

const CHART_TOOLTIP_STYLE = {
    backgroundColor: '#1a1a1a',
    borderColor: '#3a3a3a',
    borderWidth: 1,
    padding: 10,
    cornerRadius: 8,
    caretPadding: 8,
    titleColor: '#fafafa',
    bodyColor: '#bdbdbd',
    titleFont: { family: CHART_FONT_FAMILY, size: 13, weight: 600 },
    bodyFont: { family: CHART_FONT_FAMILY, size: 12 },
    titleMarginBottom: 4,
    displayColors: false
};

// Fixed order and colors for order outcomes, validated for adjacent
// colorblind separation against the card surface.
const ORDER_STATUS_ORDER: OrderStatus[] = [
    'FILLED',
    'SUBMITTED',
    'ACCEPTED',
    'REJECTED',
    'CANCELLED',
    'EXPIRED'
];

const ORDER_STATUS_COLORS: Record<OrderStatus, string> = {
    FILLED: '#00ab68',
    SUBMITTED: '#c98500',
    ACCEPTED: '#3987e5',
    REJECTED: '#e66767',
    CANCELLED: '#9085e9',
    EXPIRED: '#d55181'
};

const CLIENT_SEGMENT_ORDER: ClientSegment[] = [
    'INACTIVE',
    'OCCASIONAL',
    'REGULAR',
    'FREQUENT'
];

const USD_FORMAT = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
});

const USD_COMPACT_FORMAT = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 1
});

const USD_KPI_FORMAT = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 2
});

const COUNT_FORMAT = new Intl.NumberFormat('en-US');

const COUNT_COMPACT_FORMAT = new Intl.NumberFormat('en-US', {
    notation: 'compact',
    maximumFractionDigits: 1
});

const COUNT_KPI_FORMAT = new Intl.NumberFormat('en-US', {
    notation: 'compact',
    maximumFractionDigits: 2
});

const PERCENT_FORMAT = new Intl.NumberFormat('en-US', {
    maximumFractionDigits: 1
});

const SHORT_DAY_FORMAT = new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC'
});

const KPI_COMPACT_THRESHOLD = 1000000;

interface OutcomeRow extends OrderOutcome {
    color: string;
}

function toTitleCase(value: string): string {
    return value
        .toLowerCase()
        .replace(/(^|[\s_-])\w/g, match => match.toUpperCase())
        .replace(/_/g, ' ');
}

function pluralize(count: number, singular: string, plural: string): string {
    return `${COUNT_FORMAT.format(count)} ${count === 1 ? singular : plural}`;
}

// Greedy word wrap for canvas labels; overflow beyond maxLines is
// truncated with an ellipsis so long rejection reasons stay tidy.
function wrapLabel(text: string, maxChars: number, maxLines: number): string[] {
    const lines: string[] = [];
    let current = '';

    for (const word of text.trim().split(/\s+/)) {
        if (current && `${current} ${word}`.length > maxChars) {
            lines.push(current);
            current = word;
        } else {
            current = current ? `${current} ${word}` : word;
        }
    }

    if (current) {
        lines.push(current);
    }

    const truncate = (line: string): string =>
        `${line.slice(0, Math.max(1, maxChars - 1)).trimEnd()}…`;

    const fitted = lines.map(line =>
        line.length > maxChars ? truncate(line) : line
    );

    if (fitted.length <= maxLines) {
        return fitted;
    }

    const kept = fitted.slice(0, maxLines);
    const last = kept[maxLines - 1];
    kept[maxLines - 1] = last.endsWith('…') || last.length >= maxChars
        ? truncate(last)
        : `${last}…`;

    return kept;
}

function areaGradient(chart: Chart): CanvasGradient | string {
    const { ctx, chartArea } = chart;

    if (!chartArea) {
        return 'rgba(0, 171, 104, 0.1)';
    }

    const gradient = ctx.createLinearGradient(
        0,
        chartArea.top,
        0,
        chartArea.bottom
    );
    gradient.addColorStop(0, 'rgba(0, 171, 104, 0.18)');
    gradient.addColorStop(1, 'rgba(0, 171, 104, 0)');

    return gradient;
}

function formatBarValue(value: number): string {
    return value >= 10000
        ? COUNT_COMPACT_FORMAT.format(value)
        : COUNT_FORMAT.format(value);
}

// Vertical hairline that snaps to the hovered period on line charts.
const CROSSHAIR_PLUGIN: Plugin<'line'> = {
    id: 'opsCrosshair',
    beforeDatasetsDraw(chart) {
        const active = chart.tooltip?.getActiveElements() ?? [];
        if (active.length === 0) {
            return;
        }

        const { ctx, chartArea } = chart;
        const x = active[0].element.x;

        ctx.save();
        ctx.beginPath();
        ctx.moveTo(x, chartArea.top);
        ctx.lineTo(x, chartArea.bottom);
        ctx.lineWidth = 1;
        ctx.strokeStyle = CHART_CROSSHAIR_COLOR;
        ctx.stroke();
        ctx.restore();
    }
};

// Value at the bar tip (horizontal) or on the column cap (vertical).
const BAR_VALUE_PLUGIN: Plugin<'bar'> = {
    id: 'opsBarValues',
    afterDatasetsDraw(chart) {
        const dataset = chart.data.datasets[0];
        if (!dataset) {
            return;
        }

        const horizontal = chart.options.indexAxis === 'y';
        const { ctx } = chart;

        ctx.save();
        ctx.font = `600 12px ${CHART_FONT_FAMILY}`;
        ctx.fillStyle = CHART_LABEL_COLOR;
        ctx.textAlign = horizontal ? 'left' : 'center';
        ctx.textBaseline = horizontal ? 'middle' : 'bottom';

        chart.getDatasetMeta(0).data.forEach((element, index) => {
            const value = Number(dataset.data[index]);
            if (!Number.isFinite(value)) {
                return;
            }

            const label = formatBarValue(value);
            if (horizontal) {
                ctx.fillText(label, element.x + 8, element.y);
            } else {
                ctx.fillText(label, element.x, element.y - 6);
            }
        });

        ctx.restore();
    }
};

@Component({
    selector: 'app-analytics-dashboard',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        MatIconModule,
        InternalLayoutComponent
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

    @ViewChild('instrumentOptionsList')
    instrumentOptionsListRef?: ElementRef<HTMLElement>;

    dashboard: DashboardResponse | null = null;
    // Filters that produced the current dashboard, as opposed to the
    // form values the user may have changed without applying.
    private dashboardFilters: DashboardFilters = {};

    // Outcomes and segments in their fixed display order, prepared once
    // per response so the chart, legend and table agree.
    outcomeRows: OutcomeRow[] = [];
    outcomesTotal = 0;
    segmentRows: ClientSegmentBreakdown[] = [];

    loading = false;
    errorMessage = '';

    // ADMIN reuses analytics as the operations overview.
    isAdmin = false;
    showReportingRules = false;

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

    // Instrument combobox state.
    instrumentOptionsOpen = false;
    activeInstrumentIndex = -1;
    readonly instrumentOptionLimit = 50;

    readonly markets = [
        'NASDAQ',
        'NYSE',
        'LSE',
        'NSE',
        'FOREX',
        'CRYPTO'
    ];

    readonly assetTypeOptions: { value: AssetType | ''; label: string }[] = [
        { value: '', label: 'All' },
        { value: 'STOCK', label: 'Stock' },
        { value: 'CRYPTO', label: 'Crypto' },
        { value: 'FOREX', label: 'Forex' }
    ];

    readonly orderStatusOptions: { value: OrderStatus; label: string }[] = [
        { value: 'SUBMITTED', label: 'Submitted' },
        { value: 'ACCEPTED', label: 'Accepted' },
        { value: 'FILLED', label: 'Filled' },
        { value: 'REJECTED', label: 'Rejected' },
        { value: 'CANCELLED', label: 'Cancelled' },
        { value: 'EXPIRED', label: 'Expired' }
    ];

    readonly clientSegmentOptions: {
        value: ClientSegment;
        label: string;
        range: string;
    }[] = [
        { value: 'INACTIVE', label: 'Inactive', range: '0 orders' },
        { value: 'OCCASIONAL', label: 'Occasional', range: '1–9 orders' },
        { value: 'REGULAR', label: 'Regular', range: '10–19 orders' },
        { value: 'FREQUENT', label: 'Frequent', range: '20+ orders' }
    ];

    readonly skeletonTiles = [0, 1, 2, 3];
    readonly skeletonCards = [
        'chart-card--wide',
        '',
        'chart-card--wide',
        '',
        'chart-card--wide chart-card--stack',
        'chart-card--stack'
    ];

    private readonly destroy$ = new Subject<void>();
    private readonly dashboardRequests$ =
        new Subject<DashboardFilters | null>();

    private volumeChart?: Chart<'line'>;
    private outcomesChart?: Chart<'doughnut'>;
    private instrumentsChart?: Chart<'bar'>;
    private clientsChart?: Chart<'line'>;
    private segmentsChart?: Chart<'bar'>;
    private rejectionsChart?: Chart<'bar'>;

    constructor(
        private analyticsService: AnalyticsService,
        private instrumentService: InstrumentService,
        private authService: AuthService,
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

    toggleReportingRules(): void {
        this.showReportingRules = !this.showReportingRules;
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

    get visibleInstruments(): Instrument[] {
        return this.filteredInstruments.slice(0, this.instrumentOptionLimit);
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
        this.closeInstrumentOptions();

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

    // Typing filters the list and highlights the first match so Enter
    // picks it.
    onInstrumentInput(value: string): void {
        this.onInstrumentSearchChange(value);
        this.instrumentOptionsOpen = true;
        this.activeInstrumentIndex =
            this.instrumentSearch.trim() && this.filteredInstruments.length > 0
                ? 1
                : -1;
    }

    openInstrumentOptions(): void {
        if (this.instrumentOptionsOpen) {
            return;
        }

        this.instrumentOptionsOpen = true;
        this.activeInstrumentIndex = this.selectedInstrumentOptionIndex();
    }

    closeInstrumentOptions(): void {
        this.instrumentOptionsOpen = false;
        this.activeInstrumentIndex = -1;
    }

    // Option 0 is "All instruments"; option n is visibleInstruments[n - 1].
    selectInstrumentOption(instrument: Instrument | null): void {
        this.onInstrumentSelected(instrument ? instrument.instrumentId : '');
        this.closeInstrumentOptions();
    }

    onInstrumentKeydown(event: KeyboardEvent): void {
        const optionCount = this.visibleInstruments.length + 1;

        switch (event.key) {
            case 'ArrowDown':
            case 'ArrowUp': {
                event.preventDefault();

                if (!this.instrumentOptionsOpen) {
                    this.openInstrumentOptions();
                    return;
                }

                const step = event.key === 'ArrowDown' ? 1 : -1;
                const start = this.activeInstrumentIndex < 0 && step < 0
                    ? optionCount
                    : this.activeInstrumentIndex;

                this.setActiveInstrumentIndex(
                    Math.min(Math.max(start + step, 0), optionCount - 1)
                );
                return;
            }

            case 'Enter': {
                if (!this.instrumentOptionsOpen || this.activeInstrumentIndex < 0) {
                    return;
                }

                event.preventDefault();

                if (this.activeInstrumentIndex === 0) {
                    this.selectInstrumentOption(null);
                    return;
                }

                const instrument =
                    this.visibleInstruments[this.activeInstrumentIndex - 1];
                if (instrument) {
                    this.selectInstrumentOption(instrument);
                }
                return;
            }

            case 'Escape':
                if (this.instrumentOptionsOpen) {
                    event.preventDefault();
                    event.stopPropagation();
                    this.closeInstrumentOptions();
                }
                return;

            default:
                return;
        }
    }

    isInstrumentSelected(instrument: Instrument): boolean {
        return this.selectedInstrumentId !== null
            && Number(instrument.instrumentId) === this.selectedInstrumentId;
    }

    trackInstrument(_index: number, instrument: Instrument): string {
        return String(instrument.instrumentId);
    }

    private selectedInstrumentOptionIndex(): number {
        if (this.selectedInstrumentId === null) {
            return -1;
        }

        const index = this.visibleInstruments.findIndex(
            instrument => this.isInstrumentSelected(instrument)
        );

        return index === -1 ? -1 : index + 1;
    }

    private setActiveInstrumentIndex(index: number): void {
        this.activeInstrumentIndex = index;

        this.instrumentOptionsListRef?.nativeElement
            .querySelector<HTMLElement>(`#analytics-instrument-option-${index}`)
            ?.scrollIntoView({ block: 'nearest' });
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
                this.prepareDashboardRows(response);
                this.loading = false;

                this.changeDetectorRef.detectChanges();
                this.renderCharts();
            });
    }

    private prepareDashboardRows(response: DashboardResponse): void {
        const statusRank = (status: OrderStatus): number => {
            const index = ORDER_STATUS_ORDER.indexOf(status);
            return index === -1 ? ORDER_STATUS_ORDER.length : index;
        };

        const segmentRank = (segment: ClientSegment): number => {
            const index = CLIENT_SEGMENT_ORDER.indexOf(segment);
            return index === -1 ? CLIENT_SEGMENT_ORDER.length : index;
        };

        this.outcomeRows = [...response.order_outcomes]
            .sort((a, b) => statusRank(a.status) - statusRank(b.status))
            .map(outcome => ({
                ...outcome,
                color: ORDER_STATUS_COLORS[outcome.status] ?? CHART_TICK_COLOR
            }));

        this.outcomesTotal = this.outcomeRows.reduce(
            (total, outcome) => total + outcome.count,
            0
        );

        this.segmentRows = [...response.client_segment_breakdown]
            .sort((a, b) => segmentRank(a.segment) - segmentRank(b.segment));
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

    segmentRange(segment: ClientSegment): string {
        return this.clientSegmentOptions.find(
            option => option.value === segment
        )?.range ?? '';
    }

    // Stat tiles: compact from a million up; the full value goes in the
    // tile's title attribute.
    formatUsd(value: number): string {
        return USD_FORMAT.format(value);
    }

    formatKpiUsd(value: number): string {
        return Math.abs(value) >= KPI_COMPACT_THRESHOLD
            ? USD_KPI_FORMAT.format(value)
            : USD_FORMAT.format(value);
    }

    formatCount(value: number): string {
        return COUNT_FORMAT.format(value);
    }

    formatKpiCount(value: number): string {
        return Math.abs(value) >= KPI_COMPACT_THRESHOLD
            ? COUNT_KPI_FORMAT.format(value)
            : COUNT_FORMAT.format(value);
    }

    meterWidth(percentage: number): number {
        return Math.min(100, Math.max(0, percentage || 0));
    }

    // A one-bar chart reads better as a figure, so a selected instrument
    // shows its order count directly.
    singleInstrumentActivity(): InstrumentActivity | null {
        if (
            !this.dashboard?.selected_instrument
            || this.dashboard.most_active_instruments.length !== 1
        ) {
            return null;
        }

        return this.dashboard.most_active_instruments[0];
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

    // Periods arrive as UTC YYYY-MM-DD bucket starts.
    private formatPeriod(
        period: string,
        grouping: TimeGrouping,
        detailed: boolean
    ): string {
        const [year, month, day] = period
            .split('-')
            .map(Number);

        if (!year || !month || !day) {
            return period;
        }

        const date = new Date(Date.UTC(year, month - 1, day));

        if (grouping === 'MONTHLY') {
            return new Intl.DateTimeFormat('en-US', {
                month: detailed ? 'long' : 'short',
                year: 'numeric',
                timeZone: 'UTC'
            }).format(date);
        }

        if (!detailed) {
            return SHORT_DAY_FORMAT.format(date);
        }

        const label = this.formatCalendarDate(period);
        return grouping === 'WEEKLY' ? `Week of ${label}` : label;
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

    private chartAnimation(): false | { duration: number } {
        const reducedMotion = typeof window !== 'undefined'
            && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

        return reducedMotion ? false : { duration: 500 };
    }

    private lineDataset(
        label: string,
        data: number[]
    ): ChartDataset<'line', number[]> {
        // A lone point has no segment to draw, so it gets a marker.
        const pointRadius = data.length === 1 ? 4 : 0;

        return {
            label,
            data,
            borderColor: CHART_GREEN,
            borderWidth: 2,
            borderCapStyle: 'round',
            borderJoinStyle: 'round',
            tension: 0.3,
            fill: 'origin',
            backgroundColor: (context: ScriptableContext<'line'>) =>
                areaGradient(context.chart),
            pointRadius,
            pointHoverRadius: 4,
            pointHitRadius: 12,
            pointBackgroundColor: CHART_GREEN,
            pointBorderColor: CHART_SURFACE,
            pointBorderWidth: 2,
            pointHoverBackgroundColor: CHART_GREEN,
            pointHoverBorderColor: CHART_SURFACE,
            pointHoverBorderWidth: 2,
            clip: 8
        };
    }

    private lineOptions(
        periods: string[],
        grouping: TimeGrouping,
        formatTick: (value: number) => string,
        formatValue: (value: number) => string,
        integerTicks: boolean
    ): ChartOptions<'line'> {
        return {
            responsive: true,
            maintainAspectRatio: false,
            animation: this.chartAnimation(),
            interaction: { mode: 'index', intersect: false },
            layout: { padding: { top: 8, left: 4 } },
            plugins: {
                legend: { display: false },
                tooltip: {
                    ...CHART_TOOLTIP_STYLE,
                    callbacks: {
                        title: items => items.length
                            ? formatValue(Number(items[0].raw))
                            : '',
                        label: item => this.formatPeriod(
                            periods[item.dataIndex] ?? '',
                            grouping,
                            true
                        )
                    }
                }
            },
            scales: {
                x: {
                    grid: { display: false },
                    border: { display: false },
                    ticks: {
                        color: CHART_TICK_COLOR,
                        font: CHART_TICK_FONT,
                        padding: 8,
                        maxRotation: 0,
                        autoSkip: true,
                        autoSkipPadding: 20,
                        callback: (_value, index) => this.formatPeriod(
                            periods[index] ?? '',
                            grouping,
                            false
                        )
                    }
                },
                y: {
                    position: 'right',
                    beginAtZero: true,
                    grid: {
                        color: CHART_GRID_COLOR,
                        lineWidth: 1,
                        drawTicks: false
                    },
                    border: { display: false },
                    ticks: {
                        color: CHART_TICK_COLOR,
                        font: CHART_TICK_FONT,
                        padding: 10,
                        maxTicksLimit: 5,
                        precision: integerTicks ? 0 : undefined,
                        callback: value => formatTick(Number(value))
                    }
                }
            }
        };
    }

    private barDataset(
        label: string,
        data: number[],
        color: string,
        hoverColor: string
    ): ChartDataset<'bar', number[]> {
        return {
            label,
            data,
            backgroundColor: color,
            hoverBackgroundColor: hoverColor,
            borderRadius: 4,
            borderSkipped: 'start',
            maxBarThickness: 24,
            categoryPercentage: 0.8,
            barPercentage: 0.9
        };
    }

    private horizontalBarOptions(
        formatTitle: (value: number) => string,
        formatBody: (index: number) => string | string[],
        formatTick: (index: number, chartWidth: number) => string | string[]
    ): ChartOptions<'bar'> {
        return {
            indexAxis: 'y',
            responsive: true,
            maintainAspectRatio: false,
            animation: this.chartAnimation(),
            interaction: { mode: 'index', axis: 'y', intersect: false },
            layout: { padding: { right: 52 } },
            plugins: {
                legend: { display: false },
                tooltip: {
                    ...CHART_TOOLTIP_STYLE,
                    callbacks: {
                        title: items => items.length
                            ? formatTitle(Number(items[0].raw))
                            : '',
                        label: item => formatBody(item.dataIndex)
                    }
                }
            },
            scales: {
                x: {
                    display: false,
                    beginAtZero: true
                },
                y: {
                    grid: { display: false },
                    border: { display: false },
                    ticks: {
                        color: CHART_LABEL_COLOR,
                        font: CHART_LABEL_FONT,
                        padding: 8,
                        autoSkip: false,
                        callback(this: Scale, _value: number | string, index: number) {
                            return formatTick(index, this.chart.width);
                        }
                    }
                }
            }
        };
    }

    private renderVolumeChart(): void {
        if (!this.volumeChartRef || !this.dashboard) {
            return;
        }

        const trend = this.dashboard.trading_volume_over_time;
        const periods = trend.points.map(point => point.period);

        const configuration: ChartConfiguration<'line'> = {
            type: 'line',
            data: {
                labels: periods,
                datasets: [
                    this.lineDataset(
                        'Trading volume (USD)',
                        trend.points.map(point => point.volume_usd)
                    )
                ]
            },
            options: this.lineOptions(
                periods,
                trend.grouping,
                value => USD_COMPACT_FORMAT.format(value),
                value => `${USD_FORMAT.format(value)} traded`,
                false
            ),
            plugins: [CROSSHAIR_PLUGIN]
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

        const outcomes = this.outcomeRows;
        const labels = outcomes.map(outcome => toTitleCase(outcome.status));
        const colors = outcomes.map(outcome => outcome.color);
        const segmentsWithOrders = outcomes.filter(
            outcome => outcome.count > 0
        ).length;

        const configuration: ChartConfiguration<'doughnut'> = {
            type: 'doughnut',
            data: {
                labels,
                datasets: [
                    {
                        label: 'Orders',
                        data: outcomes.map(outcome => outcome.count),
                        backgroundColor: colors,
                        hoverBackgroundColor: colors,
                        borderColor: CHART_SURFACE,
                        hoverBorderColor: CHART_SURFACE,
                        // A full ring needs no gap, and a border would
                        // draw a seam at the top.
                        borderWidth: segmentsWithOrders > 1 ? 2 : 0,
                        hoverOffset: 4
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: this.chartAnimation(),
                cutout: '70%',
                layout: { padding: 4 },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        ...CHART_TOOLTIP_STYLE,
                        callbacks: {
                            title: items => {
                                const outcome = items.length
                                    ? outcomes[items[0].dataIndex]
                                    : undefined;

                                return outcome
                                    ? `${pluralize(outcome.count, 'order', 'orders')}`
                                        + ` · ${PERCENT_FORMAT.format(outcome.percentage)}%`
                                    : '';
                            },
                            label: item => labels[item.dataIndex] ?? ''
                        }
                    }
                }
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

        const symbols = this.dashboard.most_active_instruments
            .map(instrument => instrument.symbol);

        const configuration: ChartConfiguration<'bar'> = {
            type: 'bar',
            data: {
                labels: symbols,
                datasets: [
                    this.barDataset(
                        'Orders',
                        this.dashboard.most_active_instruments
                            .map(instrument => instrument.order_count),
                        CHART_GREEN,
                        CHART_GREEN_HOVER
                    )
                ]
            },
            options: this.horizontalBarOptions(
                value => pluralize(value, 'order', 'orders'),
                index => symbols[index] ?? '',
                index => wrapLabel(symbols[index] ?? '', 14, 1)
            ),
            plugins: [BAR_VALUE_PLUGIN]
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

        const trend = this.dashboard.client_activity_trend;
        const periods = trend.points.map(point => point.period);

        const configuration: ChartConfiguration<'line'> = {
            type: 'line',
            data: {
                labels: periods,
                datasets: [
                    this.lineDataset(
                        'Active clients',
                        trend.points.map(point => point.active_clients)
                    )
                ]
            },
            options: this.lineOptions(
                periods,
                trend.grouping,
                value => COUNT_COMPACT_FORMAT.format(value),
                value => pluralize(value, 'active client', 'active clients'),
                true
            ),
            plugins: [CROSSHAIR_PLUGIN]
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

        const reasons = this.dashboard.top_rejection_reasons
            .map(reason => reason.reason);

        const configuration: ChartConfiguration<'bar'> = {
            type: 'bar',
            data: {
                labels: reasons,
                datasets: [
                    this.barDataset(
                        'Rejected orders',
                        this.dashboard.top_rejection_reasons
                            .map(reason => reason.count),
                        CHART_RED,
                        CHART_RED_HOVER
                    )
                ]
            },
            options: this.horizontalBarOptions(
                value => pluralize(value, 'rejected order', 'rejected orders'),
                index => wrapLabel(reasons[index] ?? '', 44, 6),
                // Labels get roughly 40% of the chart width, two lines max.
                (index, chartWidth) => wrapLabel(
                    reasons[index] ?? '',
                    Math.max(14, Math.floor((chartWidth * 0.4) / 6.6)),
                    2
                )
            ),
            plugins: [BAR_VALUE_PLUGIN]
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

        const segments = this.segmentRows;
        const labels = segments.map(segment => toTitleCase(segment.segment));

        const configuration: ChartConfiguration<'bar'> = {
            type: 'bar',
            data: {
                labels,
                datasets: [
                    this.barDataset(
                        'Orders',
                        segments.map(segment => segment.order_count),
                        CHART_GREEN,
                        CHART_GREEN_HOVER
                    )
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: this.chartAnimation(),
                interaction: { mode: 'index', axis: 'x', intersect: false },
                layout: { padding: { top: 24 } },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        ...CHART_TOOLTIP_STYLE,
                        callbacks: {
                            title: items => items.length
                                ? pluralize(Number(items[0].raw), 'order', 'orders')
                                : '',
                            label: item => {
                                const segment = segments[item.dataIndex];
                                return segment
                                    ? `${labels[item.dataIndex]} clients `
                                        + `(${this.segmentRange(segment.segment)})`
                                    : '';
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { display: false },
                        border: { display: false },
                        ticks: {
                            color: CHART_LABEL_COLOR,
                            font: CHART_LABEL_FONT,
                            padding: 8
                        }
                    },
                    y: {
                        display: false,
                        beginAtZero: true
                    }
                }
            },
            plugins: [BAR_VALUE_PLUGIN]
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
