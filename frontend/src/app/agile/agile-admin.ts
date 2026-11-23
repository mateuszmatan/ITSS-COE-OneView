import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { rxResource } from '@angular/core/rxjs-interop';
import { filter, switchMap } from 'rxjs';
import { OneViewContext, scopeNameIn } from '@common/context/oneview-context';
import { Notifier } from '@common/core/notifier';
import { askToConfirm } from '@common/shared/confirm-dialog';
import { counted } from '@common/shared/formatting';
import { GRID, GridColumn } from '@common/ui/grid';
import { LoadError } from '@common/ui/load-error';
import { OvLoading } from '@common/ui/loading';
import { AgileApi } from '../core/api';
import { AgileApplication, AgileAssessment, AgileModel } from '../core/models';
import { AgileAssessmentDialog, AgileAssessmentDialogData } from './agile-assessment-dialog';
import { AgileProfileDialog, AgileProfileDialogData } from './agile-profile-dialog';
import {
  agileParticipation,
  dayText,
  frameworkLabel,
  latestAssessment,
  levelText,
} from './agile-texts';

/** An assessment with the application it belongs to. */
export interface AssessmentRow {
  assessment: AgileAssessment;
  application: AgileApplication;
}

/** The Agile Assessment & Adoption tab of OneView Admin: the team of every application and its assessments. */
@Component({
  selector: 'ov-agile-admin',
  imports: [GRID, OvLoading, LoadError],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './agile-admin.html',
  styleUrl: './agile-admin.scss',
})
export class AgileAdmin {
  private readonly api = inject(AgileApi);
  private readonly dialog = inject(Dialog);
  private readonly notifier = inject(Notifier);
  private readonly context = inject(OneViewContext);

  protected readonly model = rxResource({ stream: () => this.api.model() });
  protected readonly applications = rxResource({ stream: () => this.api.applications() });
  protected readonly participation = agileParticipation;
  protected readonly dayText = dayText;

  protected readonly level = this.context.level;
  private readonly all = computed(() =>
    this.applications.hasValue() ? this.applications.value() : [],
  );
  protected readonly scoped = computed(() =>
    this.all().filter((application) =>
      this.context.includes(application.departmentId, application.productId),
    ),
  );
  protected readonly scopeName = computed(() =>
    scopeNameIn(this.context.scope(), this.all(), this.context.scopeName()),
  );
  protected readonly summary = computed(() => {
    const applications = this.scoped();
    const taking = applications.filter((application) => application.enabled).length;
    const verb = applications.length === 1 ? 'takes part' : 'take part';
    return `${taking} of ${counted(applications.length, 'team')} ${verb}`;
  });

  /** Every assessment of the applications shown, the latest first. */
  protected readonly assessments = computed<AssessmentRow[]>(() =>
    this.scoped()
      .flatMap((application) =>
        application.assessments.map((assessment) => ({ assessment, application })),
      )
      .sort(
        (a, b) =>
          b.assessment.assessedOn.localeCompare(a.assessment.assessedOn) ||
          b.assessment.id - a.assessment.id,
      ),
  );

  private readonly frameworks = computed(() =>
    this.model.hasValue() ? this.model.value().frameworks : [],
  );

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
    {
      key: 'framework',
      header: 'Way of working',
      value: (application) => frameworkLabel(this.frameworks(), application.framework),
      wrapHeader: true,
    },
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
    { key: 'actions', header: '', width: 200 },
  ]);

  protected readonly assessmentId = (row: AssessmentRow) => row.assessment.id;
  protected readonly assessmentColumns = computed<GridColumn<AssessmentRow>[]>(() => [
    {
      key: 'assessedOn',
      header: 'Day',
      value: (row) => dayText(row.assessment.assessedOn),
      sortValue: (row) => row.assessment.assessedOn,
      width: 120,
    },
    {
      key: 'application',
      header: 'Application',
      value: (row) => row.application.name,
      minWidth: 160,
    },
    {
      key: 'assessor',
      header: 'Assessed by',
      value: (row) => row.assessment.assessor,
      minWidth: 120,
    },
    {
      key: 'overall',
      header: 'Level',
      value: (row) => levelText(row.assessment.overallLevel),
      numeric: true,
      width: 90,
    },
    {
      key: 'notes',
      header: 'Notes',
      value: (row) => row.assessment.notes ?? '',
      wrap: true,
      minWidth: 240,
    },
    { key: 'actions', header: '', width: 100 },
  ]);

  /** Why an assessment cannot be recorded for the application, or null when it can. */
  protected assessHint(application: AgileApplication): string | null {
    return application.enabled
      ? null
      : `Set up the team of ${application.name} first: only a team that takes part is assessed.`;
  }

  protected editTeam(application: AgileApplication): void {
    const model = this.loadedModel();
    if (!model) {
      return;
    }
    this.dialog
      .open<AgileApplication, AgileProfileDialogData, AgileProfileDialog>(AgileProfileDialog, {
        data: { application, model },
      })
      .closed.pipe(filter((saved): saved is AgileApplication => !!saved))
      .subscribe((saved) => {
        this.applications.update((list) =>
          list?.map((entry) => (entry.productId === saved.productId ? saved : entry)),
        );
        this.notifier.success(
          saved.enabled
            ? `The team of ${saved.name} saved. It takes part in Agile Assessment & Adoption.`
            : `The team of ${saved.name} saved. It does not take part in Agile Assessment & Adoption.`,
        );
      });
  }

  protected assess(application: AgileApplication): void {
    const model = this.loadedModel();
    if (!model) {
      return;
    }
    this.dialog
      .open<AgileAssessment, AgileAssessmentDialogData, AgileAssessmentDialog>(
        AgileAssessmentDialog,
        { data: { application, model } },
      )
      .closed.pipe(filter((saved): saved is AgileAssessment => !!saved))
      .subscribe((saved) => {
        this.notifier.success(
          `Assessment of ${application.name} of ${dayText(saved.assessedOn)} recorded: level ${levelText(saved.overallLevel)}.`,
        );
        this.applications.reload();
      });
  }

  protected delete(row: AssessmentRow): void {
    const { assessment, application } = row;
    const day = dayText(assessment.assessedOn);
    askToConfirm(this.dialog, {
      title: `Delete the assessment of ${application.name} of ${day}?`,
      message:
        `The levels and notes ${assessment.assessor} recorded on ${day} are deleted. ` +
        'The other assessments of the team stay as they are. This cannot be undone.',
      confirmLabel: 'Delete assessment',
      danger: true,
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.api.deleteAssessment(assessment.id)),
      )
      .subscribe({
        next: () => {
          this.notifier.success(`Assessment of ${application.name} of ${day} deleted`);
          this.applications.reload();
        },
        error: (error) => this.notifier.error(error),
      });
  }

  protected reload(): void {
    this.model.reload();
    this.applications.reload();
  }

  private loadedModel(): AgileModel | null {
    return this.model.hasValue() ? this.model.value() : null;
  }
}
