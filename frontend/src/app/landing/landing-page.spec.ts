import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import {
  CONTEXT_APPLICATION_KEY,
  CONTEXT_DEPARTMENT_KEY,
  OneViewContext,
} from '@common/context/oneview-context';
import { text } from '@common/testing/dom';
import {
  agileApplication,
  monitoringOverview,
  notInAgile,
  notInQuality,
  productHealth,
  qualityApplication,
} from '../testing/fixtures';
import { LandingPage, agileFigures, devSecOpsFigures, qualityFigures } from './landing-page';

@Component({ template: '' })
class Blank {}

describe('LandingPage', () => {
  let fixture: ComponentFixture<LandingPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LandingPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: Blank }]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LandingPage);
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem(CONTEXT_DEPARTMENT_KEY);
    localStorage.removeItem(CONTEXT_APPLICATION_KEY);
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const cards = () => [...page().querySelectorAll<HTMLAnchorElement>('a.area-card')];
  const figures = (card: HTMLElement) => [...card.querySelectorAll('.area-figures li')].map(text);
  const payHub = productHealth({
    productId: 2,
    code: 'PAY',
    name: 'PayHub',
    departmentId: 5,
    statusCounts: { UNSTABLE: 1, SUCCESS: 1 },
  });

  async function load() {
    fixture.detectChanges();
    http
      .expectOne('/api/monitoring/products')
      .flush(monitoringOverview({ products: [productHealth(), payHub] }));
    http.expectOne('/api/qa/applications').flush([qualityApplication(), notInQuality()]);
    http.expectOne('/api/agile/applications').flush([agileApplication(), notInAgile()]);
    await fixture.whenStable();
  }

  it('names OneView and says what it is for', async () => {
    await load();

    expect(text(page().querySelector('h1'))).toBe('ITSS COE OneView');
    expect(text(page().querySelector('.page-description'))).toContain(
      'Choose where you want to go',
    );
  });

  it('has one card for every area, linked to it', async () => {
    await load();

    expect(cards().map((card) => text(card.querySelector('h2')))).toEqual([
      'DevSecOps',
      'QM Metric Tool',
      'Agile Assessment & Adoption',
    ]);
    expect(cards().map((card) => card.getAttribute('href'))).toEqual([
      '/monitoring',
      '/qa',
      '/agile',
    ]);
    expect(cards().every((card) => text(card.querySelector('.area-description')).length > 0)).toBe(
      true,
    );
    expect(cards().map((card) => text(card.querySelector('.area-open')))).toEqual([
      'Open DevSecOps',
      'Open QM Metric Tool',
      'Open Agile Assessment & Adoption',
    ]);
  });

  it('lists the sections of an area that has several', async () => {
    await load();
    const sections = (card: HTMLElement) =>
      [...card.querySelectorAll('.area-sections li')].map(text);

    expect(sections(cards()[0])).toEqual([
      'Pipelines',
      'Self-service',
      'Pipeline Monitoring',
      'OWASP SAMM',
    ]);
    expect(sections(cards()[1])).toEqual([]);
    expect(sections(cards()[2])).toEqual([]);
  });

  it('sums up every area for the whole of BBH', async () => {
    await load();

    expect(text(page().querySelector('.scope-note'))).toBe(
      'The figures on the cards are for the whole of BBH. Choose a business service or an application above to see only theirs.',
    );
    expect(figures(cards()[0])).toEqual(['5 pipelines', '3 passed', '2 failed or with warnings']);
    expect(figures(cards()[1])).toEqual([
      '1 of 2 applications take part',
      '1 with a Grafana dashboard',
    ]);
    expect(figures(cards()[2])).toEqual([
      '1 of 2 teams take part',
      '1 assessed',
      '3.2 average level, of 5',
    ]);
  });

  it('sums up the chosen business service and shows the chosen application alone', async () => {
    const context = TestBed.inject(OneViewContext);
    context.choose(5);
    await load();

    expect(text(page().querySelector('.scope-note'))).toBe(
      'The figures on the cards are for Fund Services. Choose All business services above to see the whole of BBH.',
    );
    expect(figures(cards()[0])).toEqual(['2 pipelines', '1 passed', '1 failed or with warnings']);
    expect(figures(cards()[1])).toEqual([
      '0 of 1 application takes part',
      '0 with a Grafana dashboard',
    ]);

    context.choose(3, 1);
    await fixture.whenStable();

    expect(text(page().querySelector('.scope-note'))).toContain('are for CertScanner.');
    expect(figures(cards()[1])).toEqual([
      'Takes part in the QM Metric Tool',
      'at least 85% code coverage',
      'at least 95% test pass rate',
    ]);
    expect(figures(cards()[2])).toEqual([
      'Takes part in Agile Assessment & Adoption',
      'Level 3.2 of 5, assessed 2 Nov 2026',
    ]);
  });

  it('says when the figures of an area could not be read', async () => {
    fixture.detectChanges();
    http
      .expectOne('/api/monitoring/products')
      .flush(null, { status: 500, statusText: 'Server Error' });
    expect(text(cards()[1].querySelector('.area-figures-note'))).toBe('Reading the figures…');
    http.expectOne('/api/qa/applications').flush([]);
    http.expectOne('/api/agile/applications').flush([]);
    await fixture.whenStable();

    expect(text(cards()[0].querySelector('.area-figures-note'))).toBe(
      'The figures could not be read just now.',
    );
    expect(figures(cards()[1])).toEqual([
      '0 of 0 applications take part',
      '0 with a Grafana dashboard',
    ]);
  });

  it('opens the area of the card that is clicked', async () => {
    await load();

    cards()[1].click();
    await fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/qa');
  });

  it('counts only the teams that take part, also when a team taken out keeps its assessments', () => {
    const takenOut = agileApplication({ productId: 4, enabled: false, version: 3 });

    expect(agileFigures([agileApplication(), takenOut, notInAgile()], false)).toEqual([
      { value: '1 of 3', label: 'teams take part' },
      { value: '1', label: 'assessed' },
      { value: '3.2', label: 'average level, of 5' },
    ]);
    expect(agileFigures([takenOut], false)).toEqual([
      { value: '0 of 1', label: 'team takes part' },
      { value: '0', label: 'assessed' },
      { value: '–', label: 'average level, of 5' },
    ]);
  });

  it('counts a team that was never assessed as such', () => {
    expect(agileFigures([notInAgile()], true)).toEqual([
      { value: 'Not set up', label: 'in Agile Assessment & Adoption' },
      { value: 'Not assessed', label: 'yet' },
    ]);
    expect(qualityFigures([], true)).toEqual([
      { value: '0 of 0', label: 'applications take part' },
      { value: '0', label: 'with a Grafana dashboard' },
    ]);
    expect(devSecOpsFigures([productHealth({ statusCounts: { SUCCESS: 1 } })])[0]).toEqual({
      value: '1',
      label: 'pipeline',
    });
  });
});
