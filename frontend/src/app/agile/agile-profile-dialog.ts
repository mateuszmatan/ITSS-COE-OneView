import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
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
}

@Component({
  selector: 'ov-agile-profile-dialog',
  imports: [ReactiveFormsModule, DIALOG, Fields],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 ovDialogTitle>{{ title }}</h2>
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
          {{ application.version === null ? 'Set up' : 'Save' }}
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
  protected readonly title =
    this.application.version === null
      ? `Set up the team of ${this.application.name}`
      : `The team of ${this.application.name}`;
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

  protected readonly form = new FormGroup({
    // A team that is set up takes part; saving it unticked keeps it out.
    enabled: flag(this.application.version === null ? true : this.application.enabled),
    teamName: text(this.application.teamName, max(200)),
    framework: new FormControl<AgileFramework | null>(this.application.framework),
    coach: text(this.application.coach, max(200)),
  });
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

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
        version: this.application.version,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (saved) => this.dialogRef.close(saved),
        error: (error) =>
          this.error.set(
            failureText(
              saveFailure(
                this.form,
                error,
                `Someone else changed the team of ${this.application.name} after you opened it, so nothing was saved. Close this, then open it again to see their changes.`,
              ),
            ),
          ),
      });
  }
}
