import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AppShell } from '@common/shell/app-shell';
import { CurrentUser } from './core/current-user';
import { AREAS } from './core/sections';

@Component({
  selector: 'ov-root',
  imports: [AppShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ov-app-shell [areas]="areas" [user]="user()" />',
})
export class App {
  protected readonly areas = AREAS;
  protected readonly user = inject(CurrentUser).profile;
}
