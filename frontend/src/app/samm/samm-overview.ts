import { DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { SammApi } from '../core/api';
import {
  SammApplicationScore,
  SammDepartmentScore,
  SammDomainScore,
  SammOrganizationOverview,
  SammPracticeScore,
} from '../core/models';
import { SAMM_MATURITY } from '../core/sections';
import { OneViewContext } from '@common/context/oneview-context';
import { FORM_FIELD } from '@common/ui/form-field';
import { LoadError } from '@common/ui/load-error';
import { OvLoading } from '@common/ui/loading';

export type SammTab = 'overview' | 'applications';

@Component({
  selector: 'ov-samm-overview',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DecimalPipe,
    DatePipe,
    FORM_FIELD,
    OvLoading,
    LoadError,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './samm-overview.html',
  styleUrl: './samm-overview.scss',
})
export class SammOverviewPage {
  private readonly api = inject(SammApi);
  private readonly route = inject(ActivatedRoute);
  private readonly context = inject(OneViewContext);

  protected readonly section = SAMM_MATURITY;
  protected readonly overview = rxResource({
    params: () => this.context.scope(),
    stream: ({ params }) => this.api.overview(params),
  });

  protected readonly level = this.context.level;
  /** The application, business service or BBH whose maturity is shown, by name. */
  protected readonly scopeName = computed(() => {
    const { departmentId, productId } = this.context.scope();
    const data = this.overview.hasValue() ? this.overview.value() : null;
    if (productId !== null) {
      return (
        data?.applications.find((app) => app.productId === productId)?.productName ??
        this.context.scopeName()
      );
    }
    if (departmentId !== null) {
      return (
        data?.departments.find((dept) => dept.departmentId === departmentId)?.departmentName ??
        this.context.scopeName()
      );
    }
    return 'BBH';
  });
  protected readonly maturityLabel = computed(() =>
    this.level() === 'bbh' ? 'BBH Organization Maturity' : `${this.scopeName()} Maturity`,
  );

  protected readonly activeTab = signal<SammTab>('overview');
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly levelFilter = new FormControl('ALL', { nonNullable: true });

  private readonly searchQuery = toSignal(this.search.valueChanges, { initialValue: '' });
  private readonly selectedLvl = toSignal(this.levelFilter.valueChanges, {
    initialValue: 'ALL',
  });

  protected readonly selectedApp = signal<SammApplicationScore | null>(null);

  protected readonly sortColumn = signal<string>('name');
  protected readonly sortAsc = signal<boolean>(true);

  constructor() {
    effect(() => {
      const data = this.overview.hasValue() ? this.overview.value() : null;
      if (!data) return;
      const targetPipelineId = this.route.snapshot.queryParamMap.get('pipelineId');
      if (targetPipelineId) {
        const id = Number(targetPipelineId);
        const match = data.applications.find((a) => a.pipelineId === id);
        if (match) {
          this.selectedApp.set(match);
        }
      }
    });
  }

  protected readonly filteredApplications = computed(() => {
    const data = this.overview.hasValue() ? this.overview.value() : null;
    if (!data) return [];
    let list = data.applications;

    const query = this.searchQuery().trim().toLowerCase();
    if (query) {
      list = list.filter(
        (app) =>
          app.productName.toLowerCase().includes(query) ||
          app.serviceName.toLowerCase().includes(query) ||
          (app.pipelineKey && app.pipelineKey.toLowerCase().includes(query)) ||
          app.departmentName.toLowerCase().includes(query),
      );
    }

    const lvl = this.selectedLvl();
    if (lvl !== 'ALL') {
      const targetLvl = Number(lvl);
      list = list.filter((app) => app.maturityLevel === targetLvl);
    }

    const col = this.sortColumn();
    const asc = this.sortAsc();
    return [...list].sort((a, b) => {
      let cmp = 0;
      switch (col) {
        case 'name':
          cmp =
            a.productName.localeCompare(b.productName) ||
            a.serviceName.localeCompare(b.serviceName);
          break;
        case 'department':
          cmp = a.departmentName.localeCompare(b.departmentName);
          break;
        case 'overall':
          cmp = a.overallScore - b.overallScore;
          break;
        case 'level':
          cmp = a.maturityLevel - b.maturityLevel;
          break;
        case 'gov':
          cmp = this.domainScore(a, 'GOVERNANCE') - this.domainScore(b, 'GOVERNANCE');
          break;
        case 'design':
          cmp = this.domainScore(a, 'DESIGN') - this.domainScore(b, 'DESIGN');
          break;
        case 'impl':
          cmp = this.domainScore(a, 'IMPLEMENTATION') - this.domainScore(b, 'IMPLEMENTATION');
          break;
        case 'verif':
          cmp = this.domainScore(a, 'VERIFICATION') - this.domainScore(b, 'VERIFICATION');
          break;
        case 'ops':
          cmp = this.domainScore(a, 'OPERATIONS') - this.domainScore(b, 'OPERATIONS');
          break;
      }
      return asc ? cmp : -cmp;
    });
  });

  protected setSort(column: string): void {
    if (this.sortColumn() === column) {
      this.sortAsc.update((v) => !v);
    } else {
      this.sortColumn.set(column);
      this.sortAsc.set(true);
    }
  }

  protected domainScore(app: SammApplicationScore, fn: string): number {
    const found = app.domainScores.find((d) => d.function === fn);
    return found ? found.score : 0;
  }

  protected setTab(tab: SammTab): void {
    this.activeTab.set(tab);
  }

  /** Shows the applications of one business service, which every page then follows. */
  protected showDepartment(departmentId: number): void {
    this.context.choose(departmentId);
    this.setTab('applications');
  }

  protected selectApplication(app: SammApplicationScore): void {
    this.selectedApp.set(app);
  }

  protected closeModal(): void {
    this.selectedApp.set(null);
  }

  protected levelBadgeClass(level: number): string {
    switch (level) {
      case 3:
        return 'badge-level-3';
      case 2:
        return 'badge-level-2';
      case 1:
        return 'badge-level-1';
      default:
        return 'badge-level-0';
    }
  }

  protected levelFor(score: number): number {
    if (score >= 2.5) return 3;
    if (score >= 1.6) return 2;
    if (score >= 0.8) return 1;
    return 0;
  }

  protected scorePercent(score: number): number {
    return Math.min(100, Math.max(0, Math.round((score / 3.0) * 100)));
  }

  protected tierDescription(tier: string): string {
    switch (tier) {
      case 'Optimized':
        return 'Advanced maturity with continuous verification, zero SLA breaches, automated remediation and full telemetry.';
      case 'Defined':
        return 'Structured governance and security gating with noise-filtered SAST, SCA and containerized environments.';
      case 'Foundational':
        return 'Basic automated security testing and declarative build configuration in place.';
      default:
        return 'Initial security posture. Pipeline configuration requires integration of baseline security gates and testing.';
    }
  }
}
