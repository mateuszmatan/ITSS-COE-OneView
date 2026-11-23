import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  ActivatedRouteSnapshot,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { filter, map } from 'rxjs';
import { ContextBar } from '../context/context-bar';
import { APP_NAME } from '../core/app-name';
import { OneViewArea, OneViewSection } from '../core/sections';

export interface ShellUser {
  displayName: string;
  email?: string;
  admin?: boolean;
}

const within = (url: string, path: string): boolean =>
  url === path || ['/', '?', '#'].some((next) => url.startsWith(path + next));

const pathOf = (url: string): string => url.split(/[?#]/)[0] || '/';

/** The route data that shows the business service and application bar above a page. */
export const WITH_CONTEXT = { context: true } as const;

function deepest(route: ActivatedRouteSnapshot): ActivatedRouteSnapshot {
  return route.firstChild ? deepest(route.firstChild) : route;
}

@Component({
  selector: 'ov-app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ContextBar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell {
  readonly home = input.required<OneViewSection>();
  readonly areas = input.required<readonly OneViewArea[]>();
  /** The pages of every area, such as the action history and the administration, below the areas. */
  readonly tools = input<readonly OneViewSection[]>([]);
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

  private readonly withContext = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => deepest(this.router.routerState.snapshot.root).data['context'] === true),
    ),
    { initialValue: false },
  );

  protected readonly atHome = computed(() => pathOf(this.url()) === this.home().path);
  protected readonly area = computed(() =>
    this.areas().find((area) => area.sections.some((section) => within(this.url(), section.path))),
  );
  protected readonly tool = computed(() =>
    this.tools().find((tool) => within(this.url(), tool.path)),
  );
  protected readonly contextShown = computed(() => this.withContext());

  protected readonly sections = computed(() => {
    const sections = this.area()?.sections ?? [];
    return sections.length > 1 ? sections : [];
  });
}
