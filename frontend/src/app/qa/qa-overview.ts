import { ChangeDetectionStrategy, Component } from '@angular/core';
import { QA_METRICS } from '../core/sections';
import { AreaPlan, PlannedCapability } from '../shared/area-plan';

export const QA_CAPABILITIES: readonly PlannedCapability[] = [
  {
    title: 'Test results',
    description:
      'Passed, failed and skipped unit, regression, smoke and performance tests of every product, from the runs of its pipelines.',
  },
  {
    title: 'Code quality',
    description:
      'Coverage, the SonarQube quality gate and its open issues per service, with their trend over time.',
  },
  {
    title: 'Defects',
    description: 'Open and escaped defects per product and business service, by severity and age.',
  },
  {
    title: 'Grafana dashboards',
    description:
      'The QA dashboards in Grafana, drawn from the QA metrics in InfluxDB, linked for every product.',
  },
];

@Component({
  selector: 'ov-qa-overview',
  imports: [AreaPlan],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ov-area-plan [section]="section" [capabilities]="capabilities" />',
})
export class QaOverviewPage {
  protected readonly section = QA_METRICS;
  protected readonly capabilities = QA_CAPABILITIES;
}
