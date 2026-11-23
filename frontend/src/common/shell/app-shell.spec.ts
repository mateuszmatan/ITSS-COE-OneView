import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { APP_NAME } from '../core/app-name';
import { OneViewArea, OneViewSection } from '../core/sections';
import { text } from '../testing/dom';
import { AppShell, WITH_CONTEXT } from './app-shell';

@Component({ template: '' })
class Blank {}

const section = (path: string, label: string): OneViewSection => ({
  path,
  label,
  heading: `Heading of ${label}`,
  description: `What ${label} is for`,
});

const HOME = section('/', 'Home');

const AREAS: OneViewArea[] = [
  {
    key: 'sales',
    label: 'Sales',
    path: '/admin',
    description: 'What Sales is for',
    sections: [section('/orders', 'Orders'), section('/admin', 'Admin')],
  },
  {
    key: 'quality',
    label: 'Quality',
    path: '/quality',
    description: 'What Quality is for',
    sections: [section('/quality', 'Metrics')],
  },
];

describe('AppShell', () => {
  let fixture: ComponentFixture<AppShell>;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideRouter([
          { path: 'summary', component: Blank, data: WITH_CONTEXT },
          { path: '**', component: Blank },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_NAME, useValue: 'Test Portal' },
      ],
    });
    fixture = TestBed.createComponent(AppShell);
    fixture.componentRef.setInput('home', HOME);
    fixture.componentRef.setInput('areas', AREAS);
    await TestBed.inject(Router).navigateByUrl('/orders');
    await fixture.whenStable();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const links = () => [...page().querySelectorAll<HTMLAnchorElement>('nav.menu a')];
  const active = () =>
    links()
      .filter((link) => link.classList.contains('active'))
      .map(text);

  it('names the app in its header and links every section of the current area in order', () => {
    expect(page().querySelector('.brand-name')?.textContent).toBe('BBH Test Portal');
    expect(page().querySelector('.brand')?.getAttribute('href')).toBe('/');
    expect(links().map(text)).toEqual(['Orders', 'Admin']);
    expect(links().map((link) => link.getAttribute('href'))).toEqual(['/orders', '/admin']);
    expect(page().querySelector('header svg-icon, header svg')).toBeNull();
    expect(text(page().querySelector('header'))).not.toContain('What Orders is for');
  });

  it('marks the section of the current page, also on the pages below it', async () => {
    await TestBed.inject(Router).navigateByUrl('/admin/products/2/edit');
    await fixture.whenStable();

    expect(active()).toEqual(['Admin']);
    expect(links()[1].getAttribute('aria-current')).toBe('page');

    await TestBed.inject(Router).navigateByUrl('/orders');
    await fixture.whenStable();

    expect(active()).toEqual(['Orders']);
  });

  const areas = () => [...page().querySelectorAll<HTMLAnchorElement>('nav.sidebar a')];
  const activeAreas = () =>
    areas()
      .filter((link) => link.classList.contains('active'))
      .map(text);

  it('has a side menu with the landing page and every area, and no area switch in its header', () => {
    expect(areas().map(text)).toEqual(['Home', 'Sales', 'Quality']);
    expect(areas().map((link) => link.getAttribute('href'))).toEqual(['/', '/admin', '/quality']);
    expect(activeAreas()).toEqual(['Sales']);
    expect(page().querySelector('header nav.areas')).toBeNull();
    expect(page().querySelector('header')?.textContent).not.toContain('Quality');
  });

  it('marks the landing page in the side menu and shows no sections on it', async () => {
    await TestBed.inject(Router).navigateByUrl('/');
    await fixture.whenStable();

    expect(activeAreas()).toEqual(['Home']);
    expect(areas()[0].getAttribute('aria-current')).toBe('page');
    expect(page().querySelector('nav.menu')).toBeNull();
  });

  it('marks the area the current page belongs to and shows its sections', async () => {
    await TestBed.inject(Router).navigateByUrl('/quality');
    await fixture.whenStable();

    expect(activeAreas()).toEqual(['Quality']);
    expect(areas()[2].getAttribute('aria-current')).toBe('true');
    expect(page().querySelector('nav.menu')).toBeNull();

    await TestBed.inject(Router).navigateByUrl('/admin/products?page=2');
    await fixture.whenStable();

    expect(activeAreas()).toEqual(['Sales']);
    expect(links().map(text)).toEqual(['Orders', 'Admin']);
    expect(active()).toEqual(['Admin']);
  });

  it('lists the pages of every area below the areas and marks the one shown', async () => {
    fixture.componentRef.setInput('tools', [
      section('/history', 'History'),
      section('/setup', 'Setup'),
    ]);
    await TestBed.inject(Router).navigateByUrl('/setup/users');
    await fixture.whenStable();

    expect(areas().map(text)).toEqual(['Home', 'Sales', 'Quality', 'History', 'Setup']);
    expect(page().querySelectorAll('nav.sidebar .side-separator')).toHaveLength(1);
    expect(activeAreas()).toEqual(['Setup']);
    expect(areas()[4].getAttribute('aria-current')).toBe('true');
    expect(page().querySelector('nav.menu')).toBeNull();
  });

  it('shows the business service and application bar only above the pages that follow it', async () => {
    const http = TestBed.inject(HttpTestingController);
    expect(page().querySelector('ov-context-bar')).toBeNull();

    await TestBed.inject(Router).navigateByUrl('/summary');
    await fixture.whenStable();

    expect(page().querySelector('main ov-context-bar')).not.toBeNull();
    http.expectOne('/api/departments').flush([{ id: 3, name: 'Custody' }]);
    http.expectOne('/api/products').flush([]);

    await TestBed.inject(Router).navigateByUrl('/orders');
    await fixture.whenStable();

    expect(page().querySelector('ov-context-bar')).toBeNull();
    http.verify();
  });

  it('shows the signed-in user only once it is known', async () => {
    expect(page().querySelector('.user-badge')).toBeNull();

    fixture.componentRef.setInput('user', {
      displayName: 'Ann Lee',
      email: 'ann@bbh.com',
      admin: true,
    });
    await fixture.whenStable();

    expect(text(page().querySelector('.user-name'))).toBe('Ann Lee');
    expect(text(page().querySelector('.user-role'))).toBe('Administrator');
    expect(page().querySelector('.user-badge')?.getAttribute('title')).toBe('ann@bbh.com');

    fixture.componentRef.setInput('user', { displayName: 'Bob Day' });
    await fixture.whenStable();

    expect(text(page().querySelector('.user-badge'))).toBe('Bob Day');
    expect(page().querySelector('.user-role')).toBeNull();
  });

  it('has an empty footer apart from the year mark', () => {
    expect(page().querySelector('footer')?.textContent?.trim()).toBe('BBH 2026');
  });
});
