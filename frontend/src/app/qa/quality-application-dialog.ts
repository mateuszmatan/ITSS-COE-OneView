import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { EMPTY, Observable, catchError, finalize, ignoreElements, tap, throwError } from 'rxjs';
import { errorMessage } from '@common/core/errors';
import { Field, Fields, check, count, line, mono } from '@common/shared/fields';
import {
  HTTP_URL_ERROR,
  flag,
  integer,
  max,
  optional,
  revalidateAll,
  saveFailure,
  failureText,
  text,
  url,
} from '@common/shared/form-controls';
import { DIALOG } from '@common/ui/dialog';
import { QualityApi } from '../core/api';
import { QualityApplication, QualityTargets } from '../core/models';
import { TARGET_FIELDS, targetText } from './quality-texts';

export interface QualityApplicationDialogData {
  application: QualityApplication;
  /** The targets of BBH, which apply where the application sets none. */
  bbhTargets: QualityTargets;
  /** Told what someone else saved, once the window has loaded it after a conflict. */
  reloaded?: (current: QualityApplication) => void;
}

const PROFILE_FIELDS: readonly Field[] = [
  check('enabled', 'Takes part in the QM Metric Tool', '', 12, {
    help: 'An application that takes part shows its QA lead, its quality targets and its dashboard in the QM Metric Tool, and is counted in the summaries of its business service and of BBH.',
  }),
  line('qaLead', 'QA lead', '', 6, {
    placeholder: 'Ann Lee',
    help: 'The person who leads the quality assurance of the application and answers for its targets.',
  }),
  mono('sonarProjectKey', 'SonarQube project key', '', 6, {
    placeholder: 'com.bbh:cert-scanner',
    help: 'The key of the SonarQube project that holds the code quality and coverage of the application.',
  }),
  mono('defectProjectKey', 'Defect project key', '', 6, {
    placeholder: 'CERT',
    help: 'The project of the defect tracker, for example the Jira project, that holds the defects of the application.',
  }),
  line('dashboardUrl', 'Grafana dashboard', '', 6, {
    placeholder: 'https://grafana.bbh.com/d/cert-quality',
    error: HTTP_URL_ERROR,
    help: 'The address of the Grafana dashboard with the quality metrics of the application, drawn from InfluxDB.',
  }),
];

@Component({
  selector: 'ov-quality-application-dialog',
  imports: [ReactiveFormsModule, DIALOG, Fields],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 ovDialogTitle>{{ title() }}</h2>
    </div>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <div class="modal-body">
        <p class="intro">
          {{ application.name }} ({{ application.code }}) of
          {{ application.departmentName ?? 'no business service' }}. Fields left empty are not set.
        </p>
        <div class="form-fields">
          <ov-fields [group]="form" [fields]="profileFields" />
        </div>
        <h3 class="sub-heading">Own quality targets</h3>
        <p class="section-help">
          Leave a target empty to use the BBH target; when BBH changes it, the application follows.
        </p>
        <div class="form-fields">
          <ov-fields [group]="form.controls.targets" [fields]="targetFields" />
        </div>
        @if (error(); as message) {
          <div class="banner" role="alert">{{ message }}</div>
        }
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-link" ovDialogClose>Cancel</button>
        <button type="submit" class="btn btn-primary" [disabled]="saving()">
          {{ version() === null ? 'Set up' : 'Save' }}
        </button>
      </div>
    </form>
  `,
  styles: `
    .modal-body {
      width: min(640px, 86vw);
    }

    .intro {
      margin: 0 0 8px;
      color: var(--ov-muted);
    }

    .sub-heading {
      margin: 14px 0 2px;
      font-size: 14px;
    }

    .banner {
      margin: 10px 0 0;
    }
  `,
})
export class QualityApplicationDialog {
  private readonly data = inject<QualityApplicationDialogData>(DIALOG_DATA);
  private readonly dialogRef =
    inject<DialogRef<QualityApplication, QualityApplicationDialog>>(DialogRef);
  private readonly api = inject(QualityApi);

  protected readonly application = this.data.application;
  /** The version a save must find: the one read on opening, or after someone else's change. */
  protected readonly version = signal(this.application.version);
  protected readonly title = computed(() =>
    this.version() === null
      ? `Set up ${this.application.name} in the QM Metric Tool`
      : `${this.application.name} in the QM Metric Tool`,
  );
  protected readonly profileFields = PROFILE_FIELDS;
  protected readonly targetFields: readonly Field[] = TARGET_FIELDS.map((field) =>
    count(field.key, field.input, '', 4, {
      min: field.min,
      max: field.max,
      step: 1,
      help: `${field.help} Leave it empty to use the BBH target.`,
      hint: `BBH target: ${targetText(field, this.data.bbhTargets[field.key])}`,
    }),
  );

  private readonly values = valuesOf(this.application);
  protected readonly form = new FormGroup({
    enabled: flag(this.values.enabled),
    qaLead: text(this.values.qaLead, max(200)),
    sonarProjectKey: text(this.values.sonarProjectKey, max(400)),
    defectProjectKey: text(this.values.defectProjectKey, max(100)),
    dashboardUrl: url(this.values.dashboardUrl, 1000),
    targets: new FormGroup({
      coveragePercent: integer(this.values.targets.coveragePercent, 0, 100),
      passRatePercent: integer(this.values.targets.passRatePercent, 0, 100),
      maxCriticalDefects: integer(this.values.targets.maxCriticalDefects, 0, TARGET_FIELDS[2].max),
    }),
  });
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly stale = `Not saved: someone else changed ${this.application.name} after you opened it.`;

  protected save(): void {
    revalidateAll(this.form);
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) {
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.api
      .change(this.application.productId, {
        enabled: value.enabled,
        qaLead: optional(value.qaLead),
        sonarProjectKey: optional(value.sonarProjectKey),
        defectProjectKey: optional(value.defectProjectKey),
        dashboardUrl: optional(value.dashboardUrl),
        targets: value.targets,
        version: this.version(),
      })
      .pipe(
        catchError((error) =>
          error instanceof HttpErrorResponse && error.status === 409
            ? this.reload()
            : throwError(() => error),
        ),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: (saved) => this.dialogRef.close(saved),
        error: (error) => this.error.set(failureText(saveFailure(this.form, error, this.stale))),
      });
  }

  /** Shows what someone else saved meanwhile, so the change is made again on top of it. */
  private reload(): Observable<never> {
    return this.api.application(this.application.productId).pipe(
      tap((current) => {
        this.version.set(current.version);
        this.form.reset(valuesOf(current));
        this.data.reloaded?.(current);
        this.error.set(
          `${this.stale} The form now shows their changes: make your change again, then save.`,
        );
      }),
      catchError((error) => {
        this.error.set(`${this.stale} Their changes could not be loaded: ${errorMessage(error)}`);
        return EMPTY;
      }),
      ignoreElements(),
    );
  }
}

/** The values of the form for an application as the portal has it. */
function valuesOf(application: QualityApplication) {
  return {
    // A new application is set up to take part; saving it unticked keeps it out.
    enabled: application.version === null ? true : application.enabled,
    qaLead: application.qaLead ?? '',
    sonarProjectKey: application.sonarProjectKey ?? '',
    defectProjectKey: application.defectProjectKey ?? '',
    dashboardUrl: application.dashboardUrl ?? '',
    targets: { ...application.targets },
  };
}
