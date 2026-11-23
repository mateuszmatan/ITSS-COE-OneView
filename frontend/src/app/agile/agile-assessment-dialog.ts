import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { finalize } from 'rxjs';
import { Field, Fields, area, choice, line } from '@common/shared/fields';
import {
  failureText,
  filled,
  max,
  optional,
  revalidateAll,
  saveFailure,
  text,
} from '@common/shared/form-controls';
import { errorMessage } from '@common/core/errors';
import { DIALOG } from '@common/ui/dialog';
import { AgileApi } from '../core/api';
import { AgileApplication, AgileAssessment, AgileDimension, AgileModel } from '../core/models';
import { dayText, today } from './agile-texts';

export interface AgileAssessmentDialogData {
  application: AgileApplication;
  model: AgileModel;
}

/** A day no later than the one given; days are compared as written, 2026-11-23. */
export function notAfter(last: string): ValidatorFn {
  return (control) =>
    control.value && String(control.value) > last
      ? { rule: `Not in the future: ${dayText(last)} at the latest` }
      : null;
}

@Component({
  selector: 'ov-agile-assessment-dialog',
  imports: [ReactiveFormsModule, DIALOG, Fields],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 ovDialogTitle>Record an assessment of {{ application.name }}</h2>
    </div>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <div class="modal-body">
        <p class="intro">
          Rate the team of {{ application.teamName ?? application.name }} in every dimension, from
          {{ model.minLevel }}, just starting, to {{ model.maxLevel }}, fully mature and improving
          on its own. The overall level is the mean of the levels.
        </p>
        <div class="form-fields">
          <ov-fields [group]="form" [fields]="fields" />
        </div>
        <h3 class="sub-heading">Levels</h3>
        <div class="form-fields">
          <ov-fields [group]="form.controls.levels" [fields]="levelFields" />
        </div>
        <div class="form-fields">
          <ov-fields [group]="form" [fields]="noteFields" />
        </div>
        @if (error(); as message) {
          <div class="banner" role="alert">{{ message }}</div>
        }
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-link" ovDialogClose>Cancel</button>
        <button type="submit" class="btn btn-primary" [disabled]="saving()">
          Record assessment
        </button>
      </div>
    </form>
  `,
  styles: `
    .modal-body {
      width: min(680px, 86vw);
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
export class AgileAssessmentDialog {
  private readonly data = inject<AgileAssessmentDialogData>(DIALOG_DATA);
  private readonly dialogRef = inject<DialogRef<AgileAssessment, AgileAssessmentDialog>>(DialogRef);
  private readonly api = inject(AgileApi);

  protected readonly application = this.data.application;
  protected readonly model = this.data.model;
  /** Today in the time zone of the browser, the latest day an assessment can have. */
  private readonly lastDay = today();

  protected readonly fields: readonly Field[] = [
    line('assessedOn', 'Day of the assessment', '', 6, {
      type: 'date',
      help: 'The day the team was assessed. It cannot be in the future.',
    }),
    line('assessor', 'Assessed by', '', 6, {
      placeholder: 'Joe Coach',
      help: 'Who made the assessment, usually the agile coach of the team.',
    }),
  ];
  protected readonly levelFields: readonly Field[] = this.model.dimensions.map((dimension) =>
    choice(dimension.code, dimension.label, this.levelOptions(), '', 4, {
      help: `${dimension.description ?? dimension.label} Rate it from ${this.model.minLevel} to ${this.model.maxLevel}.`,
    }),
  );
  protected readonly noteFields: readonly Field[] = [
    area('notes', 'Notes', '', 12, {
      help: 'What the assessment found and what the team works on next. Everyone who opens the application sees them.',
    }),
  ];

  protected readonly form = new FormGroup({
    assessedOn: text(this.lastDay, filled, notAfter(this.lastDay)),
    assessor: text(this.application.coach, filled, max(200)),
    levels: new FormGroup<Record<string, FormControl<number | null>>>(
      Object.fromEntries(
        this.model.dimensions.map((dimension) => [
          dimension.code,
          new FormControl<number | null>(null, Validators.required),
        ]),
      ),
    ),
    notes: text('', max(4000)),
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
    const levels: Partial<Record<AgileDimension, number>> = {};
    for (const [code, level] of Object.entries(value.levels)) {
      if (level !== null) {
        levels[code as AgileDimension] = level;
      }
    }
    this.saving.set(true);
    this.error.set(null);
    this.api
      .recordAssessment(this.application.productId, {
        assessedOn: value.assessedOn,
        assessor: value.assessor.trim(),
        levels,
        notes: optional(value.notes),
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (saved) => this.dialogRef.close(saved),
        // The portal refuses with 409 the assessment of a team that does not take part, and says why.
        error: (error) =>
          this.error.set(failureText(saveFailure(this.form, error, errorMessage(error)))),
      });
  }

  private levelOptions() {
    const options: { value: number | null; label: string }[] = [
      { value: null, label: 'Choose a level' },
    ];
    for (let level = this.model.minLevel; level <= this.model.maxLevel; level++) {
      options.push({ value: level, label: String(level) });
    }
    return options;
  }
}
