import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { buttonOf, inputOf, text, toast, unexplained } from '@common/testing/dom';
import { globalSettings, jenkinsAutomation, noJenkinsAutomation } from '../testing/fixtures';
import { JenkinsAutomationPage } from './jenkins-automation-page';

describe('JenkinsAutomationPage', () => {
  let fixture: ComponentFixture<JenkinsAutomationPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [JenkinsAutomationPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(JenkinsAutomationPage);
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const state = () => text(page().querySelector('[data-testid=automation-state]'));

  async function load(stored = jenkinsAutomation()) {
    fixture.detectChanges();
    http.expectOne({ method: 'GET', url: '/api/jenkins-automation' }).flush(stored);
    const settings = globalSettings();
    http.expectOne('/api/settings').flush({
      ...settings,
      platform: { ...settings.platform, jenkinsUrl: 'https://jenkins.bbh.com' },
    });
    await fixture.whenStable();
  }

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

  it('says what is still needed before OneView acts by itself, and explains every field', async () => {
    await load(noJenkinsAutomation());

    expect(state()).toBe('Not set up');
    expect(text(page())).toContain(
      'Still needed before OneView acts by itself: the generic user, the OIS host, the OIS API key.',
    );
    expect(text(page())).toContain('No key is stored yet');
    expect(unexplained(page())).toEqual([]);
    expect(buttonOf(page(), 'Check the connection').disabled).toBe(true);
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);
  });

  it('shows the stored settings without the OIS API key', async () => {
    await load();

    expect(state()).toBe('Ready');
    expect(inputOf(page(), 'Generic user').value).toBe('svc-oneview');
    expect(inputOf(page(), 'OIS host').value).toBe('ois-qc.bbh.com');
    expect(inputOf(page(), 'OIS API key').value).toBe('');
    expect(inputOf(page(), 'OIS API key').type).toBe('password');
    expect(text(page())).toContain('A key is stored. Leave this empty to keep it.');
    expect(inputOf(page(), 'Jenkins address').value).toBe('https://jenkins.bbh.com');
  });

  it('saves what was typed, an empty API key as none so that the stored one stays', async () => {
    await load();
    await type('OIS host', ' ois-qc.bbh.com:8443 ');
    await type('Library repository', 'https://bitbucket.bbh.com/scm/dso/dsoenhanced.git');
    await submit();

    const request = http.expectOne({ method: 'PUT', url: '/api/jenkins-automation' });
    expect(request.request.body).toEqual({
      version: 2,
      genericUser: 'svc-oneview',
      oisHost: 'ois-qc.bbh.com:8443',
      oisApiKey: null,
      pollSchedule: 'H/5 * * * *',
      libraryRepositoryUrl: 'https://bitbucket.bbh.com/scm/dso/dsoenhanced.git',
      libraryVersion: 'main',
      libraryCredentialsId: null,
    });
    request.flush(jenkinsAutomation({ version: 3, oisHost: 'ois-qc.bbh.com:8443' }));
    await fixture.whenStable();

    expect(text(toast())).toContain('OneView can now read and create Jenkins jobs by itself');
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);
  });

  it('does not send what cannot work', async () => {
    await load();
    await type('OIS host', 'https://ois-qc.bbh.com');
    await type('Looks for changes', 'now <b>');
    await submit();

    expect(text(page())).toContain('Give a host name, without https://');
    expect(text(page())).toContain('Give a Jenkins schedule such as H/5 * * * *');
  });

  it('tells when someone else saved in the meantime, and discards on request', async () => {
    await load();
    await type('Generic user', 'svc-other');
    await submit();
    http
      .expectOne({ method: 'PUT', url: '/api/jenkins-automation' })
      .flush({ detail: 'stale' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(text(page())).toContain('Someone else saved the Jenkins automation');

    buttonOf(page(), 'Discard changes').click();
    await fixture.whenStable();

    expect(inputOf(page(), 'Generic user').value).toBe('svc-oneview');
    expect(text(page())).not.toContain('Someone else saved');
  });

  it('checks the connection with what is stored and shows the answer', async () => {
    await load();
    buttonOf(page(), 'Check the connection').click();
    const check = http.expectOne('/api/jenkins-automation/check');
    expect(check.request.body).toEqual({ jenkinsUrl: 'https://jenkins.bbh.com' });
    check.flush({ ok: true, message: 'OIS gave the secret and Jenkins knows the generic user.' });
    await fixture.whenStable();

    const result = page().querySelector('[data-testid=check-result]')!;
    expect(text(result)).toBe('OIS gave the secret and Jenkins knows the generic user.');
    expect(result.classList.contains('success')).toBe(true);
  });

  it('asks for the address of Jenkins first, and says so when the check itself fails', async () => {
    await load();
    await type('Jenkins address', '');
    buttonOf(page(), 'Check the connection').click();
    await fixture.whenStable();
    expect(text(page().querySelector('[data-testid=check-result]'))).toBe(
      'Give the address of Jenkins first.',
    );

    await type('Jenkins address', 'https://jenkins.bbh.com');
    buttonOf(page(), 'Check the connection').click();
    http
      .expectOne('/api/jenkins-automation/check')
      .flush('', { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    const result = page().querySelector('[data-testid=check-result]')!;
    expect(text(result)).toBe('OneView could not run the check. Try again in a moment.');
    expect(result.classList.contains('danger')).toBe(true);
  });
});
