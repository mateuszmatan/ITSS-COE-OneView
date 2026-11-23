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
import {
  buttonOf,
  fieldOf,
  gridCell,
  gridColumn,
  gridHeaders,
  gridRows,
  inputOf,
  text,
  toast,
  unexplained,
} from '@common/testing/dom';
import { QualityApplication } from '../core/models';
import { notInQuality, qualityApplication, qualityDefaults } from '../testing/fixtures';
import { QaAdmin } from './qa-admin';
import {
  QualityApplicationDialog,
  QualityApplicationDialogData,
} from './quality-application-dialog';

describe('QaAdmin', () => {
  let fixture: ComponentFixture<QaAdmin>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [QaAdmin],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(QaAdmin);
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem(CONTEXT_DEPARTMENT_KEY);
    localStorage.removeItem(CONTEXT_APPLICATION_KEY);
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const bbh = () => page().querySelector<HTMLElement>('.bbh')!;
  const row = (name: string) =>
    gridRows(page()).find((tr) => text(tr.querySelector('.name')) === name)!;
  const all = () => [qualityApplication(), notInQuality()];

  async function load(applications: QualityApplication[] = all(), defaults = qualityDefaults()) {
    fixture.detectChanges();
    http.expectOne('/api/qa/defaults').flush(defaults);
    http.expectOne('/api/qa/applications').flush(applications);
    await fixture.whenStable();
  }

  async function type(label: string, value: string) {
    const input = inputOf(bbh(), label);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  const dialogClosing = (...results: unknown[]) => {
    const open = vi.spyOn(TestBed.inject(Dialog), 'open');
    results.forEach((result) =>
      open.mockReturnValueOnce({ closed: of(result) } as unknown as DialogRef<unknown>),
    );
    return open;
  };

  it('shows the BBH targets, explained, and every application with how it takes part', async () => {
    await load();

    expect(text(bbh().querySelector('.summary'))).toBe(
      'Not changed yet: the targets OneView starts with',
    );
    expect(
      ['Code coverage (%)', 'Test pass rate (%)', 'Open critical defects (at most)'].map(
        (label) => inputOf(bbh(), label).value,
      ),
    ).toEqual(['80', '95', '0']);
    expect(unexplained(bbh())).toEqual([]);
    expect(buttonOf(bbh(), 'Save BBH targets').disabled).toBe(true);
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);

    expect(text(page().querySelector('.applications .summary'))).toBe(
      'BBH · 1 of 2 applications take part',
    );
    expect(gridHeaders(page())).toEqual([
      'Application',
      'Business service',
      'QM Metric Tool',
      'QA lead',
      'Targets',
      'Last changed',
      '',
    ]);
    expect(gridColumn(page(), 'enabled')).toEqual(['Takes part', 'Not set up']);
    expect(text(gridCell(row('CertScanner'), 'actions'))).toBe('Edit');
    expect(text(gridCell(row('PayHub'), 'actions'))).toBe('Set up');
  });

  it('saves the BBH targets with the version it read and lists the applications again', async () => {
    await load(all(), qualityDefaults({ version: 2, updatedAt: '2026-11-01T09:00:00Z' }));
    expect(text(bbh().querySelector('.summary'))).toMatch(/^Last saved /);

    await type('Code coverage (%)', '75');
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(true);
    buttonOf(bbh(), 'Save BBH targets').click();

    const request = http.expectOne({ method: 'PUT', url: '/api/qa/defaults' });
    expect(request.request.body).toEqual({
      targets: { coveragePercent: 75, passRatePercent: 95, maxCriticalDefects: 0 },
      version: 2,
    });
    request.flush(
      qualityDefaults({
        targets: { coveragePercent: 75, passRatePercent: 95, maxCriticalDefects: 0 },
        version: 3,
        updatedAt: new Date().toISOString(),
      }),
    );
    await fixture.whenStable();

    expect(text(toast())).toContain(
      'BBH quality targets saved. Every application without targets of its own works to them now.',
    );
    expect(text(bbh().querySelector('.summary'))).toBe('Last saved just now');
    expect(inputOf(bbh(), 'Code coverage (%)').value).toBe('75');
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);
  });

  it('asks for every BBH target before saving', async () => {
    await load();

    await type('Test pass rate (%)', '');
    buttonOf(bbh(), 'Save BBH targets').click();
    await fixture.whenStable();

    http.expectNone('/api/qa/defaults');
    expect(text(fieldOf(bbh(), 'Test pass rate (%)')?.querySelector('.invalid-feedback'))).toBe(
      'Required',
    );
  });

  it('marks what the portal did not accept and offers to reload when someone else saved first', async () => {
    await load();

    await type('Code coverage (%)', '70');
    buttonOf(bbh(), 'Save BBH targets').click();
    http
      .expectOne({ method: 'PUT', url: '/api/qa/defaults' })
      .flush(
        { errors: [{ field: 'coveragePercent', message: 'must be at least 75 at BBH' }] },
        { status: 400, statusText: 'Bad Request' },
      );
    await fixture.whenStable();

    expect(text(fieldOf(bbh(), 'Code coverage (%)')?.querySelector('.invalid-feedback'))).toBe(
      'must be at least 75 at BBH',
    );
    expect(text(bbh().querySelector('.save-problem'))).toBe(
      'The portal did not accept some values. They are marked in the form.',
    );

    await type('Code coverage (%)', '76');
    buttonOf(bbh(), 'Save BBH targets').click();
    http
      .expectOne({ method: 'PUT', url: '/api/qa/defaults' })
      .flush({ detail: 'stale' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(text(bbh().querySelector('.conflict .banner-text'))).toContain(
      'Someone else saved the BBH targets after you opened this page.',
    );
    expect(bbh().querySelector('.save-problem')).toBeNull();

    buttonOf(bbh(), 'Reload').click();
    fixture.detectChanges();
    http.expectOne('/api/qa/defaults').flush(qualityDefaults({ version: 4 }));
    http.expectOne('/api/qa/applications').flush(all());
    await fixture.whenStable();

    expect(bbh().querySelector('.conflict')).toBeNull();
    expect(inputOf(bbh(), 'Code coverage (%)').value).toBe('80');
  });

  it('lists only the applications of the chosen business service', async () => {
    TestBed.inject(OneViewContext).choose(5);
    await load();

    expect(text(page().querySelector('.applications .summary'))).toBe(
      'Fund Services · 0 of 1 application takes part',
    );
    expect(gridHeaders(page())).not.toContain('Business service');
    expect(gridColumn(page(), 'name')).toEqual(['PayHub PAY']);
  });

  it('sets up an application in a dialog and shows what was saved', async () => {
    await load();
    const saved = notInQuality({ enabled: true, qaLead: 'Eve Ray', version: 0 });
    const open = dialogClosing(saved, undefined);

    buttonOf(row('PayHub'), 'Set up PayHub').click();
    await fixture.whenStable();

    expect(open.mock.calls[0][0]).toBe(QualityApplicationDialog);
    expect(open.mock.calls[0][1]?.data).toEqual({
      application: notInQuality(),
      bbhTargets: { coveragePercent: 80, passRatePercent: 95, maxCriticalDefects: 0 },
      reloaded: expect.any(Function),
    });
    expect(text(toast())).toContain('PayHub saved. It takes part in the QM Metric Tool.');
    expect(text(gridCell(row('PayHub'), 'qaLead'))).toBe('Eve Ray');
    expect(text(gridCell(row('PayHub'), 'actions'))).toBe('Edit');

    buttonOf(row('CertScanner'), 'Edit CertScanner').click();
    await fixture.whenStable();
    http.expectNone('/api/qa/applications');
  });

  it('shows what someone else saved once the dialog loaded it, also when it closes unsaved', async () => {
    await load();
    const open = dialogClosing(undefined);

    buttonOf(row('PayHub'), 'Set up PayHub').click();
    await fixture.whenStable();
    const data = open.mock.calls[0][1]?.data as QualityApplicationDialogData;
    data.reloaded?.(notInQuality({ enabled: true, qaLead: 'Bea Lane', version: 2 }));
    await fixture.whenStable();

    expect(text(gridCell(row('PayHub'), 'qaLead'))).toBe('Bea Lane');
    expect(text(gridCell(row('PayHub'), 'actions'))).toBe('Edit');
    expect(toast()).toBeNull();
    http.expectNone('/api/qa/applications');
  });

  it('says why the targets or the applications could not be read', async () => {
    fixture.detectChanges();
    http.expectOne('/api/qa/defaults').flush(null, { status: 503, statusText: 'Unavailable' });
    http.expectOne('/api/qa/applications').flush(null, { status: 0, statusText: 'Unknown Error' });
    await fixture.whenStable();

    expect([...page().querySelectorAll('ov-load-error .banner-text')].map(text)).toEqual([
      'The BBH quality targets could not be loaded. The portal could not complete the request (error 503 Unavailable). Try again in a moment; if it keeps failing, tell the portal administrator.',
      'The applications could not be loaded. The portal cannot be reached. Check your network connection and try again.',
    ]);
  });
});
