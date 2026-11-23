import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  checkboxOf,
  choose,
  inputOf,
  optionsOf,
  selectOf,
  text,
  unexplained,
} from '@common/testing/dom';
import { AgileApplication } from '../core/models';
import { agileApplication, agileModel, notInAgile } from '../testing/fixtures';
import { AgileProfileDialog, AgileProfileDialogData } from './agile-profile-dialog';

describe('AgileProfileDialog', () => {
  let fixture: ComponentFixture<AgileProfileDialog>;
  let http: HttpTestingController;
  const close = vi.fn();

  async function open(application: AgileApplication) {
    TestBed.configureTestingModule({
      imports: [AgileProfileDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: DIALOG_DATA,
          useValue: { application, model: agileModel() } satisfies AgileProfileDialogData,
        },
        { provide: DialogRef, useValue: { close } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AgileProfileDialog);
    await fixture.whenStable();
  }

  afterEach(() => {
    http.verify();
    close.mockReset();
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

  it('sets up the team of an application with its way of working', async () => {
    await open(notInAgile());

    expect(text(page().querySelector('h2'))).toBe('Set up the team of PayHub');
    expect(unexplained(page())).toEqual([]);
    expect(checkboxOf(page(), 'Takes part in Agile Assessment & Adoption').checked).toBe(true);
    expect(optionsOf(selectOf(page(), 'Way of working'))).toEqual([
      'Not set',
      'Scrum',
      'Kanban',
      'SAFe',
      'Other',
    ]);

    await type('Team', ' Pay Team ');
    choose(selectOf(page(), 'Way of working'), 'Kanban');
    await type('Agile coach', 'Joe Coach');
    await submit();

    const request = http.expectOne({ method: 'PUT', url: '/api/agile/applications/2' });
    expect(request.request.body).toEqual({
      enabled: true,
      teamName: 'Pay Team',
      framework: 'KANBAN',
      coach: 'Joe Coach',
      version: null,
    });
    const saved = notInAgile({ enabled: true, teamName: 'Pay Team', version: 0 });
    request.flush(saved);

    expect(close).toHaveBeenCalledWith(saved);
  });

  it('changes a team with the version it was read at', async () => {
    await open(agileApplication());

    expect(text(page().querySelector('h2'))).toBe('The team of CertScanner');
    expect(inputOf(page(), 'Team').value).toBe('Cert Squad');

    checkboxOf(page(), 'Takes part in Agile Assessment & Adoption').click();
    choose(selectOf(page(), 'Way of working'), 'Not set');
    await submit();

    const request = http.expectOne({ method: 'PUT', url: '/api/agile/applications/1' });
    expect(request.request.body).toEqual({
      enabled: false,
      teamName: 'Cert Squad',
      framework: null,
      coach: 'Joe Coach',
      version: 1,
    });
    request.flush(agileApplication({ enabled: false, framework: null, version: 2 }));

    expect(close).toHaveBeenCalled();
  });

  it('says when someone else changed the team first and stays open', async () => {
    await open(agileApplication());

    await submit();
    http
      .expectOne('/api/agile/applications/1')
      .flush({ detail: 'stale' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(text(page().querySelector('[role=alert]'))).toBe(
      'Someone else changed the team of CertScanner after you opened it, so nothing was saved. Close this, then open it again to see their changes.',
    );
    expect(close).not.toHaveBeenCalled();
  });
});
