import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Department } from '../core/models';
import { filled, max, text } from '@common/shared/form-controls';
import { errorText } from '@common/shared/form-errors';
import { DIALOG } from '@common/ui/dialog';
import { FORM_FIELD } from '@common/ui/form-field';

export interface ProductNameDialogData {
  departments: readonly Department[];
  departmentId: number | null;
}

export interface NamedProduct {
  name: string;
  departmentId: number;
}

@Component({
  selector: 'ov-product-name-dialog',
  imports: [ReactiveFormsModule, DIALOG, FORM_FIELD],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 ovDialogTitle>Add product</h2>
    </div>
    <form [formGroup]="form" (ngSubmit)="next()" novalidate>
      <div class="modal-body">
        <p>
          Start with the business service the product belongs to and its name. The portal makes the
          product's code from the name; you can change it on the next page.
        </p>
        <ov-form-field
          class="full-width"
          help="The business service the product belongs to. The portal groups products by it."
        >
          <ov-label>Business Service</ov-label>
          <select ovInput formControlName="departmentId">
            @for (department of data.departments; track department.id) {
              <option [ngValue]="department.id">{{ department.name }}</option>
            }
          </select>
          <ov-error>{{ errorText(form.controls.departmentId) }}</ov-error>
        </ov-form-field>
        <ov-form-field
          class="full-width"
          help="The full name of the product as the portal shows it. No other product may use it."
        >
          <ov-label>Product name</ov-label>
          <input ovInput formControlName="name" placeholder="CertScanner" required />
          <ov-error>{{ errorText(name) }}</ov-error>
        </ov-form-field>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-link" ovDialogClose>Cancel</button>
        <button type="submit" class="btn btn-primary">Continue</button>
      </div>
    </form>
  `,
  styles: `
    .modal-body {
      display: flex;
      flex-direction: column;
      gap: 10px;
      width: min(420px, 80vw);
    }
    p {
      margin: 0;
    }
  `,
})
export class ProductNameDialog {
  protected readonly data = inject<ProductNameDialogData>(DIALOG_DATA);
  private readonly dialogRef = inject<DialogRef<NamedProduct, ProductNameDialog>>(DialogRef);

  protected readonly form = new FormGroup({
    departmentId: new FormControl(
      this.data.departments.find((department) => department.id === this.data.departmentId)?.id ??
        null,
      Validators.required,
    ),
    name: text('', filled, max(200)),
  });
  protected readonly name = this.form.controls.name;
  protected readonly errorText = errorText;

  protected next(): void {
    this.form.markAllAsTouched();
    if (this.form.valid) {
      this.dialogRef.close({
        name: this.name.value.trim(),
        departmentId: this.form.controls.departmentId.value!,
      });
    }
  }
}
