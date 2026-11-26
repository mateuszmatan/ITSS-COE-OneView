import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EventEmitter } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import {
  Pipeline,
  PipelineMonitoring,
  Product,
  PullRequestDecoration,
  ServicePipelines,
} from '../core/models';
import { KeyHistoryDialog } from '../products/key-history-dialog';
import { PipelineDialog } from '../products/pipeline-dialog';
import { RevokeKeyDialog } from '../products/revoke-key-dialog';
import { CodeDialog } from '../shared/code-dialog';
import { ConfirmDialog } from '@common/shared/confirm-dialog';
import { buttonOf, gridColumn, gridRows, text, toast } from '@common/testing/dom';
import {
  anotherService,
  decorationFinding,
  globalSettings,
  pipeline,
  pipelineMonitoring,
  pipelineRun,
  product,
  pullRequestDecoration,
  revokedKey,
  service,
  servicePipelines,
} from '../testing/fixtures';
import { PipelinePage } from './pipeline-page';

const spyOnOpen = () => vi.spyOn(TestBed.inject(Dialog), 'open');
const DECORATIONS = '/api/pipelines/100/pull-request-decorations?limit=20';

describe('PipelinePage', () => {
  let fixture: ComponentFixture<PipelinePage>;
  let http: HttpTestingController;
  let router: Router;
  let open: ReturnType<typeof spyOnOpen>;

  const closed = (result: unknown) => ({ closed: of(result) }) as unknown as DialogRef<unknown>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PipelinePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    open = spyOnOpen().mockReturnValue(closed(undefined));
    fixture = TestBed.createComponent(PipelinePage);
    fixture.componentRef.setInput('id', '100');
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const opened = (index = 0) => ({
    component: open.mock.calls[index][0],
    data: open.mock.calls[index][1]?.data as Record<string, unknown>,
  });

  async function load(
    stored: Pipeline = pipeline({ keys: [pipeline().activeKey!] }),
    monitoring: PipelineMonitoring = pipelineMonitoring(),
    services: ServicePipelines[] = [servicePipelines({ pipelines: [stored] })],
    owner: Product = product(),
    decorations: PullRequestDecoration[] = [],
  ) {
    fixture.detectChanges();
    http.expectOne('/api/settings').flush(globalSettings());
    http.expectOne('/api/pipelines/100').flush(stored);
    http.expectOne('/api/monitoring/pipelines/100?range=30d').flush(monitoring);
    http.expectOne(DECORATIONS).flush(decorations);
    await products(services, owner);
    await fixture.whenStable();
  }

  async function products(
    services: ServicePipelines[] = [servicePipelines()],
    owner: Product = product(),
  ) {
    await settle();
    fixture.detectChanges();
    await settle();
    http.expectOne('/api/products/1').flush(owner);
    http.expectOne('/api/products/1/pipelines').flush(services);
  }

  async function openMore() {
    buttonOf(page(), 'More').click();
    await fixture.whenStable();
  }

  const settle = () => new Promise((resolve) => setTimeout(resolve));

  async function menu(label: string) {
    await openMore();
    buttonOf(document, label).click();
    await settle();
  }

  async function reloadedMonitoring(monitoring: PipelineMonitoring = pipelineMonitoring()) {
    await settle();
    http.expectOne('/api/monitoring/pipelines/100?range=30d').flush(monitoring);
    await fixture.whenStable();
  }

  const chip = () => text(page().querySelector('.title ov-status-chip'));

  const fact = (label: string) =>
    [...page().querySelectorAll('.rows > div')]
      .find((row) => text(row.querySelector('dt')) === label)!
      .querySelector('dd');

  it('shows the pipeline with its key, settings, Jenkinsfile and recent runs', async () => {
    await load();

    expect(text(page().querySelector('.breadcrumb'))).toBe(
      'DevSecOps Pipelines/CertScanner/gui · Full',
    );
    expect(text(page().querySelector('h1'))).toBe('gui · Full pipeline');
    expect(text(page().querySelector('.last-run .muted'))).toBe('Last run');
    expect(text(page().querySelector('.last-run ov-status-chip'))).toBe('Passed');
    expect(text(page().querySelector('.page-description'))).toBe(
      'The automated build, test and security checks Jenkins runs for gui, a service of CertScanner CERT.',
    );
    expect(text(page().querySelector('.key-value'))).toBe('6f1c2d3e…9abc');
    expect([...page().querySelectorAll('.rows dt')].map(text)).toEqual([
      'Issued',
      'Last used by Jenkins',
      'Earlier keys',
      'What it does',
      'Jenkins job',
      'Jenkins agents',
      'Monitoring tags',
      'Last changed',
    ]);
    expect(text(fact('Last used by Jenkins'))).toBe('Not yet');
    expect(text(fact('Earlier keys'))).toBe('None');
    expect(text(fact('Jenkins agents')?.querySelector('.mono'))).toBe('linux-agent');
    expect(text(fact('Jenkins agents')?.querySelector('.explain'))).toBe(
      'The machines the pipeline runs on',
    );
    expect(text(page().querySelector('.jenkinsfile .section-help'))).toBe(
      "The file to put in the service's repository so Jenkins runs this pipeline. It holds only the pipeline key: Jenkins fetches every other setting from this portal.",
    );
    expect(text(page().querySelector('pre.code-block'))).toBe(
      "@Library('DevSecOpsJenkinsLibrary') _ devSecOpsPipeline(pipelineKey: '6f1c2d3e-0000-4abc-9def-123456789abc')",
    );
    expect(gridColumn(page(), 'result')).toEqual(['Passed', 'Failed']);
    expect(gridColumn(page(), 'build')).toEqual(['#42', '#41']);
    expect(
      [...page().querySelectorAll<HTMLAnchorElement>('.page-header .actions a')].map((link) => [
        text(link),
        link.getAttribute('href'),
      ]),
    ).toEqual([
      ['Open in Jenkins', 'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/'],
    ]);
    expect(
      [...page().querySelectorAll('.page-header .actions button')].map((button) => [
        text(button),
        button.classList.contains('btn-primary'),
      ]),
    ).toEqual([
      ['More', false],
      ['Edit settings', true],
    ]);
    expect(text(page().querySelector('.runs .section-help'))).toMatch(
      /^Up to 5 of the latest runs Jenkins reported in the past 30 days\. Times are in your time zone, .+\.$/,
    );
    const runs = page().querySelector<HTMLAnchorElement>('.runs .card-header a')!;
    expect(text(runs)).toBe('See every run and the delivery performance (DORA)');
    expect(runs.getAttribute('href')).toBe('/monitoring/pipelines/100');
    expect(page().querySelector('.banner.danger')).toBeNull();

    await openMore();
    const product = document.querySelector<HTMLAnchorElement>('.ov-menu a.dropdown-item')!;
    expect(text(product)).toBe('Open CertScanner in Admin');
    expect(product.getAttribute('href')).toBe('/admin/products/1');
    expect([...document.querySelectorAll('.ov-menu button')].map(text)).toEqual([
      'Configure service',
      'Settings sent to Jenkins (devsecops_config.yaml format)',
      'Key history',
      'Replace key',
      'Invalidate key',
      'Delete pipeline',
    ]);
  });

  const api = (overrides: Partial<Pipeline> = {}) =>
    pipeline({
      id: 200,
      serviceId: 11,
      serviceName: 'api',
      activeKey: { ...pipeline().activeKey!, value: 'a1b2c3d4-0000-4abc-9def-123456789abc' },
      ...overrides,
    });

  async function loadWithApi(gui: Pipeline, other: Pipeline = api()) {
    await load(
      gui,
      pipelineMonitoring(),
      [
        servicePipelines({ pipelines: [gui] }),
        servicePipelines({ serviceId: 11, serviceName: 'api', pipelines: [other] }),
      ],
      product({ services: [service(), anotherService()] }),
    );
  }

  it('shows the one Jenkinsfile of the services whose pipelines share the job of its repository', async () => {
    await loadWithApi(pipeline());

    expect(text(page().querySelector('.jenkinsfile .section-help'))).toBe(
      'One Jenkinsfile with the keys of gui and api goes into https://bitbucket.bbh.com/projects/CERT/repos/gui. Ask for one Jenkins job for these services, not one per service: one GoldenFix pull request then covers all the services of the job.',
    );
    expect(text(page().querySelector('pre.code-block'))).toBe(
      "@Library('DevSecOpsJenkinsLibrary') _ devSecOpsPipeline(pipelineKeys: [ '6f1c2d3e-0000-4abc-9def-123456789abc', 'a1b2c3d4-0000-4abc-9def-123456789abc', ])",
    );
  });

  it('shows the own Jenkinsfile of a service whose repository other services build in their own jobs', async () => {
    await loadWithApi(pipeline(), api({ jenkinsJob: 'DevSecOps/CERT/api-full' }));

    expect(text(page().querySelector('.jenkinsfile .section-help'))).toBe(
      "The file to put in the service's repository so Jenkins runs this pipeline. It holds only the pipeline key: Jenkins fetches every other setting from this portal.",
    );
    expect(text(page().querySelector('pre.code-block'))).toBe(
      "@Library('DevSecOpsJenkinsLibrary') _ devSecOpsPipeline(pipelineKey: '6f1c2d3e-0000-4abc-9def-123456789abc')",
    );
  });

  it('asks to replace the shared Jenkinsfile when the invalidated key of a shared job is regenerated', async () => {
    await loadWithApi(pipeline({ activeKey: null }));

    expect(text(page().querySelector('.banner.danger .banner-text'))).toBe(
      'The pipeline key is invalidated. The pipeline is refused its settings and stops at its next start. Regenerate the key, then replace the Jenkinsfile that this service shares with api in its repository with the new one.',
    );
    expect(text(page().querySelector('pre.code-block'))).toContain('<issue a new key first>');

    buttonOf(page(), 'Regenerate key').click();
    await fixture.whenStable();
    http.expectOne({ method: 'POST', url: '/api/pipelines/100/keys' }).flush(pipeline());
    await reloadedMonitoring();

    expect(text(page().querySelector('pre.code-block'))).toContain(
      "pipelineKeys: [ '6f1c2d3e-0000-4abc-9def-123456789abc', 'a1b2c3d4",
    );
  });

  it('reveals and hides the key', async () => {
    await load();

    buttonOf(page(), 'Show the key').click();
    await fixture.whenStable();
    expect(text(page().querySelector('.key-value'))).toBe('6f1c2d3e-0000-4abc-9def-123456789abc');

    buttonOf(page(), 'Hide the key').click();
    await fixture.whenStable();
    expect(text(page().querySelector('.key-value'))).toBe('6f1c2d3e…9abc');
  });

  it('shows at most five runs and says when none was reported', async () => {
    const runs = Array.from({ length: 7 }, (_, index) => pipelineRun({ build: 40 + index }));
    await load(undefined, pipelineMonitoring({ recentRuns: runs }));
    expect(gridRows(page()).length).toBe(5);

    fixture.componentRef.setInput('id', '101');
    fixture.detectChanges();
    http.expectOne('/api/pipelines/101').flush(pipeline({ id: 101 }));
    http
      .expectOne('/api/monitoring/pipelines/101?range=30d')
      .flush(pipelineMonitoring({ recentRuns: [], metricsError: 'InfluxDB timed out' }));
    http.expectOne('/api/pipelines/101/pull-request-decorations?limit=20').flush([]);
    await products();
    await fixture.whenStable();

    expect(text(page().querySelector('.small-empty h3'))).toBe('No runs in the past 30 days');
    expect(text(page().querySelector('.small-empty p'))).toBe(
      'Runs show here once Jenkins runs this pipeline with its Jenkinsfile.',
    );
    expect(text(page().querySelector('ov-metrics-banner'))).toContain('InfluxDB timed out');
  });

  it('shows the runs as loading, says why they could not be loaded and tries again', async () => {
    fixture.detectChanges();
    http.expectOne('/api/settings').flush(globalSettings());
    http.expectOne('/api/pipelines/100').flush(pipeline());
    http.expectOne(DECORATIONS).flush([]);
    await products();
    fixture.detectChanges();

    expect(page().querySelector('.runs ov-loading')).not.toBeNull();
    expect(page().querySelector('.runs .small-empty')).toBeNull();

    http
      .expectOne('/api/monitoring/pipelines/100?range=30d')
      .flush(null, { status: 502, statusText: 'Bad Gateway' });
    await fixture.whenStable();

    expect(text(page().querySelector('.runs .banner .banner-text'))).toBe(
      'The latest runs could not be loaded. The portal could not complete the request (error 502 Bad Gateway). Try again in a moment; if it keeps failing, tell the portal administrator.',
    );
    expect(page().querySelector('.runs .small-empty')).toBeNull();
    expect(page().querySelector('.runs ov-loading')).toBeNull();

    buttonOf(page().querySelector('.runs')!, 'Try again').click();
    await reloadedMonitoring();

    expect(page().querySelector('.runs .banner')).toBeNull();
    expect(gridColumn(page(), 'build')).toEqual(['#42', '#41']);
  });

  const decorations = () => page().querySelector('.decorations')!;
  const decorationColumn = (column: string) =>
    [...decorations().querySelectorAll(`tr.decoration td.${column}`)].map(text);
  const pullRequests = () =>
    [...decorations().querySelectorAll('tr.decoration td.pull-request')].map((cell) =>
      [...cell.children].map(text),
    );

  it('lists the pull request decorations, newest first, and shows the findings of one', async () => {
    await load(undefined, undefined, undefined, undefined, [
      pullRequestDecoration(),
      pullRequestDecoration({
        id: 30,
        recordedAt: '2026-11-25T16:05:00Z',
        build: 0,
        kind: 'STATIC',
        title: null,
        pullRequestId: '9',
        pullRequestUrl: null,
        sourceBranch: 'security-static-gui',
        targetBranch: null,
        created: true,
        lineComments: 0,
        generalComments: 1,
        limited: true,
        maxComments: 1,
        findingsTotal: 0,
        findings: [],
      }),
    ]);

    expect(text(decorations().querySelector('h2'))).toBe('Pull request decorations');
    expect(text(decorations().querySelector('.section-help'))).toMatch(
      /^The comments the pipeline library posted on pull requests in Bitbucket for this pipeline, and the pull requests it opened, up to the newest 20, each with its findings\. Times are in your time zone, .+\.$/,
    );
    expect(decorationColumn('when')).toEqual([
      expect.stringMatching(/^2[56] Nov 2026, \d\d:\d\d$/),
      expect.stringMatching(/^2[56] Nov 2026, \d\d:\d\d$/),
    ]);
    expect(decorationColumn('build')).toEqual(['#42', '–']);
    expect(decorationColumn('kind')).toEqual([
      'Pull request of the analysed branch',
      'Static security pull request',
    ]);
    expect(pullRequests()).toEqual([
      ['Add the login page', 'feature/login → main'],
      ['#9', 'from security-static-gui', 'Opened by this decoration'],
    ]);
    expect(decorationColumn('service')).toEqual(['gui', 'gui']);
    expect(decorationColumn('comments')).toEqual([
      '3 under a line, 2 on the pull request',
      '0 under a line, 1 on the pull request, limited to 1',
    ]);
    expect(decorationColumn('findings')).toEqual([
      '3 findings: 2 AppScan SAST and 1 Nexus IQ Show the findings',
      'None',
    ]);
    const links = [...decorations().querySelectorAll<HTMLAnchorElement>('.pull-request a')];
    expect(links.map((link) => [text(link), link.getAttribute('href')])).toEqual([
      ['Add the login page', 'https://bitbucket.bbh.com/projects/CERT/repos/gui/pull-requests/12'],
    ]);
    expect(decorations().querySelector('.finding-list')).toBeNull();

    const show = buttonOf(decorations(), 'Show the findings');
    expect(show.getAttribute('aria-expanded')).toBe('false');
    show.click();
    await fixture.whenStable();

    const hide = buttonOf(decorations(), 'Hide the findings');
    expect(hide.getAttribute('aria-expanded')).toBe('true');
    expect(document.getElementById(hide.getAttribute('aria-controls')!)).not.toBeNull();
    expect(
      [...decorations().querySelectorAll('.finding-table tbody tr')].map((row) =>
        [...row.querySelectorAll('td')].map(text),
      ),
    ).toEqual([
      [
        'AppScan SAST',
        'HIGH',
        'src/main/java/com/bbh/cert/Login.java:42',
        'SQL Injection',
        'Use a prepared statement.',
      ],
      [
        'AppScan SAST',
        'HIGH',
        'src/main/java/com/bbh/cert/Login.java:77',
        'Cross-site scripting',
        'Use a prepared statement.',
      ],
      [
        'Nexus IQ',
        '–',
        'build.gradle',
        'org.apache.logging.log4j:log4j-core 2.14.1',
        'Upgrade to 2.17.1',
      ],
    ]);

    hide.click();
    await fixture.whenStable();
    expect(decorations().querySelector('.finding-list')).toBeNull();
    expect(buttonOf(decorations(), 'Show the findings').getAttribute('aria-expanded')).toBe(
      'false',
    );
  });

  it('says when no pull request was decorated for the pipeline yet', async () => {
    await load();

    expect(text(decorations().querySelector('.small-empty h3'))).toBe(
      'No pull request decorations yet',
    );
    expect(text(decorations().querySelector('.small-empty p'))).toBe(
      'They show here once the pipeline library comments on or opens a pull request for this pipeline.',
    );
    expect(decorations().querySelector('table')).toBeNull();
  });

  it('shows the decorations as loading, says why they could not be loaded and tries again', async () => {
    fixture.detectChanges();
    http.expectOne('/api/settings').flush(globalSettings());
    http.expectOne('/api/pipelines/100').flush(pipeline());
    http.expectOne('/api/monitoring/pipelines/100?range=30d').flush(pipelineMonitoring());
    await products();
    fixture.detectChanges();

    expect(decorations().querySelector('ov-loading')).not.toBeNull();

    http.expectOne(DECORATIONS).flush(null, { status: 502, statusText: 'Bad Gateway' });
    await fixture.whenStable();

    expect(text(decorations().querySelector('.banner .banner-text'))).toBe(
      'The pull request decorations could not be loaded. The portal could not complete the request (error 502 Bad Gateway). Try again in a moment; if it keeps failing, tell the portal administrator.',
    );
    expect(decorations().querySelector('ov-loading')).toBeNull();
    expect(decorations().querySelector('.small-empty')).toBeNull();

    buttonOf(decorations(), 'Try again').click();
    await settle();
    http
      .expectOne(DECORATIONS)
      .flush([pullRequestDecoration({ findings: [decorationFinding()], findingsTotal: 340 })]);
    await fixture.whenStable();

    expect(decorations().querySelector('.banner')).toBeNull();
    expect(decorationColumn('findings')).toEqual([
      '340 findings, the first 1: 1 AppScan SAST Show the findings',
    ]);
  });

  it('offers to regenerate an invalidated key and reveals the new one', async () => {
    await load(pipeline({ activeKey: null }), pipelineMonitoring({ status: 'DISABLED' }));

    expect(text(page().querySelector('.banner.danger .banner-text'))).toBe(
      "The pipeline key is invalidated. The pipeline is refused its settings and stops at its next start. Regenerate the key, then put the new Jenkinsfile in the service's repository.",
    );
    expect(text(page().querySelector('.title .last-run'))).toBe('Key invalidated');
    expect(page().querySelector('.last-run .muted')).toBeNull();
    expect(text(page().querySelector('.no-key'))).toBe(
      'The pipeline has no working key. Key history shows when and why it was invalidated.',
    );
    expect(buttonOf(page(), 'Edit settings').classList).toContain('btn-outline-primary');
    expect(page().querySelectorAll('.btn-primary').length).toBe(1);
    expect(text(page().querySelector('pre.code-block'))).toContain('<issue a new key first>');

    buttonOf(page(), 'Regenerate key').click();
    await fixture.whenStable();
    http.expectOne({ method: 'POST', url: '/api/pipelines/100/keys' }).flush(pipeline());
    await reloadedMonitoring();

    expect(page().querySelector('.banner.danger')).toBeNull();
    expect(text(page().querySelector('.key-value'))).toBe('6f1c2d3e-0000-4abc-9def-123456789abc');
    expect(chip()).toBe('Passed');
  });

  it('counts the earlier keys and opens their history, which can issue a new key', async () => {
    const issued = new EventEmitter<Pipeline>();
    await load(pipeline({ keys: [pipeline().activeKey!, revokedKey()] }));
    open.mockReturnValueOnce({ componentInstance: { keyIssued: issued } } as DialogRef<unknown>);

    expect(text(fact('Earlier keys'))).toBe('1 key, all invalidated · Key history');

    buttonOf(fact('Earlier keys')!, 'Key history').click();
    await fixture.whenStable();
    expect(opened()).toMatchObject({ component: KeyHistoryDialog, data: { id: 100 } });

    const replaced = pipeline();
    issued.emit({ ...replaced, activeKey: { ...replaced.activeKey!, value: 'c'.repeat(36) } });
    await reloadedMonitoring();
    expect(text(page().querySelector('.key-value'))).toBe('c'.repeat(36));
  });

  it('copies the key and the Jenkinsfile and says which', async () => {
    await load();

    buttonOf(page(), 'Copy the key').click();
    await fixture.whenStable();
    expect(text(toast())).toContain('Key copied to the clipboard');

    buttonOf(page(), 'Copy the Jenkinsfile').click();
    await fixture.whenStable();
    expect(text(toast())).toContain('Jenkinsfile copied to the clipboard');
  });

  it('saves the settings in the pipeline dialog', async () => {
    await load();
    open.mockReturnValueOnce(closed(pipeline({ agentLabels: ['docker'] })));

    buttonOf(page(), 'Edit settings').click();
    await reloadedMonitoring(pipelineMonitoring({ recentRuns: [] }));

    expect(opened().component).toBe(PipelineDialog);
    expect(text(fact('Jenkins agents')?.querySelector('.mono'))).toBe('docker');
    expect(text(page().querySelector('.small-empty h3'))).toBe('No runs in the past 30 days');
  });

  it('shows the configuration and replaces the key once confirmed', async () => {
    await load();

    await menu('Settings sent to Jenkins (devsecops_config.yaml format)');
    http.expectOne('/api/pipelines/100/config').flush('projects: {}');
    expect(opened()).toMatchObject({
      component: CodeDialog,
      data: { code: 'projects: {}', fileName: 'cert-gui-full.yaml' },
    });

    open.mockReturnValueOnce(closed(true));
    await menu('Replace key');
    expect(opened(1).component).toBe(ConfirmDialog);
    const replaced = pipeline();
    http.expectOne({ method: 'POST', url: '/api/pipelines/100/keys' }).flush({
      ...replaced,
      activeKey: { ...replaced.activeKey!, value: 'b'.repeat(64), hint: 'bbbbbbbb…bbbb' },
    });
    await reloadedMonitoring();

    expect(text(page().querySelector('.key-value'))).toBe('b'.repeat(64));
  });

  it('invalidates the key with the reason the dialog asks for', async () => {
    await load();
    open.mockReturnValueOnce(closed(pipeline({ activeKey: null })));

    expect(chip()).toBe('Passed');

    await menu('Invalidate key');
    await reloadedMonitoring(pipelineMonitoring({ status: 'DISABLED' }));

    expect(opened().component).toBe(RevokeKeyDialog);
    expect(page().querySelector('.banner.danger')).not.toBeNull();
    expect(chip()).toBe('Key invalidated');
  });

  it('deletes the pipeline once confirmed and returns to the pipelines', async () => {
    await load();
    open.mockReturnValueOnce(closed(true));

    await menu('Delete pipeline');
    http.expectOne({ method: 'DELETE', url: '/api/pipelines/100' }).flush(null);
    await fixture.whenStable();

    expect(router.navigate).toHaveBeenCalledWith(['/pipelines']);
  });

  it('opens ServiceConfigDialog when clicking Configure service', async () => {
    await load();
    open.mockReturnValueOnce(closed(false));
    await menu('Configure service');
    await fixture.whenStable();

    expect(open).toHaveBeenCalled();
  });

  it('says why the pipeline could not be read and leads back to the list', async () => {
    fixture.detectChanges();
    http.expectOne('/api/settings').flush(null, { status: 500, statusText: 'Error' });
    http
      .expectOne('/api/pipelines/100')
      .flush({ detail: 'Pipeline 100 does not exist' }, { status: 404, statusText: 'Not Found' });
    http
      .expectOne('/api/monitoring/pipelines/100?range=30d')
      .flush(null, { status: 404, statusText: 'Not Found' });
    http
      .expectOne(DECORATIONS)
      .flush({ detail: 'Pipeline 100 does not exist' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(text(page().querySelector('.banner .banner-text'))).toBe(
      'The pipeline could not be loaded. Pipeline 100 does not exist',
    );
    expect(page().querySelector('.decorations')).toBeNull();
    expect(page().querySelector('a.btn')?.getAttribute('href')).toBe('/pipelines');
    expect(text(page().querySelector('.breadcrumb'))).toBe('DevSecOps Pipelines/Pipeline');
  });
});
