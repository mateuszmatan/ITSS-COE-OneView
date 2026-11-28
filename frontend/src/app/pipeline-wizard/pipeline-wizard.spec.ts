import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CONTEXT_DEPARTMENT_KEY } from '@common/context/oneview-context';
import { buttonOf, text } from '@common/testing/dom';
import { JenkinsAutomation, PipelineType } from '../core/models';
import {
  createdJenkinsJob,
  department,
  globalSettings,
  jenkinsAutomation,
  jenkinsJob,
  noJenkinsAutomation,
  pipeline,
  product,
  productSummary,
  servicePipelines,
  serviceTemplate,
  sourceInspection,
} from '../testing/fixtures';
import { PipelineWizard } from './pipeline-wizard';

const REPOSITORY = 'https://bitbucket.bbh.com/projects/PAY/repos/payments-api/browse';

describe('PipelineWizard', () => {
  let fixture: ComponentFixture<PipelineWizard>;
  let http: HttpTestingController;

  async function open(automation: JenkinsAutomation | null = jenkinsAutomation()) {
    localStorage.removeItem(CONTEXT_DEPARTMENT_KEY);
    TestBed.configureTestingModule({
      imports: [PipelineWizard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PipelineWizard);
    fixture.detectChanges();
    http.expectOne('/api/departments').flush([department()]);
    http.expectOne('/api/products').flush([productSummary()]);
    http.expectOne('/api/settings').flush(globalSettings());
    http.expectOne('/api/service-template').flush(serviceTemplate());
    const request = http.expectOne('/api/jenkins-automation');
    if (automation) {
      request.flush(automation);
    } else {
      request.flush('', { status: 503, statusText: 'Unavailable' });
    }
    await fixture.whenStable();
  }

  afterEach(() => http.verify());

  const wizard = () => fixture.componentInstance;
  const page = () => fixture.nativeElement as HTMLElement;
  const step = () => wizard()['step']();
  const review = () =>
    Object.fromEntries(
      wizard()
        ['review']()
        .map((line) => [line.label, line.value]),
    );

  async function next() {
    wizard()['next']();
    await fixture.whenStable();
  }

  async function basics(type: PipelineType = 'SAST') {
    wizard()['pipeline'].set(type as never);
    wizard()['basics'].patchValue({
      departmentId: department().id,
      productName: 'Payments',
      ownerTeam: 'Team Payments',
      serviceName: 'payments-api',
    });
    await next();
  }

  async function detect(inspection = sourceInspection()) {
    wizard()['sources'].controls.repositoryUrl.setValue(REPOSITORY);
    wizard()['sources'].controls.branch.setValue('develop');
    wizard()['detect']();
    const request = http.expectOne('/api/pipeline-setup/sources');
    expect(request.request.body).toEqual({ repositoryUrl: REPOSITORY, branch: 'develop' });
    request.flush(inspection);
    await fixture.whenStable();
  }

  async function readJob(job = jenkinsJob()) {
    wizard()['jobUrl'].setValue(`${job.jobUrl}42/`);
    wizard()['readJob']();
    const request = http.expectOne('/api/pipeline-setup/jenkins-job');
    expect(request.request.body).toEqual({ jobUrl: `${job.jobUrl}42/` });
    request.flush(job);
    await fixture.whenStable();
  }

  async function suggestCode(code = 'PAY') {
    http.expectOne((request) => request.url === '/api/products/code-suggestion').flush({ code });
    await fixture.whenStable();
  }

  /** Answers the requests that save the product and find its new pipeline. */
  async function saveProduct(type: PipelineType = 'SAST') {
    await suggestCode();
    const created = http.expectOne(
      (request) => request.method === 'POST' && request.url === '/api/products',
    );
    created.flush(product({ id: 7, name: 'Payments', code: 'PAY' }));
    await fixture.whenStable();
    http.expectOne('/api/products/7/pipelines').flush([
      servicePipelines({
        serviceName: 'payments-api',
        pipelines: [
          pipeline({
            id: 100,
            serviceName: 'payments-api',
            type,
            entryPoint: 'devSecOpsSASTScanningPipeline',
            jenkinsJob: null,
          }),
        ],
      }),
    ]);
    await fixture.whenStable();
    return created.request;
  }

  it('starts at the basics and does not go on before they are given', async () => {
    await open();

    expect(wizard()['steps']).toHaveLength(5);
    await next();
    expect(step()).toBe(0);

    await basics();
    expect(step()).toBe(1);
  });

  it('shows what it read from the sources and takes it over, and what the sources cannot tell', async () => {
    await open();
    await basics('SECURITY');
    await detect();

    expect(
      wizard()
        ['sourceFindings']()
        .map((finding) => finding.label),
    ).toContain('Java version');
    expect(text(page())).toContain('pom.xml found');
    expect(wizard()['sources'].controls.tool.value).toBe('MAVEN');
    expect(wizard()['target']()).toBe('OPENSHIFT');
    expect(wizard()['sources'].controls.agents.value).toBe('maven-jdk21');
    expect(wizard()['stillNeeded']()).toContain('The OpenShift project the service runs in');

    wizard()['sources'].controls.appScanId.setValue('app-1');
    await next();
    expect(step()).toBe(1);

    wizard()['sources'].controls.openShiftProject.setValue('payments');
    await next();
    expect(step()).toBe(2);
  });

  it('tells why the sources could not be read, and lets the user go on without them', async () => {
    await open();
    await basics();
    wizard()['detect']();
    expect(wizard()['inspectError']()).toBe('Give the address of the repository first.');

    wizard()['sources'].controls.repositoryUrl.setValue(REPOSITORY);
    wizard()['detect']();
    http
      .expectOne('/api/pipeline-setup/sources')
      .flush(
        { detail: 'Bitbucket refused an anonymous reader (HTTP 401).' },
        { status: 503, statusText: 'Unavailable' },
      );
    await fixture.whenStable();

    expect(wizard()['inspection']()).toBeNull();
    expect(text(page())).toContain('Bitbucket refused an anonymous reader (HTTP 401).');

    wizard()['sources'].controls.appScanId.setValue('app-1');
    await next();
    expect(step()).toBe(2);
  });

  it('sends a repository of database changes to Self-service', async () => {
    await open();
    await basics();
    const inspection = sourceInspection();
    inspection.stack = { ...inspection.stack, buildTool: 'LIQUIBASE' };
    await detect(inspection);
    wizard()['sources'].controls.appScanId.setValue('app-1');
    await next();

    expect(wizard()['databaseOnly']()).toBe(true);
    expect(step()).toBe(1);
  });

  it('reads an existing job, creates the product and then the Jenkins job with what it read', async () => {
    await open();
    await basics();
    await detect();
    wizard()['sources'].controls.appScanId.setValue('app-1');
    await next();

    await readJob();
    expect(
      wizard()
        ['jobFindings']()
        .map((finding) => finding.label),
    ).toContain('Parameter TARGET');
    expect(text(page())).toContain('build #42');
    await next();
    await suggestCode();

    expect(step()).toBe(3);
    expect(wizard()['job'].getRawValue()).toEqual({
      jenkinsUrl: 'https://jenkins-old.bbh.com',
      folder: 'DevSecOps/PAY',
      name: 'payments-api-sast',
      branch: 'release',
      scriptPath: 'ci/Jenkinsfile',
      credentialsId: 'bb-reader',
    });
    wizard()['job'].controls.jenkinsUrl.setValue('https://jenkins.bbh.com');
    await next();

    expect(step()).toBe(4);
    expect(review()['Jenkins job']).toBe(
      'OneView creates the Pipeline job DevSecOps/PAY/payments-api-sast in https://jenkins.bbh.com; every change of the sources starts it',
    );
    expect(review()['Taken from the existing job']).toContain('2 build parameters (TARGET, TOKEN)');

    await next();
    const saved = await saveProduct();
    expect(saved.body).toMatchObject({ name: 'Payments', code: 'PAY', ownerTeam: 'Team Payments' });
    expect(saved.body.services[0]).toMatchObject({ name: 'payments-api' });
    const job = http.expectOne('/api/pipelines/100/jenkins-job');
    expect(job.request.body).toMatchObject({
      jenkinsUrl: 'https://jenkins.bbh.com',
      folder: 'DevSecOps/PAY',
      name: 'payments-api-sast',
      kind: 'PIPELINE',
      repositoryUrl: REPOSITORY,
      branch: 'release',
      scriptPath: 'ci/Jenkinsfile',
      credentialsId: 'bb-reader',
      description: 'Payments build',
      daysToKeep: 30,
      concurrentBuildsDisabled: true,
      timerSchedule: 'H 2 * * *',
    });
    expect(
      job.request.body.parameters.map((parameter: { name: string }) => parameter.name),
    ).toEqual(['TARGET', 'TOKEN']);
    job.flush(createdJenkinsJob());
    await fixture.whenStable();

    expect(text(page().querySelector('[data-testid=job-created]'))).toContain(
      'OneView created the Jenkins job DevSecOps/PAY/payments-api-security',
    );
    expect(wizard()['jenkinsfile']()).toContain("pipelineKey: 'the-key'");
    expect(wizard().hasUnsavedChanges()).toBe(false);
  });

  it('keeps the saved pipeline when Jenkins refuses the job, and tries the job again', async () => {
    await open();
    await basics();
    wizard()['sources'].controls.repositoryUrl.setValue(REPOSITORY);
    wizard()['sources'].controls.appScanId.setValue('app-1');
    await next();
    await next();
    await suggestCode();
    wizard()['job'].controls.jenkinsUrl.setValue('https://jenkins.bbh.com');
    await next();
    await next();
    await saveProduct();
    http
      .expectOne('/api/pipelines/100/jenkins-job')
      .flush(
        { detail: 'Jenkins refused the generic user svc-oneview (HTTP 403).' },
        { status: 503, statusText: 'Unavailable' },
      );
    await fixture.whenStable();

    expect(text(page())).toContain('The Jenkins job was not created.');
    expect(text(page())).toContain('Jenkins refused the generic user svc-oneview (HTTP 403).');
    expect(text(page())).toContain('The pipeline is set up');

    buttonOf(page(), 'Try again').click();
    http.expectOne('/api/pipelines/100/jenkins-job').flush(createdJenkinsJob());
    await fixture.whenStable();

    expect(page().querySelector('[data-testid=job-created]')).not.toBeNull();
    expect(text(page())).not.toContain('The Jenkins job was not created.');
  });

  it('needs the address of Jenkins, a job name and the repository before OneView creates the job', async () => {
    await open();
    await basics();
    wizard()['sources'].controls.appScanId.setValue('app-1');
    await next();
    await next();
    await suggestCode();
    wizard()['job'].controls.jenkinsUrl.setValue('https://jenkins.bbh.com');
    await next();
    expect(step()).toBe(3);

    wizard()['chosenJobMode'].set('manual');
    await next();
    expect(step()).toBe(4);
  });

  for (const [why, automation] of [
    ['the generic user is not set up', noJenkinsAutomation()],
    ['the settings of the automation cannot be read', null],
  ] as const) {
    it(`leaves the Jenkins job to the user when ${why}`, async () => {
      await open(automation);
      await basics();
      wizard()['sources'].controls.appScanId.setValue('app-1');
      await next();
      await next();
      await suggestCode();

      expect(wizard()['canAutomate']()).toBe(false);
      expect(wizard()['jobMode']()).toBe('manual');
      expect(wizard()['jobModes']()[0].description).toContain('Not available yet');
      await next();

      expect(review()['Jenkins job']).toBe(
        'You create it yourself; OneView shows the Jenkinsfile and the settings afterwards',
      );
      expect(review()['Repository']).toBe('Not given');
      await next();
      await saveProduct();

      expect(page().querySelector('[data-testid=job-created]')).toBeNull();
      expect(wizard()['jenkinsfile']()).toContain('devSecOpsSASTScanningPipeline');
      expect(text(page())).toContain('The pipeline is set up');
    });
  }

  it('starts over with empty steps', async () => {
    await open(noJenkinsAutomation());
    await basics();
    wizard()['sources'].controls.appScanId.setValue('app-1');
    await next();
    await next();
    await suggestCode();
    await next();
    await next();
    await saveProduct();

    buttonOf(page(), 'Create another pipeline').click();
    fixture.detectChanges();
    http.expectOne('/api/products').flush([productSummary()]);
    await fixture.whenStable();

    expect(step()).toBe(0);
    expect(wizard()['basics'].controls.serviceName.value).toBe('');
    expect(wizard()['pipeline']()).toBeNull();
  });
});
