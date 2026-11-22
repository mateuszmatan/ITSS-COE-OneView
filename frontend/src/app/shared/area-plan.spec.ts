import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { text } from '@common/testing/dom';
import { AgileOverviewPage } from '../agile/agile-overview';
import { AGILE_MATURITY, QA_METRICS } from '../core/sections';
import { QaOverviewPage } from '../qa/qa-overview';

describe.each([
  [
    'QA',
    QaOverviewPage,
    QA_METRICS,
    ['Test results', 'Code quality', 'Defects', 'Grafana dashboards'],
  ],
  [
    'Agile',
    AgileOverviewPage,
    AGILE_MATURITY,
    ['Maturity assessment', 'Maturity levels', 'Trend', 'Comparison'],
  ],
] as const)('the page of the %s area', (_, component, section, titles) => {
  let fixture: ComponentFixture<unknown>;

  beforeEach(async () => {
    fixture = TestBed.createComponent(component as Type<unknown>);
    await fixture.whenStable();
  });

  const page = () => fixture.nativeElement as HTMLElement;

  it('names the area and says that it is being built', () => {
    expect(text(page().querySelector('h1'))).toBe(section.heading);
    expect(text(page().querySelector('.page-description'))).toBe(section.description);
    expect(text(page().querySelector('[role=status]'))).toContain('This area is being built');
  });

  it('lists what is planned, every item marked as planned', () => {
    expect([...page().querySelectorAll('.capability h2')].map(text)).toEqual(titles);
    expect([...page().querySelectorAll('.capability .planned')].map(text)).toEqual(
      titles.map(() => 'Planned'),
    );
  });
});
