import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AREAS, LANDING } from '../core/sections';

@Component({
  selector: 'ov-landing-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>{{ landing.heading }}</h1>
          <p class="page-description">{{ landing.description }}</p>
        </div>
      </header>

      <ul class="areas">
        @for (area of areas; track area.key) {
          <li>
            <a class="card area-card" [routerLink]="area.path">
              <h2>{{ area.label }}</h2>
              <p class="area-description">{{ area.description }}</p>
              @if (area.sections.length > 1) {
                <ul class="area-sections" [attr.aria-label]="'Sections of ' + area.label">
                  @for (section of area.sections; track section.path) {
                    <li>{{ section.label }}</li>
                  }
                </ul>
              }
              <span class="area-open">Open {{ area.label }}</span>
            </a>
          </li>
        }
      </ul>
    </div>
  `,
  styles: `
    .areas {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 16px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .areas > li {
      display: flex;
    }

    .area-card {
      display: flex;
      flex: 1;
      flex-direction: column;
      gap: 10px;
      min-height: 220px;
      padding: 18px 20px;
      border-top: 4px solid var(--ov-navy);
      border-radius: 4px;
      color: var(--ov-text);
      text-decoration: none;
      transition:
        box-shadow 0.15s ease,
        border-color 0.15s ease;

      &:hover,
      &:focus-visible {
        border-color: var(--ov-navy);
        box-shadow: 0 4px 14px rgba(12, 35, 64, 0.16);
      }

      &:focus-visible {
        outline: 2px solid var(--ov-navy);
        outline-offset: 2px;
      }

      h2 {
        margin: 0;
        color: var(--ov-navy);
        font-family: var(--ov-serif);
        font-size: 21px;
        font-weight: 400;
      }
    }

    .area-description {
      margin: 0;
      color: var(--ov-muted);
      line-height: 1.45;
    }

    .area-sections {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      margin: 0;
      padding: 0;
      list-style: none;

      li {
        padding: 2px 9px;
        border-radius: 10px;
        background: var(--ov-neutral-bg);
        color: var(--ov-neutral);
        font-size: 11.5px;
        font-weight: 600;
        white-space: nowrap;
      }
    }

    .area-open {
      margin-top: auto;
      padding-top: 6px;
      color: var(--ov-navy);
      font-size: 13px;
      font-weight: 600;

      &::after {
        content: ' \\2192';
      }
    }
  `,
})
export class LandingPage {
  protected readonly landing = LANDING;
  protected readonly areas = AREAS;
}
