import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AppShell } from '@common/shell/app-shell';
import { CurrentUser } from './core/current-user';
import { AREAS, LANDING, TOOLS } from './core/sections';

@Component({
  selector: 'ov-root',
  imports: [AppShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ov-app-shell [home]="home" [areas]="areas" [tools]="tools" [user]="user()" />',
})
export class App {
  protected readonly home = LANDING;
  protected readonly areas = AREAS;
  protected readonly tools = TOOLS;
  protected readonly user = inject(CurrentUser).profile;
}
