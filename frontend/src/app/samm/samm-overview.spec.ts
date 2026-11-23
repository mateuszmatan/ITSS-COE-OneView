import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { SammApi } from '../core/api';
import {
  SammApplicationScore,
  SammDepartmentScore,
  SammDomainScore,
  SammOrganizationOverview,
  SammPracticeScore,
} from '../core/models';
import { OneViewContext } from '@common/context/oneview-context';
import { SammOverviewPage } from './samm-overview';

describe('SammOverviewPage', () => {
  let fixture: ComponentFixture<SammOverviewPage>;
  let component: SammOverviewPage;

  const mockDomainScores: SammDomainScore[] = [
    {
      function: 'GOVERNANCE',
      title: 'Governance',
      description: 'Strategy & policy',
      score: 2.5,
      maturityLevel: 3,
      practices: [
        {
          practice: 'STRATEGY_AND_METRICS',
          practiceName: 'STRATEGY_AND_METRICS',
          practiceTitle: 'Strategy & Metrics',
          practiceCode: 'SM',
          businessFunction: 'GOVERNANCE',
          score: 2.5,
          maturityLevel: 3,
          criteriaMet: ['Pipeline stage metrics telemetry is enabled'],
          recommendations: [],
        },
      ],
    },
    {
      function: 'DESIGN',
      title: 'Design',
      description: 'Requirements & architecture',
      score: 2.0,
      maturityLevel: 2,
      practices: [],
    },
    {
      function: 'IMPLEMENTATION',
      title: 'Implementation',
      description: 'Build & deployment',
      score: 2.0,
      maturityLevel: 2,
      practices: [],
    },
    {
      function: 'VERIFICATION',
      title: 'Verification',
      description: 'Testing & code quality',
      score: 2.0,
      maturityLevel: 2,
      practices: [],
    },
    {
      function: 'OPERATIONS',
      title: 'Operations',
      description: 'Environment & monitoring',
      score: 2.0,
      maturityLevel: 2,
      practices: [],
    },
  ];

  const mockApp: SammApplicationScore = {
    pipelineId: 100,
    pipelineKey: 'BILLING-PROD-KEY',
    jobName: 'job-billing',
    productId: 10,
    productName: 'Billing Platform',
    serviceId: 20,
    serviceName: 'billing-api',
    departmentId: 1,
    departmentName: 'Core Banking',
    overallScore: 2.1,
    maturityLevel: 2,
    maturityTier: 'Defined',
    domainScores: mockDomainScores,
    practiceScores: mockDomainScores[0].practices,
    assessedAt: '2026-11-04T12:00:00Z',
  };

  const mockDept: SammDepartmentScore = {
    departmentId: 1,
    departmentName: 'Core Banking',
    applicationCount: 1,
    overallScore: 2.1,
    maturityLevel: 2,
    maturityTier: 'Defined',
    domainScores: mockDomainScores,
    levelDistribution: { 0: 0, 1: 0, 2: 1, 3: 0 },
  };

  const mockOverview: SammOrganizationOverview = {
    totalApplications: 1,
    totalDepartments: 1,
    overallScore: 2.1,
    maturityLevel: 2,
    maturityTier: 'Defined',
    domainScores: mockDomainScores,
    departments: [mockDept],
    applications: [mockApp],
    levelDistribution: { 0: 0, 1: 0, 2: 1, 3: 0 },
    generatedAt: '2026-11-04T12:00:00Z',
  };

  const overviewOf = vi.fn((_scope?: unknown) => of(mockOverview));
  const applicationOf = vi.fn((_pipelineId: number) => of(mockApp));
  const mockSammApi = {
    overview: overviewOf,
    departments: () => of([mockDept]),
    department: () => of(mockDept),
    application: applicationOf,
  };

  afterEach(() => {
    localStorage.removeItem('oneview.department');
    localStorage.removeItem('oneview.application');
    applicationOf.mockClear();
  });

  /** Opens the page at the address given, as a link to it does. */
  async function openAt(url: string) {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [SammOverviewPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: SammApi, useValue: mockSammApi },
      ],
    }).compileComponents();
    await TestBed.inject(Router).navigateByUrl(url);

    fixture = TestBed.createComponent(SammOverviewPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => openAt('/'));

  it('renders the SAMM header and executive summary', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('h1')?.textContent).toContain('DevSecOps OWASP SAMM');
    expect(el.querySelector('.big-score')?.textContent).toContain('2.1');
    expect(el.querySelector('.tier-pill')?.textContent).toContain('Level 2 · Defined');
  });

  it('renders 5 domain scorecards on executive summary tab', () => {
    const el = fixture.nativeElement as HTMLElement;
    const cards = el.querySelectorAll('.domain-card');
    expect(cards.length).toBe(5);
    expect(el.textContent).toContain('Governance');
    expect(el.textContent).toContain('Design');
    expect(el.textContent).toContain('Implementation');
    expect(el.textContent).toContain('Verification');
    expect(el.textContent).toContain('Operations');
  });

  it('renders department summary card', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.dept-name')?.textContent).toContain('Core Banking');
    expect(el.querySelector('.dept-apps-count')?.textContent).toContain('1 applications');
  });

  it('switches to applications tab and renders application table row', async () => {
    const el = fixture.nativeElement as HTMLElement;
    const tabButtons = el.querySelectorAll<HTMLButtonElement>('.tab-btn');
    tabButtons[1].click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(el.querySelector('.applications-table')).toBeTruthy();
    expect(el.querySelector('.product-name')?.textContent).toContain('Billing Platform');
    expect(el.querySelector('.service-name')?.textContent).toContain('billing-api');
    expect(el.querySelector('.dept-badge')?.textContent).toContain('Core Banking');
  });

  it('filters applications by search query', async () => {
    component['search'].setValue('non-matching-app');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component['filteredApplications']().length).toBe(0);

    component['search'].setValue('Billing');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component['filteredApplications']().length).toBe(1);
  });

  it('opens detailed scorecard modal and displays practices with criteria and recommendations', async () => {
    component['selectApplication'](mockApp);
    fixture.detectChanges();
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    const modal = el.querySelector('.scorecard-modal');
    expect(modal).toBeTruthy();
    expect(modal?.textContent).toContain('Billing Platform / billing-api');
    expect(modal?.textContent).toContain('Strategy & Metrics');
    expect(modal?.textContent).toContain('Pipeline stage metrics telemetry is enabled');

    component['closeModal']();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(el.querySelector('.scorecard-modal')).toBeFalsy();
  });

  it('scores BBH while nothing is chosen, and a business service or application once chosen', async () => {
    expect(overviewOf).toHaveBeenLastCalledWith({ departmentId: null, productId: null });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.score-label')?.textContent).toContain('BBH Organization Maturity');

    TestBed.inject(OneViewContext).choose(1);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(overviewOf).toHaveBeenLastCalledWith({ departmentId: 1, productId: null });
    expect(el.querySelector('.score-label')?.textContent).toContain('Core Banking Maturity');

    TestBed.inject(OneViewContext).choose(1, 10);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(overviewOf).toHaveBeenLastCalledWith({ departmentId: 1, productId: 10 });
    expect(el.querySelector('.score-label')?.textContent).toContain('Billing Platform Maturity');
  });

  it('words the average and the counts by the business service or application chosen', async () => {
    const el = fixture.nativeElement as HTMLElement;
    const labels = () => [...el.querySelectorAll('.stat-label')].map((label) => label.textContent);
    expect(el.querySelector('.section-subtitle')?.textContent).toBe(
      'Average score across the 1 BBH application',
    );
    expect(labels()).toContain('Business Services Covered');

    TestBed.inject(OneViewContext).choose(1);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(el.querySelector('.section-subtitle')?.textContent).toBe(
      'Average score across 1 application of Core Banking',
    );
    expect(labels()).not.toContain('Business Services Covered');

    TestBed.inject(OneViewContext).choose(1, 10);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(el.querySelector('.section-subtitle')?.textContent).toBe(
      'Average score across 1 pipeline of Billing Platform',
    );
    expect(labels()).not.toContain('Business Services Covered');
  });

  it('opens the scorecard of the pipeline a link names once, also after another scope is chosen', async () => {
    await openAt('/?pipelineId=100');
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('.scorecard-modal')?.textContent).toContain(
      'Billing Platform / billing-api',
    );
    expect(applicationOf).not.toHaveBeenCalled();

    component['closeModal']();
    TestBed.inject(OneViewContext).choose(1);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(el.querySelector('.scorecard-modal')).toBeNull();
  });

  it('reads the scorecard of a linked pipeline outside the business service chosen on its own', async () => {
    TestBed.inject(OneViewContext).choose(1);
    applicationOf.mockReturnValueOnce(
      of({ ...mockApp, pipelineId: 300, productName: 'Trade Archive', serviceName: 'archive-api' }),
    );

    await openAt('/?pipelineId=300');

    expect(applicationOf).toHaveBeenCalledWith(300);
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.scorecard-modal')?.textContent,
    ).toContain('Trade Archive / archive-api');
  });

  it('opens the applications of a business service from its card and chooses it for every page', async () => {
    const el = fixture.nativeElement as HTMLElement;
    el.querySelector<HTMLButtonElement>('.btn-inspect-dept')!.click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(TestBed.inject(OneViewContext).scope()).toEqual({ departmentId: 1, productId: null });
    expect(el.querySelector('.applications-table')).toBeTruthy();
    expect(el.querySelector('#dept-select')).toBeNull();
  });
});
