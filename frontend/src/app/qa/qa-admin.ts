import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { filter, finalize } from 'rxjs';
import { OneViewContext, scopeNameIn } from '@common/context/oneview-context';
import { Notifier } from '@common/core/notifier';
import { HasUnsavedChanges } from '@common/core/unsaved-changes';
import { Field, Fields, count } from '@common/shared/fields';
import { failureText, integer, revalidateAll, saveFailure } from '@common/shared/form-controls';
import { RelativeTimePipe, formatRelative } from '@common/shared/formatting';
import { GRID, GridColumn } from '@common/ui/grid';
import { LoadError } from '@common/ui/load-error';
import { OvLoading } from '@common/ui/loading';
import { QualityApi } from '../core/api';
import { QualityApplication, QualityTargets } from '../core/models';
import {
  QualityApplicationDialog,
  QualityApplicationDialogData,
} from './quality-application-dialog';
import {
  MAX_CRITICAL_DEFECTS,
  TARGET_FIELDS,
  ownTargetsSummary,
  participation,
} from './quality-texts';

const BBH_FIELDS: readonly Field[] = TARGET_FIELDS.map((field) =>
  count(field.key, field.input, '', 4, {
    min: field.min,
    max: field.max,
    step: 1,
    help: `${field.help} Every application that sets no target of its own works to this one.`,
  }),
);

/** The QM Metric Tool tab of OneView Admin: the targets of BBH and how every application takes part. */
@Component({
  selector: 'ov-qa-admin',
  imports: [ReactiveFormsModule, Fields, GRID, OvLoading, LoadError, RelativeTimePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './qa-admin.html',
  styleUrl: './qa-admin.scss',
})
export class QaAdmin implements HasUnsavedChanges {
  private readonly api = inject(QualityApi);
  private readonly dialog = inject(Dialog);
  private readonly notifier = inject(Notifier);
  private readonly context = inject(OneViewContext);

  protected readonly defaults = rxResource({ stream: () => this.api.defaults() });
  protected readonly applications = rxResource({ stream: () => this.api.applications() });

  protected readonly fields = BBH_FIELDS;
  protected readonly participation = participation;
  protected readonly form = new FormGroup({
    coveragePercent: integer(null, 0, 100, Validators.required),
    passRatePercent: integer(null, 0, 100, Validators.required),
    maxCriticalDefects: integer(null, 0, MAX_CRITICAL_DEFECTS, Validators.required),
  });
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly conflict = signal(false);

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
    return `${taking} of ${applications.length} ${applications.length === 1 ? 'application takes' : 'applications take'} part`;
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
    { key: 'enabled', header: 'QM Metric Tool', value: participation, width: 150 },
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
    {
      key: 'updatedAt',
      header: 'Last changed',
      value: (application) => (application.updatedAt ? formatRelative(application.updatedAt) : ''),
      sortValue: (application) => application.updatedAt ?? '',
      width: 130,
    },
    { key: 'actions', header: '', width: 110 },
  ]);

  constructor() {
    effect(() => {
      if (this.defaults.hasValue()) {
        this.form.reset({ ...this.defaults.value().targets });
      }
    });
  }

  hasUnsavedChanges(): boolean {
    return this.form.dirty;
  }

  protected save(): void {
    this.saveError.set(null);
    this.conflict.set(false);
    revalidateAll(this.form);
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving() || !this.defaults.hasValue()) {
      return;
    }
    const targets: QualityTargets = this.form.getRawValue();
    this.saving.set(true);
    this.api
      .changeDefaults({ targets, version: this.defaults.value().version })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (saved) => {
          this.defaults.set(saved);
          this.notifier.success(
            'BBH quality targets saved. Every application without targets of its own works to them now.',
          );
        },
        error: (error) => {
          const failure = saveFailure(
            this.form,
            error,
            'Someone else saved the BBH targets after you opened this page, so nothing was saved.',
          );
          this.conflict.set(failure.conflict);
          this.saveError.set(failureText(failure));
        },
      });
  }

  protected discard(): void {
    if (this.defaults.hasValue()) {
      this.form.reset({ ...this.defaults.value().targets });
    }
    this.saveError.set(null);
  }

  protected reload(): void {
    this.conflict.set(false);
    this.saveError.set(null);
    this.defaults.reload();
    this.applications.reload();
  }

  protected edit(application: QualityApplication): void {
    if (!this.defaults.hasValue()) {
      return;
    }
    this.dialog
      .open<QualityApplication, QualityApplicationDialogData, QualityApplicationDialog>(
        QualityApplicationDialog,
        {
          data: {
            application,
            bbhTargets: this.defaults.value().targets,
            // The list shows what someone else saved even when the window is then closed unsaved.
            reloaded: (current) => this.show(current),
          },
        },
      )
      .closed.pipe(filter((saved): saved is QualityApplication => !!saved))
      .subscribe((saved) => {
        this.show(saved);
        this.notifier.success(
          saved.enabled
            ? `${saved.name} saved. It takes part in the QM Metric Tool.`
            : `${saved.name} saved. It does not take part in the QM Metric Tool.`,
        );
      });
  }

  /** Shows an application in the list as the portal has it now. */
  private show(changed: QualityApplication): void {
    this.applications.update((list) =>
      list?.map((entry) => (entry.productId === changed.productId ? changed : entry)),
    );
  }
}
