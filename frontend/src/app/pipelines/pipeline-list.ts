import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { OneViewContext } from '@common/context/oneview-context';
import { PipelinesApi } from '../core/api';
import {
  PIPELINE_TYPES,
  Pipeline,
  PipelineHealth,
  PipelineType,
  RunResult,
  pipelineTypeLabel,
  pipelineTypeOrder,
} from '../core/models';
import { PIPELINES, SELF_SERVICE, pipelinePage } from '../core/sections';
import { MetricsBanner } from '../monitoring/metrics-banner';
import { text } from '@common/shared/form-controls';
import { Option, counted, formatRelative } from '@common/shared/formatting';
import { RUN_LOOK, StatusChip } from '../shared/status-chip';
import { GRID, GridColumn } from '@common/ui/grid';
import { LoadError } from '@common/ui/load-error';
import { OvLoading } from '@common/ui/loading';
import { PipelineActions } from './pipeline-actions';
import { KEY_MEANING } from './pipeline-texts';

export type KeyFilter = 'ALL' | 'ACTIVE' | 'INVALIDATED';

export interface PipelineFilters {
  service: string;
  product: string;
  type: PipelineType | 'ALL';
  job: string;
  key: KeyFilter;
  status: RunResult | 'ALL';
}

export const KEY_FILTERS: Option<KeyFilter>[] = [
  { value: 'ALL', label: 'All' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INVALIDATED', label: 'Invalidated' },
];

export const TYPE_FILTERS: Option<PipelineType | 'ALL'>[] = [
  { value: 'ALL', label: 'All' },
  ...PIPELINE_TYPES.map(({ value, label }) => ({ value, label })),
];

export const STATUS_FILTERS: Option<RunResult | 'ALL'>[] = [
  { value: 'ALL', label: 'All' },
  ...(Object.keys(RUN_LOOK) as RunResult[]).map((value) => ({
    value,
    label: RUN_LOOK[value].label,
  })),
];

const contains = (value: string | null, typed: string) =>
  (value ?? '').toLowerCase().includes(typed.trim().toLowerCase());

export function matches(row: PipelineHealth, filters: PipelineFilters): boolean {
  const { pipeline } = row;
  const active = pipeline.activeKey !== null;
  return (
    contains(pipeline.serviceName, filters.service) &&
    (contains(pipeline.productName, filters.product) ||
      contains(pipeline.productCode, filters.product)) &&
    (filters.type === 'ALL' || pipeline.type === filters.type) &&
    contains(pipeline.jenkinsJob, filters.job) &&
    (filters.key === 'ALL' || (filters.key === 'ACTIVE') === active) &&
    (filters.status === 'ALL' || row.status === filters.status)
  );
}

@Component({
  selector: 'ov-pipeline-list',
  imports: [RouterLink, GRID, OvLoading, LoadError, MetricsBanner, StatusChip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>{{ section.heading }}</h1>
          <p class="page-description">{{ section.description }}</p>
        </div>
        <div class="actions">
          <a class="btn btn-primary" [routerLink]="selfService.path"
            >Set up pipelines in Self-service</a
          >
        </div>
      </header>
      @if (pipelines.isLoading()) {
        <ov-loading />
      }
      @if (pipelines.error(); as error) {
        <ov-load-error what="The pipelines" [error]="error" (retry)="pipelines.reload()" />
      }
      @if (pipelines.hasValue()) {
        <ov-metrics-banner [metricsError]="pipelines.value().metricsError" />
        @if (all().length) {
          <section class="card list">
            <header class="card-header">
              <h2>{{ listTitle() }}</h2>
              <span class="muted shown">{{ shown() }}</span>
            </header>
            <p class="section-help">
              Pipeline key: {{ keyMeaning }}. Only its first and last characters are shown here;
              Invalidated means the pipeline is refused its settings until a new key is issued.
            </p>
            <ov-grid
              label="Pipelines"
              empty="No pipeline matches the filters."
              [rows]="rows()"
              [columns]="columns"
              [rowId]="rowId"
              [rowClass]="rowClass"
              (rowClick)="open($event.pipeline)"
            >
              <ng-template ovCell="service" let-row>
                <a class="mono quiet-link" [routerLink]="pipelineLink(row.pipeline.id)">{{
                  row.pipeline.serviceName
                }}</a>
              </ng-template>
              <ng-template ovCell="product" let-row>
                {{ row.pipeline.productName }}
                <span class="muted mono">{{ row.pipeline.productCode }}</span>
              </ng-template>
              <ng-template ovCell="key" let-row>
                @if (row.pipeline.activeKey; as key) {
                  <span class="mono">{{ key.hint }}</span>
                } @else {
                  <span class="chip danger">Invalidated</span>
                }
              </ng-template>
              <ng-template ovCell="status" let-row>
                <ov-status-chip [status]="row.status" />
              </ng-template>
              <ng-template ovCell="actions" let-row>
                <div class="row-actions">
                  <button
                    type="button"
                    class="btn btn-link"
                    [attr.aria-label]="'Configure service of ' + label(row.pipeline)"
                    (click)="editService(row.pipeline)"
                  >
                    Configure service
                  </button>
                  <button
                    type="button"
                    class="btn btn-link"
                    [attr.aria-label]="'Edit the settings of ' + label(row.pipeline)"
                    (click)="edit(row.pipeline)"
                  >
                    Edit settings
                  </button>
                </div>
              </ng-template>
            </ov-grid>
          </section>
        } @else {
          <div class="card empty-state">
            <h3>No pipelines in {{ scopeName() }} yet</h3>
            <p>
              Self-service sets up the pipelines of a product in a few guided steps and gives each
              service its pipeline key and its Jenkinsfile.
            </p>
            <a class="btn btn-primary" [routerLink]="selfService.path"
              >Set up pipelines in Self-service</a
            >
          </div>
        }
      }
    </div>
  `,
  styles: `
    .list {
      padding: 10px 14px 0;
    }

    .shown {
      font-size: 12px;
    }
  `,
})
export class PipelineList {
  private readonly api = inject(PipelinesApi);
  private readonly actions = inject(PipelineActions);
  private readonly router = inject(Router);
  private readonly context = inject(OneViewContext);

  protected readonly section = PIPELINES;
  protected readonly selfService = SELF_SERVICE;
  protected readonly pipelineLink = pipelinePage;
  protected readonly keyMeaning = KEY_MEANING;

  protected readonly scopeName = this.context.scopeName;
  protected readonly listTitle = computed(() =>
    this.context.level() === 'bbh'
      ? 'Pipelines of every business service'
      : `Pipelines of ${this.context.scopeName()}`,
  );

  protected readonly pipelines = rxResource({
    params: () => ({ departmentId: this.context.departmentId() }),
    stream: ({ params }) => this.api.listForDepartment(params.departmentId),
  });

  protected readonly filters = new FormGroup({
    service: text(''),
    product: text(''),
    type: new FormControl<PipelineType | 'ALL'>('ALL', { nonNullable: true }),
    job: text(''),
    key: new FormControl<KeyFilter>('ALL', { nonNullable: true }),
    status: new FormControl<RunResult | 'ALL'>('ALL', { nonNullable: true }),
  });
  private readonly filterValue = toSignal(
    this.filters.valueChanges.pipe(map(() => this.filters.getRawValue())),
    { initialValue: this.filters.getRawValue() },
  );

  protected readonly rowId = (row: PipelineHealth) => row.pipeline.id;
  protected readonly rowClass = (row: PipelineHealth) =>
    row.pipeline.activeKey ? 'clickable' : ['clickable', 'muted'];
  protected readonly columns: GridColumn<PipelineHealth>[] = [
    {
      key: 'service',
      header: 'Service',
      value: (row) => row.pipeline.serviceName,
      filter: { control: this.filters.controls.service, label: 'service' },
      minWidth: 140,
    },
    {
      key: 'product',
      header: 'Product',
      value: (row) => row.pipeline.productName,
      filter: { control: this.filters.controls.product, label: 'product' },
      minWidth: 190,
    },
    {
      key: 'type',
      header: 'Type',
      value: (row) => pipelineTypeLabel(row.pipeline.type),
      sortValue: (row) => pipelineTypeOrder(row.pipeline.type),
      filter: { control: this.filters.controls.type, label: 'type', options: TYPE_FILTERS },
      minWidth: 140,
    },
    {
      key: 'job',
      header: 'Jenkins job',
      value: (row) => row.pipeline.jenkinsJob ?? 'Not set',
      sortValue: (row) => row.pipeline.jenkinsJob ?? '',
      filter: { control: this.filters.controls.job, label: 'Jenkins job' },
      cellClass: 'mono',
      wrap: true,
      minWidth: 200,
    },
    {
      key: 'key',
      header: 'Pipeline key',
      value: (row) => row.pipeline.activeKey?.hint ?? 'Invalidated',
      sortValue: (row) => (row.pipeline.activeKey ? 0 : 1),
      filter: { control: this.filters.controls.key, label: 'pipeline key', options: KEY_FILTERS },
      minWidth: 130,
    },
    {
      key: 'status',
      header: 'Last run',
      value: (row) => RUN_LOOK[row.status].label,
      sortValue: (row) => Object.keys(RUN_LOOK).indexOf(row.status),
      filter: { control: this.filters.controls.status, label: 'last run', options: STATUS_FILTERS },
      minWidth: 170,
    },
    {
      key: 'lastRun',
      header: 'Finished',
      value: (row) => (row.lastRun ? formatRelative(row.lastRun.time) : ''),
      sortValue: (row) => Date.parse(row.lastRun?.time ?? '') || 0,
      width: 120,
    },
    { key: 'actions', header: '', width: 250 },
  ];

  protected readonly all = computed(() => {
    const productId = this.context.productId();
    const listed = this.pipelines.hasValue() ? this.pipelines.value().pipelines : [];
    return productId === null
      ? listed
      : listed.filter((row) => row.pipeline.productId === productId);
  });
  protected readonly rows = computed(() =>
    this.all().filter((row) => matches(row, this.filterValue())),
  );
  protected readonly shown = computed(() => {
    const all = counted(this.all().length, 'pipeline');
    return this.rows().length === this.all().length ? all : `${this.rows().length} of ${all} shown`;
  });

  constructor() {
    this.context.ensureLoaded();
  }

  protected label(pipeline: Pipeline): string {
    return `the ${pipelineTypeLabel(pipeline.type)} pipeline of ${pipeline.serviceName}`;
  }

  protected open(pipeline: Pipeline): void {
    this.router.navigate(pipelinePage(pipeline.id));
  }

  protected editService(pipeline: Pipeline): void {
    this.actions.editService(pipeline).subscribe((saved) => {
      if (saved) {
        this.pipelines.reload();
      }
    });
  }

  protected edit(pipeline: Pipeline): void {
    this.actions.edit(pipeline).subscribe((updated) =>
      this.pipelines.update(
        (listed) =>
          listed && {
            ...listed,
            pipelines: listed.pipelines.map((row) =>
              row.pipeline.id === updated.id
                ? { ...row, pipeline: { ...updated, activeKey: row.pipeline.activeKey } }
                : row,
            ),
          },
      ),
    );
  }
}
