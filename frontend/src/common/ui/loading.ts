import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ov-loading',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'progress ov-loading',
    role: 'progressbar',
    'aria-label': 'Loading',
  },
  template: `<div class="progress-bar progress-bar-striped progress-bar-animated w-100"></div>`,
})
export class OvLoading {}

@Component({
  selector: 'ov-spinner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'spinner-border spinner-border-sm ov-spinner',
    role: 'status',
    'aria-label': 'Loading',
  },
  template: '',
})
export class OvSpinner {}
