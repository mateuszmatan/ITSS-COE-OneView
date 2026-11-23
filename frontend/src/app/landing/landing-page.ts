import { ChangeDetectionStrategy, Component, ResourceRef, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { OneViewContext, scopeNameIn } from '@common/context/oneview-context';
import {
  AGILE_MAX_LEVEL,
  agileParticipation,
  averageLevel,
  dayText,
  latestAssessment,
  levelText,
} from '../agile/agile-texts';
import { AgileApi, MonitoringApi, QualityApi } from '../core/api';
import { AgileApplication, ProductHealth, QualityApplication, RunResult } from '../core/models';
import { AGILE, AREAS, DEVSECOPS, LANDING, QA } from '../core/sections';
import { TARGET_FIELDS, participation, targetText } from '../qa/quality-texts';

/** One figure on the card of an area: a value and what it counts. */
export interface AreaFigure {
  value: string;
  label: string;
}

interface AreaFigures {
  state: 'loading' | 'error' | 'shown';
  figures: AreaFigure[];
}

function figuresOf<T>(
  resource: ResourceRef<T | undefined>,
  figures: (value: T) => AreaFigure[],
): AreaFigures {
  if (resource.error()) {
    return { state: 'error', figures: [] };
  }
  return resource.hasValue()
    ? { state: 'shown', figures: figures(resource.value() as T) }
    : { state: 'loading', figures: [] };
}

/** The pipelines of the products shown, and how their latest runs ended. */
export function devSecOpsFigures(products: readonly ProductHealth[]): AreaFigure[] {
  const totals: Partial<Record<RunResult, number>> = {};
  for (const product of products) {
    for (const [status, count] of Object.entries(product.statusCounts) as [RunResult, number][]) {
      totals[status] = (totals[status] ?? 0) + count;
    }
  }
  const pipelines = Object.values(totals).reduce((sum, count) => sum + (count ?? 0), 0);
  return [
    { value: String(pipelines), label: pipelines === 1 ? 'pipeline' : 'pipelines' },
    { value: String(totals.SUCCESS ?? 0), label: 'passed' },
    {
      value: String((totals.FAILURE ?? 0) + (totals.UNSTABLE ?? 0)),
      label: 'failed or with warnings',
    },
  ];
}

/** How the applications shown take part in the QM Metric Tool; one application shows its targets. */
export function qualityFigures(
  applications: readonly QualityApplication[],
  one: boolean,
): AreaFigure[] {
  if (one && applications.length === 1) {
    const [application] = applications;
    const [coverage, passRate] = TARGET_FIELDS;
    return [
      { value: participation(application), label: 'in the QM Metric Tool' },
      {
        value: targetText(coverage, application.effectiveTargets.coveragePercent),
        label: 'code coverage',
      },
      {
        value: targetText(passRate, application.effectiveTargets.passRatePercent),
        label: 'test pass rate',
      },
    ];
  }
  const taking = applications.filter((application) => application.enabled);
  return [
    {
      value: `${taking.length} of ${applications.length}`,
      label: applications.length === 1 ? 'application takes part' : 'applications take part',
    },
    {
      value: String(taking.filter((application) => application.dashboardUrl).length),
      label: 'with a Grafana dashboard',
    },
  ];
}

/** How the teams of the applications shown take part and how mature they are. */
export function agileFigures(
  applications: readonly AgileApplication[],
  one: boolean,
): AreaFigure[] {
  if (one && applications.length === 1) {
    const [application] = applications;
    const latest = latestAssessment(application);
    return [
      { value: agileParticipation(application), label: 'in Agile Assessment & Adoption' },
      latest
        ? {
            value: `Level ${levelText(latest.overallLevel)}`,
            label: `of ${AGILE_MAX_LEVEL}, assessed ${dayText(latest.assessedOn)}`,
          }
        : { value: 'Not assessed', label: 'yet' },
    ];
  }
  const assessed = applications.filter((application) => application.assessments.length);
  return [
    {
      value: `${applications.filter((application) => application.enabled).length} of ${applications.length}`,
      label: applications.length === 1 ? 'team takes part' : 'teams take part',
    },
    { value: String(assessed.length), label: 'assessed' },
    {
      value: levelText(
        averageLevel(assessed.map((application) => latestAssessment(application)?.overallLevel)),
      ),
      label: `average level, of ${AGILE_MAX_LEVEL}`,
    },
  ];
}

@Component({
  selector: 'ov-landing-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>{{ landing.heading }}</h1>
          <p class="page-description">{{ landing.description }}</p>
        </div>
      </header>

      <p class="scope-note">
        @if (level() === 'bbh') {
          The figures on the cards are for the whole of BBH. Choose a business service or an
          application above to see only theirs.
        } @else {
          The figures on the cards are for {{ scopeName() }}. Choose All business services above to
          see the whole of BBH.
        }
      </p>

      <ul class="areas">
        @for (area of areas; track area.key) {
          <li>
            <a class="card area-card" [routerLink]="area.path">
              <h2>{{ area.label }}</h2>
              <p class="area-description">{{ area.description }}</p>
              @if (area.sections.length > 1) {
                <ul class="area-sections" [attr.aria-label]="'Sections of ' + area.label">
                  @for (section of area.sections; track section.path) {
                    <li>{{ section.label }}</li>
                  }
                </ul>
              }
              @if (figures()[area.key]; as summary) {
                @switch (summary.state) {
                  @case ('shown') {
                    <ul class="area-figures" [attr.aria-label]="area.label + ' in ' + scopeName()">
                      @for (figure of summary.figures; track figure.label) {
                        <li>
                          <strong>{{ figure.value }}</strong
                          >&ngsp;<span>{{ figure.label }}</span>
                        </li>
                      }
                    </ul>
                  }
                  @case ('error') {
                    <p class="area-figures-note">The figures could not be read just now.</p>
                  }
                  @default {
                    <p class="area-figures-note">Reading the figures…</p>
                  }
                }
              }
              <span class="area-open">Open {{ area.label }}</span>
            </a>
          </li>
        }
      </ul>
    </div>
  `,
  styles: `
    .areas {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 16px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .areas > li {
      display: flex;
    }

    .area-card {
      display: flex;
      flex: 1;
      flex-direction: column;
      gap: 10px;
      min-height: 220px;
      padding: 18px 20px;
      border-top: 4px solid var(--ov-navy);
      border-radius: 4px;
      color: var(--ov-text);
      text-decoration: none;
      transition:
        box-shadow 0.15s ease,
        border-color 0.15s ease;

      &:hover,
      &:focus-visible {
        border-color: var(--ov-navy);
        box-shadow: 0 4px 14px rgba(12, 35, 64, 0.16);
      }

      &:focus-visible {
        outline: 2px solid var(--ov-navy);
        outline-offset: 2px;
      }

      h2 {
        margin: 0;
        color: var(--ov-navy);
        font-family: var(--ov-serif);
        font-size: 21px;
        font-weight: 400;
      }
    }

    .area-description {
      margin: 0;
      color: var(--ov-muted);
      line-height: 1.45;
    }

    .area-sections {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      margin: 0;
      padding: 0;
      list-style: none;

      li {
        padding: 2px 9px;
        border-radius: 10px;
        background: var(--ov-neutral-bg);
        color: var(--ov-neutral);
        font-size: 11.5px;
        font-weight: 600;
        white-space: nowrap;
      }
    }

    .scope-note {
      margin: -4px 0 12px;
      color: var(--ov-muted);
      font-size: 12.5px;
    }

    .area-figures {
      display: flex;
      flex-wrap: wrap;
      gap: 6px 18px;
      margin: 0;
      padding: 8px 0 0;
      border-top: 1px solid var(--ov-border);
      list-style: none;

      li {
        display: flex;
        align-items: baseline;
        gap: 5px;
        font-size: 12.5px;
      }

      strong {
        color: var(--ov-navy);
        font-family: var(--ov-serif);
        font-size: 18px;
        font-weight: 500;
      }

      span {
        color: var(--ov-muted);
      }
    }

    .area-figures-note {
      margin: 0;
      padding-top: 8px;
      border-top: 1px solid var(--ov-border);
      color: var(--ov-muted);
      font-size: 12px;
    }

    .area-open {
      margin-top: auto;
      padding-top: 6px;
      color: var(--ov-navy);
      font-size: 13px;
      font-weight: 600;

      &::after {
        content: ' \\2192';
      }
    }
  `,
})
export class LandingPage {
  private readonly context = inject(OneViewContext);
  private readonly monitoringApi = inject(MonitoringApi);
  private readonly qualityApi = inject(QualityApi);
  private readonly agileApi = inject(AgileApi);

  protected readonly landing = LANDING;
  protected readonly areas = AREAS;
  protected readonly level = this.context.level;

  private readonly monitoring = rxResource({ stream: () => this.monitoringApi.overview() });
  private readonly quality = rxResource({ stream: () => this.qualityApi.applications() });
  private readonly agile = rxResource({ stream: () => this.agileApi.applications() });

  protected readonly scopeName = computed(() =>
    scopeNameIn(
      this.context.scope(),
      [
        ...(this.quality.hasValue() ? this.quality.value() : []),
        ...(this.agile.hasValue() ? this.agile.value() : []),
      ],
      this.context.scopeName(),
    ),
  );

  /** The figures of every area for what is chosen above: BBH, a business service or an application. */
  protected readonly figures = computed<Record<string, AreaFigures | undefined>>(() => {
    const one = this.level() === 'application';
    const shown = <T extends { departmentId: number | null; productId: number }>(rows: T[]) =>
      rows.filter((row) => this.context.includes(row.departmentId, row.productId));
    return {
      [DEVSECOPS.key]: figuresOf(this.monitoring, (overview) =>
        devSecOpsFigures(shown(overview.products)),
      ),
      [QA.key]: figuresOf(this.quality, (applications) => qualityFigures(shown(applications), one)),
      [AGILE.key]: figuresOf(this.agile, (applications) => agileFigures(shown(applications), one)),
    };
  });
}
