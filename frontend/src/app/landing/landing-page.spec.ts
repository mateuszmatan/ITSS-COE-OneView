import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { text } from '@common/testing/dom';
import { LandingPage } from './landing-page';

@Component({ template: '' })
class Blank {}

describe('LandingPage', () => {
  let fixture: ComponentFixture<LandingPage>;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [LandingPage],
      providers: [provideRouter([{ path: '**', component: Blank }])],
    });
    fixture = TestBed.createComponent(LandingPage);
    await fixture.whenStable();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const cards = () => [...page().querySelectorAll<HTMLAnchorElement>('a.area-card')];

  it('names OneView and says what it is for', () => {
    expect(text(page().querySelector('h1'))).toBe('ITSS COE OneView');
    expect(text(page().querySelector('.page-description'))).toContain('Choose where you want to go');
  });

  it('has one card for every area, linked to it', () => {
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

  it('lists the sections of an area that has several', () => {
    const sections = (card: HTMLElement) => [...card.querySelectorAll('.area-sections li')].map(text);

    expect(sections(cards()[0])).toEqual([
      'Pipelines',
      'Self-service',
      'Pipeline Monitoring',
      'OWASP SAMM',
      'Action history',
      'Admin',
    ]);
    expect(sections(cards()[1])).toEqual([]);
    expect(sections(cards()[2])).toEqual([]);
  });

  it('opens the area of the card that is clicked', async () => {
    cards()[1].click();
    await fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/qa');
  });
});
