import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AdminArea, OneViewTab } from '../core/sections';

export interface TabGroup {
  label: string | null;
  tabs: OneViewTab[];
}

/** The tabs in their order, those next to each other with the same group together. */
export function tabGroups(tabs: readonly OneViewTab[]): TabGroup[] {
  const groups: TabGroup[] = [];
  for (const tab of tabs) {
    const label = tab.group ?? null;
    const last = groups.at(-1);
    if (last && last.label === label) {
      last.tabs.push(tab);
    } else {
      groups.push({ label, tabs: [tab] });
    }
  }
  return groups;
}

@Component({
  selector: 'ov-admin-page',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page admin">
      <header class="page-header">
        <div>
          <h1>{{ area().section.heading }}</h1>
          <p class="page-description">{{ area().section.description }}</p>
        </div>
      </header>
      <nav class="tab-bar" [attr.aria-label]="area().section.heading">
        @for (group of groups(); track $index) {
          <div
            class="tab-group"
            [attr.role]="group.label ? 'group' : null"
            [attr.aria-label]="group.label"
          >
            @if (group.label) {
              <span class="tab-group-label" aria-hidden="true">{{ group.label }}</span>
            }
            @for (tab of group.tabs; track tab.path) {
              <a [routerLink]="tab.path" routerLinkActive="active" ariaCurrentWhenActive="page">{{
                tab.label
              }}</a>
            }
          </div>
        }
      </nav>
      <router-outlet />
    </div>
  `,
  styles: `
    .page-header {
      margin-bottom: 0;
      border-bottom: 0;
    }

    .tab-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 0 30px;
      margin-bottom: 12px;
      border-bottom: 1px solid var(--ov-border);

      .tab-group {
        display: flex;
        flex-wrap: wrap;
        align-items: flex-end;
        gap: 0 18px;
      }

      .tab-group-label {
        padding: 6px 0 7px;
        color: var(--ov-muted);
        font-size: 10.5px;
        font-weight: 600;
        letter-spacing: 0.06em;
        text-transform: uppercase;
        white-space: nowrap;
      }

      a {
        margin-bottom: -1px;
        padding: 6px 0 5px;
        border-bottom: 2px solid transparent;
        color: var(--ov-muted);
        font-size: 13px;
        font-weight: 500;
        text-decoration: none;
        white-space: nowrap;

        &:hover,
        &:focus-visible {
          color: var(--ov-navy);
        }

        &.active {
          color: var(--ov-navy);
          border-bottom-color: var(--ov-navy);
        }
      }
    }
  `,
})
export class AdminPage {
  readonly area = input.required<AdminArea>();

  protected readonly groups = computed(() => tabGroups(this.area().tabs));
}
