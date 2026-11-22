import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { APP_NAME } from '../core/app-name';
import { OneViewArea, OneViewSection } from '../core/sections';
import { text } from '../testing/dom';
import { AppShell } from './app-shell';

@Component({ template: '' })
class Blank {}

const section = (path: string, label: string): OneViewSection => ({
  path,
  label,
  heading: `Heading of ${label}`,
  description: `What ${label} is for`,
});

const AREAS: OneViewArea[] = [
  { key: 'sales', label: 'Sales', sections: [section('/orders', 'Orders'), section('/admin', 'Admin')] },
  { key: 'quality', label: 'Quality', sections: [section('/quality', 'Metrics')] },
];

describe('AppShell', () => {
  let fixture: ComponentFixture<AppShell>;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideRouter([{ path: '**', component: Blank }]),
        { provide: APP_NAME, useValue: 'Test Portal' },
      ],
    });
    fixture = TestBed.createComponent(AppShell);
    fixture.componentRef.setInput('areas', AREAS);
    await fixture.whenStable();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const links = () => [...page().querySelectorAll<HTMLAnchorElement>('nav.menu a')];
  const active = () =>
    links()
      .filter((link) => link.classList.contains('active'))
      .map(text);

  it('names the app in its header and links every section in order', () => {
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

  const areas = () => [...page().querySelectorAll<HTMLAnchorElement>('nav.areas a')];
  const activeAreas = () =>
    areas()
      .filter((link) => link.classList.contains('active'))
      .map(text);

  it('links every area to its first section and starts in the first area', () => {
    expect(areas().map(text)).toEqual(['Sales', 'Quality']);
    expect(areas().map((link) => link.getAttribute('href'))).toEqual(['/orders', '/quality']);
    expect(activeAreas()).toEqual(['Sales']);
  });

  it('shows the sections of the area the current page belongs to', async () => {
    await TestBed.inject(Router).navigateByUrl('/quality');
    await fixture.whenStable();

    expect(activeAreas()).toEqual(['Quality']);
    expect(areas()[1].getAttribute('aria-current')).toBe('true');
    expect(links().map(text)).toEqual(['Metrics']);
    expect(active()).toEqual(['Metrics']);

    await TestBed.inject(Router).navigateByUrl('/admin/products?page=2');
    await fixture.whenStable();

    expect(activeAreas()).toEqual(['Sales']);
    expect(links().map(text)).toEqual(['Orders', 'Admin']);
  });

  it('has no area switch when there is one area only', async () => {
    fixture.componentRef.setInput('areas', AREAS.slice(0, 1));
    await fixture.whenStable();

    expect(page().querySelector('nav.areas')).toBeNull();
    expect(links().map(text)).toEqual(['Orders', 'Admin']);
  });

  it('shows the signed-in user only once it is known', async () => {
    expect(page().querySelector('.user-badge')).toBeNull();

    fixture.componentRef.setInput('user', { displayName: 'Ann Lee', email: 'ann@bbh.com', admin: true });
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
