import { ComponentFixture, TestBed } from '@angular/core/testing';
import { text } from '@common/testing/dom';
import { AGILE_CAPABILITIES } from '../agile/agile-overview';
import { QA_CAPABILITIES } from '../qa/qa-overview';
import { AreaPlan } from './area-plan';

describe('AreaPlan', () => {
  let fixture: ComponentFixture<AreaPlan>;

  beforeEach(() => {
    fixture = TestBed.createComponent(AreaPlan);
    fixture.componentRef.setInput('capabilities', QA_CAPABILITIES);
  });

  const page = () => fixture.nativeElement as HTMLElement;

  it('lists what an area is to show next, every item marked as planned', async () => {
    await fixture.whenStable();

    expect(text(page().querySelector('h2'))).toBe('Coming next');
    expect(text(page().querySelector('.section-help'))).toBe(
      'This area is being built. It will also show the following.',
    );
    const titles = [...page().querySelectorAll('.capability h3')].map(text);
    expect(titles).toEqual(['Test results', 'Code quality', 'Defects', 'Quality trends']);
    expect([...page().querySelectorAll('.capability .planned')].map(text)).toEqual(
      titles.map(() => 'Planned'),
    );
  });

  it('says what the plan depends on', async () => {
    fixture.componentRef.setInput('capabilities', AGILE_CAPABILITIES);
    fixture.componentRef.setInput('intro', 'Next, this page will also show:');
    await fixture.whenStable();

    expect(text(page().querySelector('.section-help'))).toBe('Next, this page will also show:');
    expect([...page().querySelectorAll('.capability h3')].map(text)).toEqual([
      'Trend chart',
      'What the next level asks for',
    ]);
  });
});
