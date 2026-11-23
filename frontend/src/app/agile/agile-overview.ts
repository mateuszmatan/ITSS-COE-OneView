import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { OneViewContext, scopeNameIn } from '@common/context/oneview-context';
import { counted } from '@common/shared/formatting';
import { GRID, GridColumn } from '@common/ui/grid';
import { LoadError } from '@common/ui/load-error';
import { OvLoading } from '@common/ui/loading';
import { AgileApi } from '../core/api';
import { AgileApplication, AgileAssessment } from '../core/models';
import { ADMIN_AGILE, ADMIN_PRODUCTS, AGILE_MATURITY } from '../core/sections';
import { AreaPlan, PlannedCapability } from '../shared/area-plan';
import {
  agileParticipation,
  averageLevel,
  averageOf,
  dayText,
  frameworkLabel,
  latestAssessment,
  levelShare,
  levelText,
  notTakingPartText,
} from './agile-texts';

export const AGILE_CAPABILITIES: readonly PlannedCapability[] = [
  {
    title: 'Trend chart',
    description:
      'How the maturity of a team and of a business service changes from one assessment to the next, drawn as a chart.',
  },
  {
    title: 'What the next level asks for',
    description:
      'For every dimension, the practices a team needs to reach its next level, with the assessments that show them.',
  },
];

export interface DimensionLevel {
  code: string;
  label: string;
  description: string | null;
  level: number | null;
  previous: number | null;
}

@Component({
  selector: 'ov-agile-overview',
  imports: [RouterLink, GRID, OvLoading, LoadError, AreaPlan],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './agile-overview.html',
  styleUrl: './agile-overview.scss',
})
export class AgileOverviewPage {
  private readonly api = inject(AgileApi);
  private readonly context = inject(OneViewContext);

  protected readonly section = AGILE_MATURITY;
  protected readonly capabilities = AGILE_CAPABILITIES;
  protected readonly adminPath = ADMIN_AGILE.path;
  protected readonly newProduct = `${ADMIN_PRODUCTS.path}/new`;
  protected readonly participation = agileParticipation;
  protected readonly notTakingPart = notTakingPartText;
  protected readonly levelText = levelText;
  protected readonly dayText = dayText;

  protected readonly model = rxResource({ stream: () => this.api.model() });
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

  private readonly dimensions = computed(() =>
    this.model.hasValue() ? this.model.value().dimensions : [],
  );
  private readonly frameworks = computed(() =>
    this.model.hasValue() ? this.model.value().frameworks : [],
  );
  protected readonly maxLevel = computed(() =>
    this.model.hasValue() ? this.model.value().maxLevel : 5,
  );

  /** The teams that take part and were assessed: the summaries count only them. */
  private readonly assessed = computed(() =>
    this.scoped().filter(
      (application) => application.enabled && application.assessments.length > 0,
    ),
  );
  protected readonly anyAssessed = computed(() => this.assessed().length > 0);

  protected readonly tiles = computed(() => {
    const applications = this.scoped();
    const assessed = this.assessed();
    const average = averageLevel(
      assessed.map((application) => latestAssessment(application)?.overallLevel),
    );
    return [
      { label: 'Applications', value: String(applications.length), tone: 'info' },
      {
        label: 'Teams taking part',
        value: String(applications.filter((application) => application.enabled).length),
        tone: 'success',
      },
      { label: 'Assessed', value: String(assessed.length), tone: 'info' },
      {
        label: `Average level, of ${this.maxLevel()}`,
        value: levelText(average),
        tone: 'info',
      },
    ];
  });

  /** The mean level of every dimension over the latest assessments in the scope. */
  protected readonly dimensionAverages = computed<DimensionLevel[]>(() =>
    this.dimensions().map((dimension) => ({
      ...dimension,
      level: averageOf(this.assessed(), dimension.code),
      previous: null,
    })),
  );

  /** The mean overall level of every business service, over the latest assessments of its applications. */
  protected readonly departmentLevels = computed(() => {
    if (this.level() !== 'bbh') {
      return [];
    }
    const groups = new Map<string, AgileApplication[]>();
    for (const application of this.assessed()) {
      const name = application.departmentName ?? 'Not in a business service';
      groups.set(name, [...(groups.get(name) ?? []), application]);
    }
    return [...groups.entries()]
      .map(([name, applications]) => ({
        name,
        level: averageLevel(
          applications.map((application) => latestAssessment(application)?.overallLevel),
        ),
        note: counted(applications.length, 'assessed application'),
      }))
      .sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }));
  });

  protected readonly latest = computed(() => {
    const application = this.application();
    return application ? latestAssessment(application) : null;
  });

  /** The levels of the latest assessment of the application shown, with those of the one before it. */
  protected readonly latestLevels = computed<DimensionLevel[]>(() => {
    const latest = this.latest();
    const previous = this.application()?.assessments[1] ?? null;
    return this.dimensions().map((dimension) => ({
      ...dimension,
      level: latest?.levels[dimension.code] ?? null,
      previous: previous?.levels[dimension.code] ?? null,
    }));
  });

  protected readonly frameworkOf = (application: AgileApplication) =>
    frameworkLabel(this.frameworks(), application.framework);

  protected share(level: number | null): number {
    return levelShare(level, this.maxLevel());
  }

  protected change(entry: DimensionLevel): string | null {
    if (entry.level === null || entry.previous === null || entry.level === entry.previous) {
      return null;
    }
    const difference = entry.level - entry.previous;
    return difference > 0
      ? `up ${difference} from ${entry.previous}`
      : `down ${-difference} from ${entry.previous}`;
  }

  protected readonly rowId = (application: AgileApplication) => application.productId;
  protected readonly columns = computed<GridColumn<AgileApplication>[]>(() => [
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
            value: (application: AgileApplication) =>
              application.departmentName ?? 'Not in a business service',
            wrapHeader: true,
            minWidth: 110,
          },
        ]
      : []),
    { key: 'enabled', header: 'Agile', value: agileParticipation },
    {
      key: 'team',
      header: 'Team',
      value: (application) => application.teamName ?? '',
      minWidth: 120,
    },
    { key: 'framework', header: 'Way of working', value: this.frameworkOf, wrapHeader: true },
    {
      key: 'coach',
      header: 'Agile coach',
      value: (application) => application.coach ?? '',
      wrapHeader: true,
      minWidth: 110,
    },
    {
      key: 'assessedOn',
      header: 'Last assessed',
      value: (application) => dayText(latestAssessment(application)?.assessedOn),
      sortValue: (application) => latestAssessment(application)?.assessedOn ?? '',
      wrapHeader: true,
      minWidth: 110,
    },
    {
      key: 'overall',
      header: 'Level',
      value: (application) => levelText(latestAssessment(application)?.overallLevel),
      sortValue: (application) => latestAssessment(application)?.overallLevel ?? -1,
      numeric: true,
      width: 90,
    },
  ]);

  protected readonly assessmentId = (assessment: AgileAssessment) => assessment.id;
  protected readonly historyColumns = computed<GridColumn<AgileAssessment>[]>(() => [
    {
      key: 'assessedOn',
      header: 'Day',
      value: (assessment) => dayText(assessment.assessedOn),
      sortValue: (assessment) => assessment.assessedOn,
      width: 120,
    },
    {
      key: 'assessor',
      header: 'Assessed by',
      value: (assessment) => assessment.assessor,
      minWidth: 120,
    },
    ...this.dimensions().map((dimension) => ({
      key: `level-${dimension.code}`,
      header: dimension.label,
      value: (assessment: AgileAssessment) => assessment.levels[dimension.code] ?? '–',
      numeric: true,
      wrapHeader: true,
      minWidth: 110,
    })),
    {
      key: 'overall',
      header: 'Overall',
      value: (assessment) => levelText(assessment.overallLevel),
      numeric: true,
      width: 90,
    },
  ]);

  /** Shows one application alone, as if it had been chosen above. */
  protected open(application: AgileApplication): void {
    this.context.choose(application.departmentId, application.productId);
  }

  protected refresh(): void {
    this.model.reload();
    this.applications.reload();
  }
}
