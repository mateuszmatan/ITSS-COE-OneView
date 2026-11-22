import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { OneViewSection } from '@common/core/sections';

export interface PlannedCapability {
  title: string;
  description: string;
}

@Component({
  selector: 'ov-area-plan',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>{{ section().heading }}</h1>
          <p class="page-description">{{ section().description }}</p>
        </div>
      </header>

      <p class="notice" role="status">
        This area is being built. It will show the following once its data is connected.
      </p>

      <ul class="capabilities">
        @for (capability of capabilities(); track capability.title) {
          <li class="card capability">
            <div class="capability-heading">
              <h2>{{ capability.title }}</h2>
              <span class="planned">Planned</span>
            </div>
            <p>{{ capability.description }}</p>
          </li>
        }
      </ul>
    </div>
  `,
  styles: `
    .notice {
      margin: 0 0 12px;
      padding: 8px 12px;
      border: 1px solid var(--ov-border);
      border-radius: 4px;
      background: var(--ov-info-bg);
      color: var(--ov-text);
    }

    .capabilities {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 12px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .capability {
      padding: 12px 14px;

      p {
        margin: 6px 0 0;
        color: var(--ov-muted);
      }
    }

    .capability-heading {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 8px;

      h2 {
        margin: 0;
        font-size: 15px;
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
  readonly section = input.required<OneViewSection>();
  readonly capabilities = input.required<readonly PlannedCapability[]>();
}
