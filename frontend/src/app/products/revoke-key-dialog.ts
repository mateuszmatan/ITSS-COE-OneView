import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { PipelinesApi } from '../core/api';
import { errorMessage } from '@common/core/errors';
import { Pipeline } from '../core/models';
import { pipelineName, typeName } from '../pipelines/pipeline-texts';
import { filled, max, text } from '@common/shared/form-controls';
import { errorText } from '@common/shared/form-errors';
import { DIALOG } from '@common/ui/dialog';
import { FORM_FIELD } from '@common/ui/form-field';
import { OvSpinner } from '@common/ui/loading';

@Component({
  selector: 'ov-revoke-key-dialog',
  imports: [ReactiveFormsModule, DIALOG, FORM_FIELD, OvSpinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 ovDialogTitle>Invalidate the key of the pipeline {{ name }}?</h2>
    </div>
    <form [formGroup]="form" (ngSubmit)="revoke()" novalidate>
      <div class="modal-body">
        <div class="banner">
          <span>
            The {{ typeName }} pipeline of
            <strong class="mono">{{ pipeline.serviceName }}</strong>
            is refused its settings from now on and stops at its next start. This key cannot be used
            again; to let the pipeline run later, regenerate its key.
          </span>
        </div>
        <ov-form-field
          class="full-width"
          help="Why the key may no longer be used. Jenkins writes the reason to the build log, so do not include the key or any other secret."
        >
          <ov-label>Reason</ov-label>
          <textarea
            ovInput
            formControlName="reason"
            required
            rows="3"
            placeholder="Service retired, key leaked in a build log, ..."
          ></textarea>
          <ov-hint
            >Kept in the key history and sent to Jenkins when the pipeline is refused its
            settings</ov-hint
          >
          <ov-error>{{ errorText(reason) }}</ov-error>
        </ov-form-field>
        @if (error(); as message) {
          <div class="banner" role="alert">{{ message }}</div>
        }
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-link" ovDialogClose>Cancel</button>
        <button type="submit" class="btn btn-danger" [disabled]="saving()">
          @if (saving()) {
            <ov-spinner />
          }
          Invalidate key
        </button>
      </div>
    </form>
  `,
  styles: `
    .modal-body {
      width: min(520px, 80vw);
    }
  `,
})
export class RevokeKeyDialog {
  protected readonly pipeline = inject<Pipeline>(DIALOG_DATA);
  protected readonly name = pipelineName(this.pipeline);
  protected readonly typeName = typeName(this.pipeline.type);
  private readonly dialogRef = inject<DialogRef<Pipeline, RevokeKeyDialog>>(DialogRef);
  private readonly api = inject(PipelinesApi);

  protected readonly form = new FormGroup({
    reason: text('', filled, max(500)),
  });
  protected readonly reason = this.form.controls.reason;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly errorText = errorText;

  protected revoke(): void {
    this.reason.markAsTouched();
    if (this.reason.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api
      .revokeKey(this.pipeline.id, this.reason.value.trim())
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (pipeline) => this.dialogRef.close(pipeline),
        error: (error) => this.error.set(errorMessage(error)),
      });
  }
}
