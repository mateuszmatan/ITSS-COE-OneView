import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { EMPTY, Observable, catchError, finalize, ignoreElements, tap, throwError } from 'rxjs';
import { errorMessage } from '@common/core/errors';
import { Field, Fields, check, choice, line } from '@common/shared/fields';
import {
  failureText,
  flag,
  max,
  optional,
  revalidateAll,
  saveFailure,
  text,
} from '@common/shared/form-controls';
import { DIALOG } from '@common/ui/dialog';
import { AgileApi } from '../core/api';
import { AgileApplication, AgileFramework, AgileModel } from '../core/models';

export interface AgileProfileDialogData {
  application: AgileApplication;
  model: AgileModel;
  /** Told what someone else saved, once the window has loaded it after a conflict. */
  reloaded?: (current: AgileApplication) => void;
}

@Component({
  selector: 'ov-agile-profile-dialog',
  imports: [ReactiveFormsModule, DIALOG, Fields],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 ovDialogTitle>{{ title() }}</h2>
    </div>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <div class="modal-body">
        <p class="intro">
          The team that builds {{ application.name }} ({{ application.code }}) of
          {{ application.departmentName ?? 'no business service' }}. Fields left empty are not set.
        </p>
        <div class="form-fields">
          <ov-fields [group]="form" [fields]="fields" />
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
      width: min(600px, 86vw);
    }

    .intro {
      margin: 0 0 8px;
      color: var(--ov-muted);
    }

    .banner {
      margin: 10px 0 0;
    }
  `,
})
export class AgileProfileDialog {
  private readonly data = inject<AgileProfileDialogData>(DIALOG_DATA);
  private readonly dialogRef = inject<DialogRef<AgileApplication, AgileProfileDialog>>(DialogRef);
  private readonly api = inject(AgileApi);

  protected readonly application = this.data.application;
  /** The version a save must find: the one read on opening, or after someone else's change. */
  protected readonly version = signal(this.application.version);
  protected readonly title = computed(() =>
    this.version() === null
      ? `Set up the team of ${this.application.name}`
      : `The team of ${this.application.name}`,
  );
  protected readonly fields: readonly Field[] = [
    check('enabled', 'Takes part in Agile Assessment & Adoption', '', 12, {
      help: 'A team that takes part is assessed and shows its maturity in Agile Assessment & Adoption, and is counted in the summaries of its business service and of BBH.',
    }),
    line('teamName', 'Team', '', 6, {
      placeholder: 'Cert Squad',
      help: 'The name of the team that builds and runs the application.',
    }),
    choice(
      'framework',
      'Way of working',
      [
        { value: null, label: 'Not set' },
        ...this.data.model.frameworks.map((framework) => ({
          value: framework.code,
          label: framework.label,
        })),
      ],
      '',
      6,
      { help: 'The agile framework the team follows, for example Scrum or Kanban.' },
    ),
    line('coach', 'Agile coach', '', 6, {
      placeholder: 'Joe Coach',
      help: 'The agile coach who supports the team and usually makes its assessments.',
    }),
  ];

  private readonly values = valuesOf(this.application);
  protected readonly form = new FormGroup({
    enabled: flag(this.values.enabled),
    teamName: text(this.values.teamName, max(200)),
    framework: new FormControl<AgileFramework | null>(this.values.framework),
    coach: text(this.values.coach, max(200)),
  });
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly stale = `Not saved: someone else changed the team of ${this.application.name} after you opened it.`;

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
      .changeProfile(this.application.productId, {
        enabled: value.enabled,
        teamName: optional(value.teamName),
        framework: value.framework,
        coach: optional(value.coach),
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

/** The values of the form for the team of an application as the portal has it. */
function valuesOf(application: AgileApplication) {
  return {
    // A team that is set up takes part; saving it unticked keeps it out.
    enabled: application.version === null ? true : application.enabled,
    teamName: application.teamName ?? '',
    framework: application.framework,
    coach: application.coach ?? '',
  };
}
