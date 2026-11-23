import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { checkboxOf, fieldOf, inputOf, text, unexplained } from '@common/testing/dom';
import { QualityApplication } from '../core/models';
import { notInQuality, qualityApplication } from '../testing/fixtures';
import {
  QualityApplicationDialog,
  QualityApplicationDialogData,
} from './quality-application-dialog';

describe('QualityApplicationDialog', () => {
  let fixture: ComponentFixture<QualityApplicationDialog>;
  let http: HttpTestingController;
  const close = vi.fn();
  const reloaded = vi.fn();
  const bbhTargets = { coveragePercent: 80, passRatePercent: 95, maxCriticalDefects: 0 };

  async function open(application: QualityApplication) {
    TestBed.configureTestingModule({
      imports: [QualityApplicationDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: DIALOG_DATA,
          useValue: { application, bbhTargets, reloaded } satisfies QualityApplicationDialogData,
        },
        { provide: DialogRef, useValue: { close } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(QualityApplicationDialog);
    await fixture.whenStable();
  }

  afterEach(() => {
    http.verify();
    close.mockReset();
    reloaded.mockReset();
  });

  const page = () => fixture.nativeElement as HTMLElement;

  async function type(label: string, value: string) {
    const input = inputOf(page(), label);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function submit() {
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  it('sets up an application that never took part, with the BBH targets as hints', async () => {
    await open(notInQuality());

    expect(text(page().querySelector('h2'))).toBe('Set up PayHub in the QM Metric Tool');
    expect(text(page().querySelector('.intro'))).toBe(
      'PayHub (PAY) of Fund Services. Fields left empty are not set.',
    );
    expect(unexplained(page())).toEqual([]);
    expect(checkboxOf(page(), 'Takes part in the QM Metric Tool').checked).toBe(true);
    expect(text(fieldOf(page(), 'Code coverage (%)')?.querySelector('ov-hint'))).toBe(
      'BBH target: at least 80%',
    );
    expect(text(fieldOf(page(), 'Open critical defects (at most)')?.querySelector('ov-hint'))).toBe(
      'BBH target: at most 0',
    );
    expect(text(page().querySelector('button[type=submit]'))).toBe('Set up');

    await type('QA lead', '  Eve Ray ');
    await type('Grafana dashboard', 'https://grafana.bbh.com/d/pay');
    await type('Test pass rate (%)', '90');
    await submit();

    const request = http.expectOne({ method: 'PUT', url: '/api/qa/applications/2' });
    expect(request.request.body).toEqual({
      enabled: true,
      qaLead: 'Eve Ray',
      sonarProjectKey: null,
      defectProjectKey: null,
      dashboardUrl: 'https://grafana.bbh.com/d/pay',
      targets: { coveragePercent: null, passRatePercent: 90, maxCriticalDefects: null },
      version: null,
    });
    const saved = notInQuality({ enabled: true, qaLead: 'Eve Ray', version: 0 });
    request.flush(saved);

    expect(close).toHaveBeenCalledWith(saved);
  });

  it('changes an application with the version it was read at and clears a target', async () => {
    await open(qualityApplication());

    expect(text(page().querySelector('h2'))).toBe('CertScanner in the QM Metric Tool');
    expect(inputOf(page(), 'Code coverage (%)').value).toBe('85');
    expect(inputOf(page(), 'SonarQube project key').value).toBe('com.bbh:cert-scanner');

    checkboxOf(page(), 'Takes part in the QM Metric Tool').click();
    await type('Code coverage (%)', '');
    await submit();

    const request = http.expectOne({ method: 'PUT', url: '/api/qa/applications/1' });
    expect(request.request.body).toMatchObject({
      enabled: false,
      targets: { coveragePercent: null, passRatePercent: null, maxCriticalDefects: null },
      version: 2,
    });
    request.flush(qualityApplication({ enabled: false, version: 3 }));

    expect(close).toHaveBeenCalled();
  });

  it('checks the values before sending them', async () => {
    await open(qualityApplication());

    await type('Grafana dashboard', 'grafana dashboard');
    await type('Test pass rate (%)', '101');
    await submit();

    http.expectNone('/api/qa/applications/1');
    expect(text(fieldOf(page(), 'Grafana dashboard')?.querySelector('.invalid-feedback'))).toBe(
      'An http or https URL without spaces, double quotes, backslashes, $ or backticks',
    );
    expect(text(fieldOf(page(), 'Test pass rate (%)')?.querySelector('.invalid-feedback'))).toBe(
      'At most 100',
    );
  });

  it('marks the targets the portal did not accept and says when someone else changed it', async () => {
    await open(qualityApplication());

    await submit();
    http.expectOne('/api/qa/applications/1').flush(
      {
        errors: [
          { field: 'targets.maxCriticalDefects', message: 'must be between 0 and 999999' },
          { field: 'owner', message: 'is unknown' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(
      text(fieldOf(page(), 'Open critical defects (at most)')?.querySelector('.invalid-feedback')),
    ).toBe('must be between 0 and 999999');
    expect(text(page().querySelector('[role=alert]'))).toBe(
      'The portal did not accept some values. They are marked in the form. owner: is unknown',
    );

    await type('Open critical defects (at most)', '3');
    await submit();
    http
      .expectOne({ method: 'PUT', url: '/api/qa/applications/1' })
      .flush({ detail: 'stale' }, { status: 409, statusText: 'Conflict' });
    http
      .expectOne({ method: 'GET', url: '/api/qa/applications/1' })
      .flush(qualityApplication({ qaLead: 'Bea Lane', version: 3 }));
    await fixture.whenStable();

    expect(text(page().querySelector('[role=alert]'))).toBe(
      'Not saved: someone else changed CertScanner after you opened it. The form now shows their changes: make your change again, then save.',
    );
    expect(inputOf(page(), 'QA lead').value).toBe('Bea Lane');
    expect(inputOf(page(), 'Open critical defects (at most)').value).toBe('');
    expect(reloaded).toHaveBeenCalledWith(qualityApplication({ qaLead: 'Bea Lane', version: 3 }));
    expect(close).not.toHaveBeenCalled();

    await type('Open critical defects (at most)', '3');
    await submit();
    const again = http.expectOne({ method: 'PUT', url: '/api/qa/applications/1' });
    expect(again.request.body).toMatchObject({
      qaLead: 'Bea Lane',
      targets: { maxCriticalDefects: 3 },
      version: 3,
    });
    again.flush(qualityApplication({ qaLead: 'Bea Lane', version: 4 }));

    expect(close).toHaveBeenCalled();
  });

  it('says when the changes of someone else could not be loaded either', async () => {
    await open(qualityApplication());

    await submit();
    http
      .expectOne({ method: 'PUT', url: '/api/qa/applications/1' })
      .flush({ detail: 'stale' }, { status: 409, statusText: 'Conflict' });
    http
      .expectOne({ method: 'GET', url: '/api/qa/applications/1' })
      .flush({ detail: 'down' }, { status: 503, statusText: 'Service Unavailable' });
    await fixture.whenStable();

    expect(text(page().querySelector('[role=alert]'))).toMatch(
      /^Not saved: someone else changed CertScanner after you opened it\. Their changes could not be loaded: /,
    );
    expect(reloaded).not.toHaveBeenCalled();
    expect(close).not.toHaveBeenCalled();
  });
});
