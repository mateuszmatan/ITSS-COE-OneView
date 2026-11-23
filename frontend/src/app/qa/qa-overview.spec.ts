import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  CONTEXT_APPLICATION_KEY,
  CONTEXT_DEPARTMENT_KEY,
  OneViewContext,
} from '@common/context/oneview-context';
import { buttonOf, gridCell, gridColumn, gridHeaders, gridRows, text } from '@common/testing/dom';
import { QualityApplication, QualityDefaults } from '../core/models';
import { QA_METRICS } from '../core/sections';
import { notInQuality, qualityApplication, qualityDefaults } from '../testing/fixtures';
import { QaOverviewPage } from './qa-overview';

describe('QaOverviewPage', () => {
  let fixture: ComponentFixture<QaOverviewPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [QaOverviewPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(QaOverviewPage);
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem(CONTEXT_DEPARTMENT_KEY);
    localStorage.removeItem(CONTEXT_APPLICATION_KEY);
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const tiles = () => [...page().querySelectorAll('.summary .stat')].map(text);
  const custody = qualityApplication({
    productId: 3,
    code: 'LEDG',
    name: 'Ledger',
    qaLead: 'Bob Day',
    dashboardUrl: null,
    targets: { coveragePercent: null, passRatePercent: null, maxCriticalDefects: null },
  });
  const all = () => [qualityApplication(), custody, notInQuality()];

  async function load(
    applications: QualityApplication[] = all(),
    defaults: QualityDefaults = qualityDefaults(),
  ) {
    fixture.detectChanges();
    http.expectOne('/api/qa/defaults').flush(defaults);
    http.expectOne('/api/qa/applications').flush(applications);
    await fixture.whenStable();
  }

  it('sums up every application of BBH against the BBH targets', async () => {
    await load();

    expect(text(page().querySelector('h1'))).toBe(QA_METRICS.heading);
    expect(text(page().querySelector('.page-description'))).toBe(QA_METRICS.description);
    expect(page().querySelector('.page-header a')?.getAttribute('href')).toBe('/admin/qa');
    expect(text(page().querySelector('.summary h2'))).toBe('Applications');
    expect(text(page().querySelector('.summary .card-header .muted'))).toBe('BBH');
    expect(tiles()).toEqual([
      '3 Applications',
      '2 Take part',
      '1 Do not take part yet',
      '1 With a Grafana dashboard',
    ]);
    expect(text(page().querySelector('.bbh-targets'))).toBe(
      'BBH targets: Code coverage at least 80%, test pass rate at least 95%, open critical defects at most 0. An application can set its own in OneView Admin.',
    );
    expect(gridHeaders(page())).toEqual([
      'Application',
      'Business service',
      'QM Metric Tool',
      'QA lead',
      'Targets',
      'Grafana',
    ]);
    expect(gridColumn(page(), 'name')).toEqual(['CertScanner CERT', 'Ledger LEDG', 'PayHub PAY']);
    expect(gridColumn(page(), 'enabled')).toEqual(['Takes part', 'Takes part', 'Not set up']);
    expect(gridColumn(page(), 'targets')).toEqual([
      'Code coverage at least 85%',
      'The BBH targets',
      'The BBH targets',
    ]);
    const dashboard = gridCell(gridRows(page())[0], 'dashboard').querySelector('a')!;
    expect(dashboard.getAttribute('href')).toBe('https://grafana.bbh.com/d/cert-quality');
    expect(dashboard.getAttribute('target')).toBe('_blank');
    expect([...page().querySelectorAll('ov-area-plan h3')].map(text)).toEqual([
      'Test results',
      'Code quality',
      'Defects',
      'Quality trends',
    ]);
  });

  it('sums up only the applications of the chosen business service', async () => {
    TestBed.inject(OneViewContext).choose(3);
    await load();

    expect(text(page().querySelector('.summary .card-header .muted'))).toBe('Corporate Technology');
    expect(tiles()).toEqual([
      '2 Applications',
      '2 Take part',
      '0 Do not take part yet',
      '1 With a Grafana dashboard',
    ]);
    expect(gridHeaders(page())).not.toContain('Business service');
    expect(gridColumn(page(), 'name')).toEqual(['CertScanner CERT', 'Ledger LEDG']);
  });

  it('shows one application alone once it is opened, with its own and the BBH targets', async () => {
    await load();

    buttonOf(page(), 'Show CertScanner alone').click();
    await fixture.whenStable();

    expect(TestBed.inject(OneViewContext).scope()).toEqual({ departmentId: 3, productId: 1 });
    expect(page().querySelector('.summary')).toBeNull();
    expect(text(page().querySelector('.profile h2'))).toBe('CertScanner CERT');
    expect(text(page().querySelector('.profile .chip'))).toBe('Takes part');
    expect(
      [...page().querySelectorAll('.profile dt')].map(
        (term) => `${text(term)}: ${text(term.nextElementSibling)}`,
      ),
    ).toEqual([
      'Business service: Corporate Technology',
      'QA lead: Ann Lee',
      'SonarQube project: com.bbh:cert-scanner',
      'Defect project: CERT',
      'Grafana dashboard: Open the quality dashboard',
      expect.stringMatching(/^Last changed: /),
    ]);
    expect([...page().querySelectorAll('.targets .stat')].map(text)).toEqual([
      'at least 85% Code coverage · own target',
      'at least 95% Test pass rate · BBH target',
      'at most 0 Open critical defects · BBH target',
    ]);
    expect(page().querySelector('.not-taking-part')).toBeNull();
  });

  it('says how an application that does not take part yet is set up', async () => {
    TestBed.inject(OneViewContext).choose(5, 2);
    await load();

    expect(text(page().querySelector('.profile .chip'))).toBe('Not set up');
    expect(text(page().querySelector('.not-taking-part .banner-text'))).toBe(
      'PayHub does not take part in the QM Metric Tool yet. An administrator sets it up in OneView Admin with its QA lead, where its results are and its own quality targets.',
    );
    expect(page().querySelector('.not-taking-part a')?.getAttribute('href')).toBe('/admin/qa');
    expect(text(page().querySelector('.profile dl'))).toContain('Grafana dashboard Not linked yet');
  });

  it('says when the chosen business service has no applications yet', async () => {
    TestBed.inject(OneViewContext).choose(9);
    await load();

    expect(text(page().querySelector('.empty-state h3'))).toBe(
      'No applications in the chosen business service yet',
    );
    expect(page().querySelector('.empty-state a')?.getAttribute('href')).toBe(
      '/admin/products/new',
    );
  });

  it('says why the QM Metric Tool could not be read and reads it again', async () => {
    fixture.detectChanges();
    http.expectOne('/api/qa/defaults').flush(qualityDefaults());
    http.expectOne('/api/qa/applications').flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(text(page().querySelector('ov-load-error'))).toContain(
      'The QM Metric Tool could not be loaded.',
    );

    buttonOf(page(), 'Try again').click();
    fixture.detectChanges();
    http.expectOne('/api/qa/defaults').flush(qualityDefaults());
    http.expectOne('/api/qa/applications').flush(all());
    await fixture.whenStable();

    expect(page().querySelector('ov-load-error')).toBeNull();
    expect(gridRows(page())).toHaveLength(3);
  });
});
