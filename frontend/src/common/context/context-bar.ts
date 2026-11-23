import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { errorMessage } from '../core/errors';
import { NOT_IN_A_DEPARTMENT } from '../departments/departments';
import { counted } from '../shared/formatting';
import { FORM_FIELD } from '../ui/form-field';
import { ContextApplication, OneViewContext } from './oneview-context';

interface ApplicationGroup {
  name: string;
  applications: ContextApplication[];
}

/**
 * The business service and the application a person looks at, above every page that follows them. It reads what
 * there is to choose from each time it is shown.
 */
@Component({
  selector: 'ov-context-bar',
  imports: [ReactiveFormsModule, FORM_FIELD],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="context-bar" aria-label="Business service and application">
      <ov-form-field
        class="context-field"
        help="Every page shows the business service you choose here: its pipelines, its quality and its agile maturity. All business services shows the whole of BBH. Self-service preselects it for a new product, and this browser remembers your choice."
      >
        <ov-label>Business service</ov-label>
        <select ovInput [formControl]="department" data-context="department">
          <option [ngValue]="null">All business services</option>
          @for (department of context.departments(); track department.id) {
            <option [ngValue]="department.id">{{ department.name }}</option>
          }
        </select>
      </ov-form-field>
      <ov-form-field
        class="context-field"
        help="Choose one application (a product of the catalog) to see it alone. All applications sums up every application of the business service."
      >
        <ov-label>Application</ov-label>
        <select ovInput [formControl]="application" data-context="application">
          <option [ngValue]="null">{{ allApplications() }}</option>
          @if (context.departmentId() === null) {
            @for (group of groups(); track group.name) {
              <optgroup [label]="group.name">
                @for (application of group.applications; track application.id) {
                  <option [ngValue]="application.id">{{ application.name }}</option>
                }
              </optgroup>
            }
          } @else {
            @for (application of context.applicationsInScope(); track application.id) {
              <option [ngValue]="application.id">{{ application.name }}</option>
            }
          }
        </select>
      </ov-form-field>
      <p class="context-summary" aria-live="polite">
        @if (context.loadError(); as error) {
          <span class="context-error" role="alert"
            >The business services could not be read ({{ problem(error) }}).</span
          >
          <button type="button" class="btn btn-link" (click)="context.refresh()">Try again</button>
        } @else {
          {{ summary() }}
        }
      </p>
    </section>
  `,
  styles: `
    :host {
      display: block;
      max-width: 1360px;
      margin: 0 auto;
      padding: 12px var(--ov-gutter) 0;
    }

    .context-bar {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      gap: 8px 20px;
      padding: 8px 14px 10px;
      border: 1px solid var(--ov-border);
      border-left: 4px solid var(--ov-navy);
      border-radius: 4px;
      background: var(--ov-card);
    }

    .context-field select {
      width: 260px;
      max-width: 100%;
    }

    .context-summary {
      flex: 1 1 220px;
      margin: 0 0 6px;
      color: var(--ov-muted);
      font-size: 12.5px;
      text-align: right;
    }

    .context-error {
      color: var(--ov-danger);
    }

    @media (max-width: 700px) {
      .context-summary {
        text-align: left;
      }
    }
  `,
})
export class ContextBar {
  protected readonly context = inject(OneViewContext);

  protected readonly department = new FormControl<number | null>(this.context.departmentId());
  protected readonly application = new FormControl<number | null>(this.context.productId());

  protected readonly groups = computed<ApplicationGroup[]>(() => {
    const applications = this.context.applications();
    const groups: ApplicationGroup[] = this.context.departments().map((department) => ({
      name: department.name,
      applications: applications.filter(
        (application) => application.departmentId === department.id,
      ),
    }));
    const placed = new Set(this.context.departments().map((department) => department.id));
    const unplaced = applications.filter(
      (application) => application.departmentId === null || !placed.has(application.departmentId),
    );
    return [...groups, { name: NOT_IN_A_DEPARTMENT, applications: unplaced }].filter(
      (group) => group.applications.length,
    );
  });

  protected readonly allApplications = computed(() =>
    this.context.departmentId() === null
      ? 'All applications'
      : `All applications of ${this.context.department()?.name ?? 'the business service'}`,
  );

  protected readonly summary = computed(() => {
    if (!this.context.loaded()) {
      return '';
    }
    switch (this.context.level()) {
      case 'application': {
        const application = this.context.application();
        return application
          ? `Showing ${application.name} (${application.code})${application.departmentName ? ' of ' + application.departmentName : ''}`
          : '';
      }
      case 'department':
        return `Showing ${this.context.scopeName()}: ${counted(this.context.applicationsInScope().length, 'application')}`;
      default:
        return (
          `Showing all of BBH: ${counted(this.context.applications().length, 'application')} in ` +
          counted(this.context.departments().length, 'business service')
        );
    }
  });

  protected readonly problem = errorMessage;

  constructor() {
    this.context.refresh();
    effect(() => {
      const departmentId = this.context.departmentId();
      if (this.department.value !== departmentId) {
        this.department.setValue(departmentId, { emitEvent: false });
      }
    });
    effect(() => {
      const productId = this.context.productId();
      if (this.application.value !== productId) {
        this.application.setValue(productId, { emitEvent: false });
      }
    });
    this.department.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((departmentId) => this.context.choose(departmentId, null));
    this.application.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((productId) => this.context.chooseApplication(productId));
  }
}
