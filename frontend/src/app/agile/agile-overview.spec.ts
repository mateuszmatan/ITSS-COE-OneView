import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  CONTEXT_APPLICATION_KEY,
  CONTEXT_DEPARTMENT_KEY,
  OneViewContext,
} from '@common/context/oneview-context';
import { buttonOf, gridColumn, gridHeaders, gridRows, text } from '@common/testing/dom';
import { AgileApplication } from '../core/models';
import { AGILE_MATURITY } from '../core/sections';
import { agileApplication, agileAssessment, agileModel, notInAgile } from '../testing/fixtures';
import { AgileOverviewPage } from './agile-overview';

describe('AgileOverviewPage', () => {
  let fixture: ComponentFixture<AgileOverviewPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AgileOverviewPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AgileOverviewPage);
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem(CONTEXT_DEPARTMENT_KEY);
    localStorage.removeItem(CONTEXT_APPLICATION_KEY);
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const tiles = () => [...page().querySelectorAll('.summary .stat')].map(text);
  const levels = (selector: string) =>
    [...page().querySelectorAll(`${selector} .dimension`)].map((entry) =>
      [
        text(entry.querySelector('.dimension-label strong')),
        text(entry.querySelector('.value')),
      ].join(' '),
    );
  const ledger = agileApplication({
    productId: 3,
    code: 'LEDG',
    name: 'Ledger',
    departmentId: 5,
    departmentName: 'Fund Services',
    teamName: 'Ledger Team',
    framework: 'KANBAN',
    assessments: [
      agileAssessment({
        id: 21,
        productId: 3,
        levels: { TEAM: 2, PLANNING: 3, ENGINEERING: 4, PRODUCT: 4, IMPROVEMENT: 2 },
        overallLevel: 3,
      }),
    ],
  });
  const all = () => [agileApplication(), ledger, notInAgile()];

  async function load(applications: AgileApplication[] = all()) {
    fixture.detectChanges();
    http.expectOne('/api/agile/model').flush(agileModel());
    http.expectOne('/api/agile/applications').flush(applications);
    await fixture.whenStable();
  }

  it('sums up the teams of BBH, by dimension and by business service', async () => {
    await load();

    expect(text(page().querySelector('h1'))).toBe(AGILE_MATURITY.heading);
    expect(text(page().querySelector('.page-description'))).toBe(AGILE_MATURITY.description);
    expect(page().querySelector('.page-header a')?.getAttribute('href')).toBe('/admin/agile');
    expect(text(page().querySelector('.summary h2'))).toBe('Application teams');
    expect(text(page().querySelector('.summary .card-header .muted'))).toBe('BBH');
    expect(tiles()).toEqual([
      '3 Applications',
      '2 Teams taking part',
      '2 Assessed',
      '3.1 Average level, of 5',
    ]);
    expect(gridHeaders(page())).toEqual([
      'Application',
      'Business service',
      'Agile',
      'Team',
      'Way of working',
      'Agile coach',
      'Last assessed',
      'Level',
    ]);
    expect(gridColumn(page(), 'name')).toEqual(['CertScanner CERT', 'Ledger LEDG', 'PayHub PAY']);
    expect(gridColumn(page(), 'framework')).toEqual(['Scrum', 'Kanban', 'Not set']);
    expect(gridColumn(page(), 'assessedOn')).toEqual(['2 Nov 2026', '2 Nov 2026', '–']);
    expect(gridColumn(page(), 'overall')).toEqual(['3.2', '3.0', '–']);
    expect(levels('.dimensions')).toEqual([
      'Team and roles 3.0',
      'Planning and delivery 3.0',
      'Engineering practices 3.5',
      'Product ownership 3.0',
      'Continuous improvement 3.0',
    ]);
    expect(levels('.departments')).toEqual(['Corporate Technology 3.2', 'Fund Services 3.0']);
    expect([...page().querySelectorAll('ov-area-plan h3')].map(text)).toEqual([
      'Trend chart',
      'What the next level asks for',
    ]);
  });

  it('sums up only the teams of the chosen business service', async () => {
    TestBed.inject(OneViewContext).choose(5);
    await load();

    expect(text(page().querySelector('.summary .card-header .muted'))).toBe('Fund Services');
    expect(tiles()).toEqual([
      '2 Applications',
      '1 Teams taking part',
      '1 Assessed',
      '3.0 Average level, of 5',
    ]);
    expect(gridHeaders(page())).not.toContain('Business service');
    expect(page().querySelector('.departments')).toBeNull();
    expect(levels('.dimensions')[0]).toBe('Team and roles 2.0');
  });

  it('shows the latest assessment of one team, how it changed and every assessment', async () => {
    await load();

    buttonOf(page(), 'Show CertScanner alone').click();
    await fixture.whenStable();

    expect(TestBed.inject(OneViewContext).scope()).toEqual({ departmentId: 3, productId: 1 });
    expect(text(page().querySelector('.profile h2'))).toBe('CertScanner CERT');
    expect(text(page().querySelector('.profile .chip'))).toBe('Takes part');
    expect(
      [...page().querySelectorAll('.profile dt')].map(
        (term) => `${text(term)}: ${text(term.nextElementSibling)}`,
      ),
    ).toEqual([
      'Business service: Corporate Technology',
      'Team: Cert Squad',
      'Way of working: Scrum',
      'Agile coach: Joe Coach',
    ]);
    expect(text(page().querySelector('.latest .card-header .muted'))).toBe(
      'Assessed 2 Nov 2026 by Joe Coach',
    );
    expect(text(page().querySelector('.overall'))).toBe('3.2 overall level, of 5');
    expect(levels('.latest')).toEqual([
      'Team and roles 4',
      'Planning and delivery 3',
      'Engineering practices 3',
      'Product ownership 2',
      'Continuous improvement 4',
    ]);
    expect([...page().querySelectorAll('.latest .change')].map(text)).toEqual([
      'up 1 from 3',
      'up 1 from 2',
      'up 1 from 2',
      'up 1 from 3',
    ]);
    expect(text(page().querySelector('.notes'))).toBe('Notes: Retrospectives every sprint.');
    expect(gridRows(page().querySelector('.history')!)).toHaveLength(2);
    expect(gridColumn(page().querySelector('.history')!, 'overall')).toEqual(['3.2', '2.4']);
  });

  it('says how a team that was never assessed gets its first assessment', async () => {
    TestBed.inject(OneViewContext).choose(5, 2);
    await load();

    expect(text(page().querySelector('.not-taking-part .banner-text'))).toContain(
      'The team of PayHub does not take part in Agile Assessment & Adoption yet.',
    );
    expect(text(page().querySelector('.latest .empty-state h3'))).toBe(
      'No assessment of PayHub yet',
    );
    expect(page().querySelector('.latest .empty-state a')?.getAttribute('href')).toBe(
      '/admin/agile',
    );
    expect(page().querySelector('.history')).toBeNull();
  });

  it('counts only the teams that take part, and says how a team taken out comes back', async () => {
    const takenOut = { ...ledger, enabled: false, version: 2 };
    await load([agileApplication(), takenOut, notInAgile()]);

    expect(tiles()).toEqual([
      '3 Applications',
      '1 Teams taking part',
      '1 Assessed',
      '3.2 Average level, of 5',
    ]);
    expect(levels('.departments')).toEqual(['Corporate Technology 3.2']);

    TestBed.inject(OneViewContext).choose(5, 3);
    await fixture.whenStable();

    expect(text(page().querySelector('.not-taking-part .banner-text'))).toBe(
      'The team of Ledger no longer takes part in Agile Assessment & Adoption. An administrator brings it back in OneView Admin by editing its team and ticking Takes part.',
    );
    expect(text(page().querySelector('.overall'))).toBe('3.0 overall level, of 5');
  });

  it('leaves out the charts until a team is assessed', async () => {
    await load([notInAgile()]);

    expect(tiles()).toEqual([
      '1 Applications',
      '0 Teams taking part',
      '0 Assessed',
      '– Average level, of 5',
    ]);
    expect(page().querySelector('.charts')).toBeNull();
  });

  it('says why the teams could not be read', async () => {
    fixture.detectChanges();
    http.expectOne('/api/agile/model').flush(null, { status: 500, statusText: 'Server Error' });
    http.expectOne('/api/agile/applications').flush(all());
    await fixture.whenStable();

    expect(text(page().querySelector('ov-load-error .banner-text'))).toContain(
      'Agile Assessment & Adoption could not be loaded.',
    );
  });
});
