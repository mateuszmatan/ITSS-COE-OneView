import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { APP_NAME } from '../core/app-name';
import { OneViewArea, OneViewSection } from '../core/sections';

export interface ShellUser {
  displayName: string;
  email?: string;
  admin?: boolean;
}

const within = (url: string, path: string): boolean =>
  url === path || ['/', '?', '#'].some((next) => url.startsWith(path + next));

@Component({
  selector: 'ov-app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell {
  readonly home = input.required<OneViewSection>();
  readonly areas = input.required<readonly OneViewArea[]>();
  readonly user = input<ShellUser>();

  protected readonly name = inject(APP_NAME);

  private readonly router = inject(Router);
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  protected readonly area = computed(() =>
    this.areas().find((area) => area.sections.some((section) => within(this.url(), section.path))),
  );

  protected readonly sections = computed(() => {
    const sections = this.area()?.sections ?? [];
    return sections.length > 1 ? sections : [];
  });
}
