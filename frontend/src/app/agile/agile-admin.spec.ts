import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import {
  CONTEXT_APPLICATION_KEY,
  CONTEXT_DEPARTMENT_KEY,
  OneViewContext,
} from '@common/context/oneview-context';
import { ConfirmDialog } from '@common/shared/confirm-dialog';
import {
  buttonOf,
  gridCell,
  gridColumn,
  gridHeaders,
  gridRows,
  text,
  toast,
} from '@common/testing/dom';
import { AgileApplication } from '../core/models';
import { agileApplication, agileAssessment, agileModel, notInAgile } from '../testing/fixtures';
import { AgileAdmin } from './agile-admin';
import { AgileAssessmentDialog } from './agile-assessment-dialog';
import { AgileProfileDialog } from './agile-profile-dialog';

describe('AgileAdmin', () => {
  let fixture: ComponentFixture<AgileAdmin>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AgileAdmin],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AgileAdmin);
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem(CONTEXT_DEPARTMENT_KEY);
    localStorage.removeItem(CONTEXT_APPLICATION_KEY);
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const teams = () => page().querySelector<HTMLElement>('.teams')!;
  const assessments = () => page().querySelector<HTMLElement>('.assessments');
  const row = (name: string) =>
    gridRows(teams()).find((tr) => text(tr.querySelector('.name')) === name)!;
  const all = () => [agileApplication(), notInAgile()];

  async function load(applications: AgileApplication[] = all()) {
    fixture.detectChanges();
    http.expectOne('/api/agile/model').flush(agileModel());
    http.expectOne('/api/agile/applications').flush(applications);
    await fixture.whenStable();
  }

  const dialogClosing = (...results: unknown[]) => {
    const open = vi.spyOn(TestBed.inject(Dialog), 'open');
    results.forEach((result) =>
      open.mockReturnValueOnce({ closed: of(result) } as unknown as DialogRef<unknown>),
    );
    return open;
  };

  it('lists every team with how it takes part, and every assessment the latest first', async () => {
    await load();

    expect(text(teams().querySelector('.summary'))).toBe('BBH · 1 of 2 teams take part');
    expect(gridHeaders(teams())).toEqual([
      'Application',
      'Business service',
      'Agile',
      'Team',
      'Way of working',
      'Agile coach',
      'Last assessed',
      '',
    ]);
    expect(gridColumn(teams(), 'enabled')).toEqual(['Takes part', 'Not set up']);
    const actions = (name: string) =>
      [...gridCell(row(name), 'actions').querySelectorAll('button')].map(text);
    expect(actions('CertScanner')).toEqual(['Edit', 'Record assessment']);
    expect(actions('PayHub')).toEqual(['Set up', 'Record assessment']);
    const assess = buttonOf(row('PayHub'), 'Record an assessment of PayHub');
    expect(assess.disabled).toBe(true);
    expect(text(page().querySelector(`#${assess.getAttribute('aria-describedby')}`))).toBe(
      'Set up the team of PayHub first: only a team that takes part is assessed.',
    );

    expect(text(assessments()!.querySelector('.summary'))).toBe('2 recorded, the latest first');
    expect(gridColumn(assessments()!, 'assessedOn')).toEqual(['2 Nov 2026', '15 Aug 2026']);
    expect(gridColumn(assessments()!, 'overall')).toEqual(['3.2', '2.4']);
    expect(gridColumn(assessments()!, 'notes')).toEqual(['Retrospectives every sprint.', '']);
  });

  it('lists only the teams of the chosen business service', async () => {
    TestBed.inject(OneViewContext).choose(5);
    await load();

    expect(text(teams().querySelector('.summary'))).toBe('Fund Services · 0 of 1 team takes part');
    expect(gridHeaders(teams())).not.toContain('Business service');
    expect(gridColumn(teams(), 'name')).toEqual(['PayHub PAY']);
    expect(text(assessments()!.querySelector('.empty-state h3'))).toBe('No assessments yet');
  });

  it('sets up a team in a dialog and shows what was saved', async () => {
    await load();
    const saved = notInAgile({ enabled: true, teamName: 'Pay Team', version: 0 });
    const open = dialogClosing(saved);

    buttonOf(row('PayHub'), 'Set up the team of PayHub').click();
    await fixture.whenStable();

    expect(open.mock.calls[0][0]).toBe(AgileProfileDialog);
    expect(open.mock.calls[0][1]?.data).toEqual({ application: notInAgile(), model: agileModel() });
    expect(text(toast())).toContain(
      'The team of PayHub saved. It takes part in Agile Assessment & Adoption.',
    );
    expect(text(gridCell(row('PayHub'), 'team'))).toBe('Pay Team');
    expect(buttonOf(row('PayHub'), 'Record an assessment of PayHub').disabled).toBe(false);
  });

  it('records an assessment in a dialog and lists the teams again', async () => {
    await load();
    const recorded = agileAssessment({ id: 12, assessedOn: '2026-11-20', overallLevel: 3.6 });
    const open = dialogClosing(recorded, undefined);

    buttonOf(row('CertScanner'), 'Record an assessment of CertScanner').click();
    fixture.detectChanges();

    expect(open.mock.calls[0][0]).toBe(AgileAssessmentDialog);
    expect(open.mock.calls[0][1]?.data).toEqual({
      application: agileApplication(),
      model: agileModel(),
    });
    expect(text(toast())).toContain(
      'Assessment of CertScanner of 20 Nov 2026 recorded: level 3.6.',
    );
    http
      .expectOne('/api/agile/applications')
      .flush([
        agileApplication({ assessments: [recorded, ...agileApplication().assessments] }),
        notInAgile(),
      ]);
    await fixture.whenStable();

    expect(gridColumn(assessments()!, 'assessedOn')).toEqual([
      '20 Nov 2026',
      '2 Nov 2026',
      '15 Aug 2026',
    ]);

    buttonOf(row('CertScanner'), 'Record an assessment of CertScanner').click();
    await fixture.whenStable();
    http.expectNone('/api/agile/applications');
  });

  it('deletes an assessment once confirmed and says why one could not be deleted', async () => {
    await load();
    const open = dialogClosing(true, false, true);
    const deleteButton = () =>
      buttonOf(assessments()!, 'Delete the assessment of CertScanner of 15 Aug 2026');

    deleteButton().click();
    expect(open.mock.calls[0][0]).toBe(ConfirmDialog);
    expect(open.mock.calls[0][1]?.data).toMatchObject({
      title: 'Delete the assessment of CertScanner of 15 Aug 2026?',
      message:
        'The levels and notes Joe Coach recorded on 15 Aug 2026 are deleted. The other assessments of the team stay as they are. This cannot be undone.',
      confirmLabel: 'Delete assessment',
      danger: true,
    });
    http.expectOne({ method: 'DELETE', url: '/api/agile/assessments/7' }).flush(null);
    fixture.detectChanges();
    http
      .expectOne('/api/agile/applications')
      .flush([
        agileApplication({ assessments: [agileApplication().assessments[0]] }),
        notInAgile(),
      ]);
    await fixture.whenStable();

    expect(text(toast())).toContain('Assessment of CertScanner of 15 Aug 2026 deleted');
    expect(gridRows(assessments()!)).toHaveLength(1);

    buttonOf(assessments()!, 'Delete the assessment of CertScanner of 2 Nov 2026').click();
    http.expectNone('/api/agile/assessments/11');

    buttonOf(assessments()!, 'Delete the assessment of CertScanner of 2 Nov 2026').click();
    http
      .expectOne('/api/agile/assessments/11')
      .flush({ detail: 'Assessment 11 was not found' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(text(toast())).toContain('Assessment 11 was not found');
  });

  it('says why the teams could not be read and reads them again', async () => {
    fixture.detectChanges();
    http.expectOne('/api/agile/model').flush(agileModel());
    http
      .expectOne('/api/agile/applications')
      .flush(null, { status: 0, statusText: 'Unknown Error' });
    await fixture.whenStable();

    expect(text(page().querySelector('ov-load-error .banner-text'))).toBe(
      'The application teams could not be loaded. The portal cannot be reached. Check your network connection and try again.',
    );
    expect(assessments()).toBeNull();

    buttonOf(page(), 'Try again').click();
    fixture.detectChanges();
    http.expectOne('/api/agile/model').flush(agileModel());
    http.expectOne('/api/agile/applications').flush(all());
    await fixture.whenStable();

    expect(gridRows(teams())).toHaveLength(2);
  });
});
