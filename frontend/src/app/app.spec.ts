import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { APP_NAME } from '@common/core/app-name';
import { text } from '@common/testing/dom';
import { App } from './app';
import { SECTIONS, TOOLS } from './core/sections';

@Component({ template: '' })
class Blank {}

describe('App', () => {
  let fixture: ComponentFixture<App>;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: Blank }]),
        { provide: APP_NAME, useValue: 'ITSS COE OneView' },
      ],
    });
    fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl('/monitoring');
    await fixture.whenStable();
  });

  it('shows the user the portal reports as signed in', async () => {
    TestBed.inject(HttpTestingController)
      .expectOne('/api/user/me')
      .flush({ username: 'ann', displayName: 'Ann Lee', email: 'ann.lee@bbh.com', admin: false });
    await fixture.whenStable();

    expect(page().querySelector('.user-badge')?.textContent?.trim()).toBe('Ann Lee');
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const links = () => [...page().querySelectorAll<HTMLAnchorElement>('nav.menu a')];
  const active = () =>
    links()
      .filter((link) => link.classList.contains('active'))
      .map(text);

  it('names the portal in its header and links only the DevSecOps sections', () => {
    expect(page().querySelector('.brand-name')?.textContent).toBe('BBH ITSS COE OneView');
    expect(links().map(text)).toEqual([
      'Pipelines',
      'Self-service',
      'Pipeline Monitoring',
      'OWASP SAMM',
    ]);
    expect(links().map((link) => link.getAttribute('href'))).toEqual([
      '/pipelines',
      '/self-service',
      '/monitoring',
      '/samm',
    ]);
    expect(text(page())).not.toContain('Beadle');
    expect(text(page())).not.toContain('Change Evidence');
  });

  it('has a side menu with the landing page, the three areas, and the action history and admin of all of them', async () => {
    const areas = () => [...page().querySelectorAll<HTMLAnchorElement>('nav.sidebar a')];
    const activeAreas = () =>
      areas()
        .filter((link) => link.classList.contains('active'))
        .map(text);

    expect(areas().map(text)).toEqual([
      'Main Landing Page',
      'DevSecOps',
      'QM Metric Tool',
      'Agile Assessment & Adoption',
      'Action history',
      'Admin',
    ]);
    expect(areas().map((link) => link.getAttribute('href'))).toEqual([
      '/',
      '/monitoring',
      '/qa',
      '/agile',
      '/history',
      '/admin',
    ]);
    expect(activeAreas()).toEqual(['DevSecOps']);
    expect(page().querySelector('nav.areas')).toBeNull();

    await TestBed.inject(Router).navigateByUrl('/qa');
    await fixture.whenStable();

    expect(activeAreas()).toEqual(['QM Metric Tool']);
    expect(links()).toEqual([]);

    await TestBed.inject(Router).navigateByUrl('/agile');
    await fixture.whenStable();

    expect(activeAreas()).toEqual(['Agile Assessment & Adoption']);
    expect(links()).toEqual([]);

    await TestBed.inject(Router).navigateByUrl('/');
    await fixture.whenStable();

    expect(activeAreas()).toEqual(['Main Landing Page']);
    expect(links()).toEqual([]);
  });

  it('gives every section the full DevSecOps name as its page heading', () => {
    expect(SECTIONS.map((section) => [section.heading, section.description])).toEqual([
      [
        'DevSecOps Pipelines',
        'Every automated build, test and security pipeline of the business service or application chosen above. Open one to see its key, its Jenkinsfile and its latest runs.',
      ],
      [
        'DevSecOps Self-service',
        'Set up the DevSecOps pipelines of your product, or change them, in five guided steps. No DevSecOps knowledge needed.',
      ],
      [
        'DevSecOps Pipeline Monitoring',
        'How the pipelines of every product are doing: whether their latest runs passed, and how often and how safely changes reach production.',
      ],
      [
        'DevSecOps OWASP SAMM',
        'Software Assurance Maturity Model (OWASP SAMM v2.0) across BBH applications and business services. Continuous security posture scoring derived from pipeline health and verification gates.',
      ],
    ]);
  });

  it('names the action history and the admin after OneView, as they serve every area', () => {
    expect(TOOLS.map((tool) => [tool.label, tool.heading])).toEqual([
      ['Action history', 'OneView Action History'],
      ['Admin', 'OneView Admin'],
    ]);
  });

  it.each([
    ['/admin/products/2/edit', 'Admin'],
    ['/admin/qa', 'Admin'],
    ['/history', 'Action history'],
  ])('marks %s in the side menu as %s and shows no DevSecOps sections', async (url, label) => {
    await TestBed.inject(Router).navigateByUrl(url);
    await fixture.whenStable();

    const marked = [...page().querySelectorAll<HTMLAnchorElement>('nav.sidebar a.active')].map(
      text,
    );
    expect(marked).toEqual([label]);
    expect(links()).toEqual([]);
  });

  it.each([
    ['/monitoring', 'Pipeline Monitoring'],
    ['/monitoring/products/1', 'Pipeline Monitoring'],
    ['/pipelines/100', 'Pipelines'],
    ['/self-service', 'Self-service'],
    ['/samm', 'OWASP SAMM'],
  ])('marks the section of %s', async (url, label) => {
    await TestBed.inject(Router).navigateByUrl(url);
    await fixture.whenStable();

    expect(active()).toEqual([label]);
  });
});
