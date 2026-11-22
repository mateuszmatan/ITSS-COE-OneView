import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AGILE_MATURITY } from '../core/sections';
import { AreaPlan, PlannedCapability } from '../shared/area-plan';

export const AGILE_CAPABILITIES: readonly PlannedCapability[] = [
  {
    title: 'Maturity assessment',
    description:
      'The agile maturity of every team and business service, assessed per dimension against the BBH model.',
  },
  {
    title: 'Maturity levels',
    description: 'The level reached in every dimension, with what the next level asks for.',
  },
  {
    title: 'Trend',
    description:
      'How the maturity of a team and of a business service changes from one assessment to the next.',
  },
  {
    title: 'Comparison',
    description: 'Business services side by side, and BBH as a whole.',
  },
];

@Component({
  selector: 'ov-agile-overview',
  imports: [AreaPlan],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ov-area-plan [section]="section" [capabilities]="capabilities" />',
})
export class AgileOverviewPage {
  protected readonly section = AGILE_MATURITY;
  protected readonly capabilities = AGILE_CAPABILITIES;
}
