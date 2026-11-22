import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Directive,
  DoCheck,
  ElementRef,
  computed,
  contentChild,
  inject,
  input,
  signal,
} from '@angular/core';
import { AbstractControl, FormGroupDirective, NgControl, NgForm, Validators } from '@angular/forms';
import { Subscription, merge } from 'rxjs';
import { OvHelp } from './help';

let nextId = 0;

export const uniqueId = (prefix: string) => `${prefix}-${nextId++}`;

/** A form control. The first one takes the initial focus of a dialog, ahead of the help icons before it. */
@Directive({
  selector: 'input[ovInput], textarea[ovInput], select[ovInput]',
  host: {
    cdkFocusInitial: '',
    '[id]': 'id',
    '[class.form-control]': '!select',
    '[class.form-control-sm]': '!select',
    '[class.form-select]': 'select',
    '[class.form-select-sm]': 'select',
    '[class.is-invalid]': 'errorState()',
    '[attr.aria-invalid]': 'errorState() || null',
    '[attr.aria-describedby]': 'describedBy()',
  },
})
export class OvInput implements DoCheck {
  readonly id = uniqueId('ov-input');
  readonly element =
    inject<ElementRef<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>>(ElementRef)
      .nativeElement;
  readonly ngControl = inject(NgControl, { optional: true, self: true });

  protected readonly select = this.element.tagName === 'SELECT';

  private readonly form =
    inject(FormGroupDirective, { optional: true }) ?? inject(NgForm, { optional: true });
  private readonly revision = signal(0);
  private watched: AbstractControl | null = null;
  private subscription?: Subscription;
  private snapshot = '';

  private readonly field = inject(OvFormField, { optional: true });

  readonly errorState = computed(() => {
    this.revision();
    const control = this.ngControl?.control;
    return !!control && control.invalid && (control.touched || !!this.form?.submitted);
  });

  protected readonly describedBy = computed(() =>
    this.field ? (this.errorState() ? this.field.errorId : this.field.hintId) : null,
  );

  constructor() {
    inject(DestroyRef).onDestroy(() => this.subscription?.unsubscribe());
  }

  ngDoCheck(): void {
    const control = this.ngControl?.control ?? null;
    if (control !== this.watched) {
      this.watched = control;
      this.subscription?.unsubscribe();
      this.subscription = control
        ? merge(control.events, this.form?.ngSubmit ?? []).subscribe(() => this.changed())
        : undefined;
    }
    const snapshot = `${control?.status}|${control?.touched}`;
    if (snapshot !== this.snapshot) {
      this.snapshot = snapshot;
      this.changed();
    }
  }

  required(): boolean {
    this.revision();
    return this.element.required || !!this.ngControl?.control?.hasValidator(Validators.required);
  }

  private changed(): void {
    this.revision.update((value) => value + 1);
  }
}

@Component({
  selector: 'ov-label',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ng-content />',
})
export class OvLabel {}

@Component({
  selector: 'ov-hint',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ng-content />',
})
export class OvHint {}

@Component({
  selector: 'ov-error',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ng-content />',
})
export class OvError {}

@Directive({
  selector: '[ovSuffix]',
  host: { class: 'ov-suffix' },
})
export class OvSuffix {}

@Component({
  selector: 'ov-form-field',
  imports: [OvHelp],
  changeDetection: ChangeDetectionStrategy.Eager,
  host: {
    class: 'ov-form-field',
    '[class.has-error]': 'errorState()',
    '[class.has-suffix]': '!!suffix()',
  },
  template: `
    <div class="form-label-row">
      <label class="form-label" [attr.for]="input()?.id">
        <ng-content select="ov-label" />
        @if (input()?.required()) {
          <span class="required-marker" aria-hidden="true"></span>
        }
      </label>
      @if (help(); as text) {
        <ov-help [text]="text" />
      }
    </div>
    <div class="control">
      <ng-content />
      <ng-content select="[ovSuffix]" />
    </div>
    @if (errorState()) {
      <div class="invalid-feedback d-block" [id]="errorId">
        <ng-content select="ov-error" />
      </div>
    } @else {
      <div class="form-text" [id]="hintId">
        <ng-content select="ov-hint" />
      </div>
    }
  `,
})
export class OvFormField {
  readonly help = input<string | null | undefined>();

  protected readonly input = contentChild(OvInput);
  protected readonly suffix = contentChild(OvSuffix);
  readonly hintId = uniqueId('ov-hint');
  readonly errorId = uniqueId('ov-error');
  protected readonly errorState = computed(() => this.input()?.errorState() ?? false);
}

export const FORM_FIELD = [OvFormField, OvLabel, OvHint, OvError, OvInput, OvSuffix] as const;
