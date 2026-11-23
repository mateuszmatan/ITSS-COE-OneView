import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export interface PlannedCapability {
  title: string;
  description: string;
}

/** What an area of OneView is to show next, every item marked as planned. */
@Component({
  selector: 'ov-area-plan',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card plan">
      <header class="card-header">
        <h2>Coming next</h2>
      </header>
      <p class="section-help">{{ intro() }}</p>
      <ul class="capabilities">
        @for (capability of capabilities(); track capability.title) {
          <li class="capability">
            <div class="capability-heading">
              <h3>{{ capability.title }}</h3>
              <span class="planned">Planned</span>
            </div>
            <p>{{ capability.description }}</p>
          </li>
        }
      </ul>
    </section>
  `,
  styles: `
    .plan {
      padding: 12px 14px;
    }

    .capabilities {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 8px 16px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .capability {
      padding: 8px 10px;
      border-left: 3px solid var(--ov-border);

      p {
        margin: 4px 0 0;
        color: var(--ov-muted);
        font-size: 12.5px;
      }
    }

    .capability-heading {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 8px;

      h3 {
        margin: 0;
        font-size: 14px;
      }
    }

    .planned {
      padding: 1px 8px;
      border-radius: 10px;
      background: var(--ov-neutral-bg);
      color: var(--ov-muted);
      font-size: 11px;
      font-weight: 600;
      white-space: nowrap;
    }
  `,
})
export class AreaPlan {
  readonly capabilities = input.required<readonly PlannedCapability[]>();
  readonly intro = input('This area is being built. It will also show the following.');
}
