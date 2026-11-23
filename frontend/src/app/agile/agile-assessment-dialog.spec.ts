import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  choose,
  fieldOf,
  inputOf,
  optionsOf,
  selectOf,
  text,
  unexplained,
} from '@common/testing/dom';
import { agileApplication, agileAssessment, agileModel } from '../testing/fixtures';
import {
  AgileAssessmentDialog,
  AgileAssessmentDialogData,
  notAfter,
} from './agile-assessment-dialog';
import { today } from './agile-texts';

describe('AgileAssessmentDialog', () => {
  let fixture: ComponentFixture<AgileAssessmentDialog>;
  let http: HttpTestingController;
  const close = vi.fn();
  const LEVELS = [
    'Team and roles',
    'Planning and delivery',
    'Engineering practices',
    'Product ownership',
    'Continuous improvement',
  ];

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [AgileAssessmentDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: DIALOG_DATA,
          useValue: {
            application: agileApplication(),
            model: agileModel(),
          } satisfies AgileAssessmentDialogData,
        },
        { provide: DialogRef, useValue: { close } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AgileAssessmentDialog);
    await fixture.whenStable();
  });

  afterEach(() => {
    http.verify();
    close.mockReset();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const errorOf = (label: string) =>
    text(fieldOf(page(), label)?.querySelector('.invalid-feedback'));

  async function type(label: string, value: string) {
    const input = inputOf(page(), label);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function rate(levels: number[]) {
    levels.forEach((level, index) => choose(selectOf(page(), LEVELS[index]), String(level)));
    await fixture.whenStable();
  }

  async function submit() {
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  it('records an assessment of every dimension, made today by the coach of the team', async () => {
    expect(text(page().querySelector('h2'))).toBe('Record an assessment of CertScanner');
    expect(text(page().querySelector('.intro'))).toBe(
      'Rate the team of Cert Squad in every dimension, from 1, just starting, to 5, fully mature and improving on its own. The overall level is the mean of the levels.',
    );
    expect(unexplained(page())).toEqual([]);
    expect(inputOf(page(), 'Day of the assessment').type).toBe('date');
    expect(inputOf(page(), 'Day of the assessment').value).toBe(today());
    expect(inputOf(page(), 'Assessed by').value).toBe('Joe Coach');
    expect(optionsOf(selectOf(page(), 'Team and roles'))).toEqual([
      'Choose a level',
      '1',
      '2',
      '3',
      '4',
      '5',
    ]);

    await type('Day of the assessment', '2026-11-20');
    await rate([4, 3, 3, 2, 5]);
    await type('Notes', '  Pairing on every story. ');
    await submit();

    const request = http.expectOne({
      method: 'POST',
      url: '/api/agile/applications/1/assessments',
    });
    expect(request.request.body).toEqual({
      assessedOn: '2026-11-20',
      assessor: 'Joe Coach',
      levels: { TEAM: 4, PLANNING: 3, ENGINEERING: 3, PRODUCT: 2, IMPROVEMENT: 5 },
      notes: 'Pairing on every story.',
    });
    const saved = agileAssessment({ id: 12, assessedOn: '2026-11-20', overallLevel: 3.4 });
    request.flush(saved);

    expect(close).toHaveBeenCalledWith(saved);
  });

  it('asks for every level, the assessor and a day that is not in the future', async () => {
    await type('Assessed by', '  ');
    await type('Day of the assessment', '2999-01-01');
    await rate([4, 3]);
    await submit();

    http.expectNone('/api/agile/applications/1/assessments');
    expect(errorOf('Assessed by')).toBe('Required');
    expect(errorOf('Day of the assessment')).toMatch(/^Not in the future: .+ at the latest$/);
    expect(errorOf('Engineering practices')).toBe('Required');
    expect(errorOf('Team and roles')).toBe('');
    expect(notAfter('2026-11-23')({ value: '2026-11-24' } as never)).toEqual({
      rule: 'Not in the future: 23 Nov 2026 at the latest',
    });
    expect(notAfter('2026-11-23')({ value: '2026-11-23' } as never)).toBeNull();
  });

  it('marks the levels the portal did not accept', async () => {
    await rate([4, 3, 3, 2, 5]);
    await submit();

    http.expectOne('/api/agile/applications/1/assessments').flush(
      {
        errors: [
          { field: 'levels.PRODUCT', message: 'must be between 1 and 5' },
          { field: 'assessedOn', message: 'must not be in the future' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(errorOf('Product ownership')).toBe('must be between 1 and 5');
    expect(errorOf('Day of the assessment')).toBe('must not be in the future');
    expect(text(page().querySelector('[role=alert]'))).toBe(
      'The portal did not accept some values. They are marked in the form.',
    );
    expect(close).not.toHaveBeenCalled();
  });

  it('says why the portal refused the assessment of a team taken out meanwhile', async () => {
    await rate([4, 3, 3, 2, 5]);
    await submit();

    http.expectOne('/api/agile/applications/1/assessments').flush(
      {
        detail:
          'Edit the team of CertScanner and tick Takes part first: only a team that takes part is assessed.',
      },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(text(page().querySelector('[role=alert]'))).toBe(
      'Edit the team of CertScanner and tick Takes part first: only a team that takes part is assessed.',
    );
    expect(close).not.toHaveBeenCalled();
  });
});
