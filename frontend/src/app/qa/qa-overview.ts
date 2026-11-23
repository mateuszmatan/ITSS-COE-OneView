import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { OneViewContext, scopeNameIn } from '@common/context/oneview-context';
import { RelativeTimePipe } from '@common/shared/formatting';
import { GRID, GridColumn } from '@common/ui/grid';
import { LoadError } from '@common/ui/load-error';
import { OvLoading } from '@common/ui/loading';
import { QualityApi } from '../core/api';
import { QualityApplication } from '../core/models';
import { ADMIN_PRODUCTS, ADMIN_QA, QA_METRICS } from '../core/sections';
import { AreaPlan, PlannedCapability } from '../shared/area-plan';
import {
  TARGET_FIELDS,
  ownTargetsSummary,
  participation,
  targetText,
  targetsSummary,
} from './quality-texts';

export const QA_CAPABILITIES: readonly PlannedCapability[] = [
  {
    title: 'Test results',
    description:
      'Passed, failed and skipped unit, regression, smoke and performance tests of every application, from the runs of its pipelines.',
  },
  {
    title: 'Code quality',
    description:
      'Coverage, the SonarQube quality gate and its open issues per service, measured against the targets of the application.',
  },
  {
    title: 'Defects',
    description:
      'Open and escaped defects per application and business service, by severity and age.',
  },
  {
    title: 'Quality trends',
    description:
      'How the test results, the coverage and the defects of every application change over time, from the quality metrics in InfluxDB.',
  },
];

@Component({
  selector: 'ov-qa-overview',
  imports: [RouterLink, GRID, OvLoading, LoadError, AreaPlan, RelativeTimePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './qa-overview.html',
  styleUrl: './qa-overview.scss',
})
export class QaOverviewPage {
  private readonly api = inject(QualityApi);
  private readonly context = inject(OneViewContext);

  protected readonly section = QA_METRICS;
  protected readonly capabilities = QA_CAPABILITIES;
  protected readonly adminPath = ADMIN_QA.path;
  protected readonly newProduct = `${ADMIN_PRODUCTS.path}/new`;
  protected readonly participation = participation;

  protected readonly defaults = rxResource({ stream: () => this.api.defaults() });
  protected readonly applications = rxResource({ stream: () => this.api.applications() });

  protected readonly level = this.context.level;
  private readonly all = computed(() =>
    this.applications.hasValue() ? this.applications.value() : [],
  );
  /** The applications of the chosen business service or the chosen application, or every one. */
  protected readonly scoped = computed(() =>
    this.all().filter((application) =>
      this.context.includes(application.departmentId, application.productId),
    ),
  );
  protected readonly scopeName = computed(() =>
    scopeNameIn(this.context.scope(), this.all(), this.context.scopeName()),
  );
  protected readonly application = computed(() =>
    this.level() === 'application' ? (this.scoped()[0] ?? null) : null,
  );

  protected readonly tiles = computed(() => {
    const applications = this.scoped();
    const taking = applications.filter((application) => application.enabled);
    return [
      { label: 'Applications', value: applications.length, tone: 'info' },
      { label: 'Take part', value: taking.length, tone: 'success' },
      {
        label: 'Do not take part yet',
        value: applications.length - taking.length,
        tone: 'neutral',
      },
      {
        label: 'With a Grafana dashboard',
        value: taking.filter((application) => application.dashboardUrl).length,
        tone: 'info',
      },
    ];
  });

  protected readonly bbhTargets = computed(() =>
    this.defaults.hasValue() ? targetsSummary(this.defaults.value().targets) : '',
  );

  protected readonly targetTiles = computed(() => {
    const application = this.application();
    return application
      ? TARGET_FIELDS.map((field) => ({
          label: field.label,
          value: targetText(field, application.effectiveTargets[field.key]),
          own: (application.targets[field.key] ?? null) !== null,
        }))
      : [];
  });

  protected readonly rowId = (application: QualityApplication) => application.productId;
  protected readonly columns = computed<GridColumn<QualityApplication>[]>(() => [
    {
      key: 'name',
      header: 'Application',
      value: (application) => application.name,
      minWidth: 180,
    },
    ...(this.level() === 'bbh'
      ? [
          {
            key: 'department',
            header: 'Business service',
            value: (application: QualityApplication) =>
              application.departmentName ?? 'Not in a business service',
            minWidth: 150,
          },
        ]
      : []),
    {
      key: 'enabled',
      header: 'QM Metric Tool',
      value: participation,
      width: 150,
    },
    {
      key: 'qaLead',
      header: 'QA lead',
      value: (application) => application.qaLead ?? '',
      minWidth: 120,
    },
    {
      key: 'targets',
      header: 'Targets',
      value: (application) => ownTargetsSummary(application.targets),
      wrap: true,
      minWidth: 220,
    },
    { key: 'dashboard', header: 'Grafana', width: 110 },
  ]);

  /** Shows one application alone, as if it had been chosen above. */
  protected open(application: QualityApplication): void {
    this.context.choose(application.departmentId, application.productId);
  }

  protected refresh(): void {
    this.defaults.reload();
    this.applications.reload();
  }
}
