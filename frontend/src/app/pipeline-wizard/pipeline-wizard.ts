import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable, catchError, finalize, map, of, switchMap } from 'rxjs';
import { DepartmentsApi } from '@common/core/api';
import { OneViewContext } from '@common/context/oneview-context';
import { errorMessage, fieldProblems } from '@common/core/errors';
import { Notifier } from '@common/core/notifier';
import { HasUnsavedChanges } from '@common/core/unsaved-changes';
import { commaItems, filled, joinWords, max, text } from '@common/shared/form-controls';
import { errorText } from '@common/shared/form-errors';
import { FORM_FIELD } from '@common/ui/form-field';
import { OvLoading } from '@common/ui/loading';
import {
  JenkinsAutomationApi,
  PipelineSetupApi,
  PipelinesApi,
  ProductsApi,
  ServiceTemplateApi,
  SettingsApi,
} from '../core/api';
import {
  BuildTool,
  CreatedJenkinsJob,
  DeployTarget,
  Department,
  JenkinsJobSnapshot,
  JobKind,
  Pipeline,
  Product,
  SourceInspection,
} from '../core/models';
import {
  ADMIN_JENKINS,
  PIPELINE_WIZARD,
  SELF_SERVICE,
  monitoringProduct,
  pipelinePage,
} from '../core/sections';
import { jenkinsfile } from '../products/jenkinsfile';
import {
  PIPELINES,
  PRODUCT_MODES,
  ProductMode,
  TARGETS,
  WizardPipeline,
  deploys,
  fromService,
  pipelineLabel,
  problemText,
  productRequest,
} from '../self-service/self-service-model';
import { DEFAULT_BUILD_TOOLS, agentLabels } from '../shared/build-options';
import { Choice, ChoiceTiles } from '../shared/choice-tiles';
import {
  DATABASE_TOOLS,
  JOB_KINDS,
  STEPS,
  agentsFrom,
  carriedOver,
  detectedTarget,
  detectedTool,
  jobFindings,
  jobRequest,
  notInSources,
  proposedFolder,
  proposedJobName,
  sourceFindings,
  wizardService,
} from './pipeline-wizard-model';

type JobMode = 'auto' | 'manual';

interface Done {
  product: Product;
  pipeline: Pipeline | null;
  job: CreatedJenkinsJob | null;
  jobError: string | null;
}

const REPOSITORY = /^(https?|ssh):\/\/\S+$/;
const JENKINS_URL = /^https?:\/\/[^\s$`"\\]+$/;
const rule = (pattern: RegExp, message: string) => (control: { value: unknown }) => {
  const value = String(control.value ?? '').trim();
  return !value || pattern.test(value) ? null : { rule: message };
};

/**
 * The guided set-up of one pipeline: the basics, the sources OneView reads, the Jenkins build it may
 * read, the Jenkins job it creates, and a review. Reading and creating can each be skipped.
 */
@Component({
  selector: 'ov-pipeline-wizard',
  imports: [ReactiveFormsModule, RouterLink, ClipboardModule, FORM_FIELD, OvLoading, ChoiceTiles],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pipeline-wizard.html',
  styleUrls: ['../../common/shared/wizard.scss', './pipeline-wizard.scss'],
})
export class PipelineWizard implements HasUnsavedChanges {
  private readonly productsApi = inject(ProductsApi);
  private readonly pipelinesApi = inject(PipelinesApi);
  private readonly setupApi = inject(PipelineSetupApi);
  private readonly notifier = inject(Notifier);
  private readonly context = inject(OneViewContext);

  protected readonly section = PIPELINE_WIZARD;
  protected readonly selfService = SELF_SERVICE;
  protected readonly adminJenkins = ADMIN_JENKINS;
  protected readonly steps = STEPS;
  protected readonly modes = PRODUCT_MODES;
  protected readonly pipelines = PIPELINES;
  protected readonly targets = TARGETS;
  protected readonly tools = DEFAULT_BUILD_TOOLS;
  protected readonly jobKinds = JOB_KINDS;
  protected readonly errorText = errorText;
  protected readonly pipelinePage = pipelinePage;
  protected readonly monitoringProduct = monitoringProduct;

  protected readonly step = signal(0);
  protected readonly checked = signal(false);
  protected readonly mode = signal<ProductMode>('new');
  protected readonly pipeline = signal<WizardPipeline | null>(null);
  protected readonly target = signal<DeployTarget>('VM');

  protected readonly inspecting = signal(false);
  protected readonly inspection = signal<SourceInspection | null>(null);
  protected readonly inspectError = signal<string | null>(null);
  protected readonly reading = signal(false);
  protected readonly existingJob = signal<JenkinsJobSnapshot | null>(null);
  protected readonly readError = signal<string | null>(null);
  protected readonly jobKind = signal<JobKind>('PIPELINE');
  protected readonly chosenJobMode = signal<JobMode>('auto');

  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly saveProblems = signal<string[]>([]);
  protected readonly done = signal<Done | null>(null);

  protected readonly departments = toSignal(
    inject(DepartmentsApi)
      .list<Department>()
      .pipe(catchError(() => of([]))),
    { initialValue: [] },
  );
  protected readonly catalogue = rxResource({ stream: () => this.productsApi.list() });
  private readonly settings = toSignal(
    inject(SettingsApi)
      .get()
      .pipe(catchError(() => of(null))),
    { initialValue: null },
  );
  private readonly template = toSignal(
    inject(ServiceTemplateApi)
      .get()
      .pipe(catchError(() => of(null))),
    { initialValue: null },
  );
  protected readonly automation = toSignal(
    inject(JenkinsAutomationApi)
      .get()
      .pipe(catchError(() => of(null))),
    { initialValue: null },
  );

  protected readonly basics = new FormGroup({
    departmentId: new FormControl<number | null>(this.context.departmentId(), Validators.required),
    productName: text('', max(200)),
    productId: new FormControl<number | null>(null),
    ownerTeam: text('', max(200)),
    contactEmail: text('', Validators.email, max(320)),
    serviceName: text(
      '',
      filled,
      max(200),
      rule(/^[A-Za-z0-9][A-Za-z0-9._-]*$/, 'Letters, digits and . _ - only, starting with one'),
    ),
  });
  protected readonly sources = new FormGroup({
    repositoryUrl: text(
      '',
      max(1000),
      rule(REPOSITORY, 'Give the http, https or ssh address of the repository'),
    ),
    branch: text('main', max(500)),
    tool: new FormControl<BuildTool>('GRADLE', { nonNullable: true }),
    appScanId: text('', filled, max(200)),
    openShiftProject: text('', max(200)),
    nexusIqApplication: text('', max(200)),
    agents: agentLabels(''),
  });
  protected readonly jobUrl = text(
    '',
    max(2000),
    rule(JENKINS_URL, 'Give the http or https address of the job or of one of its builds'),
  );
  protected readonly job = new FormGroup({
    jenkinsUrl: text('', max(500), rule(JENKINS_URL, 'Give the http or https address of Jenkins')),
    folder: text('', max(500)),
    name: text(
      '',
      max(200),
      rule(
        /^[^?*/\\%!@#$^&|<>[\]:;]+$/,
        'A job name has none of ? * / \\ % ! @ # $ ^ & | < > [ ] : ;',
      ),
    ),
    branch: text('main', max(500)),
    scriptPath: text('Jenkinsfile', max(500)),
    credentialsId: text('', max(200)),
  });

  private readonly departmentId = toSignal(this.basics.controls.departmentId.valueChanges, {
    initialValue: this.basics.controls.departmentId.value,
  });
  private readonly productId = toSignal(this.basics.controls.productId.valueChanges, {
    initialValue: null,
  });
  protected readonly choosable = computed(() =>
    (this.catalogue.hasValue() ? this.catalogue.value() : []).filter(
      (product) => product.departmentId === this.departmentId(),
    ),
  );
  protected readonly existing = rxResource({
    params: () => (this.mode() === 'existing' ? (this.productId() ?? undefined) : undefined),
    stream: ({ params: id }) => this.productsApi.get(id),
  });

  /** OneView can create the job only once an administrator has set up the generic user. */
  protected readonly canAutomate = computed(() => this.automation()?.ready === true);
  protected readonly jobMode = computed<JobMode>(() =>
    this.canAutomate() ? this.chosenJobMode() : 'manual',
  );
  protected readonly jobModes = computed<readonly Choice<JobMode>[]>(() => [
    {
      value: 'auto',
      label: 'OneView creates the Jenkins job',
      description: this.canAutomate()
        ? `As the generic user ${this.automation()?.genericUser}, with a trigger on every change of the sources`
        : 'Not available yet: an administrator has to set up the generic user first',
    },
    {
      value: 'manual',
      label: 'I create the Jenkins job myself',
      description: 'OneView gives you the Jenkinsfile and the settings of the job',
    },
  ]);
  protected readonly missing = computed(
    () => this.automation()?.missing.join(', ') ?? 'the Jenkins automation',
  );

  protected readonly sourceFindings = computed(() => {
    const inspection = this.inspection();
    return inspection ? sourceFindings(inspection) : [];
  });
  protected readonly jobFindings = computed(() => {
    const job = this.existingJob();
    return job ? jobFindings(job) : [];
  });
  protected readonly stillNeeded = computed(() =>
    notInSources(this.pipeline() ?? 'SECURITY', this.deploys() ? this.target() : null),
  );
  protected readonly databaseOnly = computed(() => {
    const tool = detectedTool(this.inspection());
    return tool !== null && DATABASE_TOOLS.includes(tool);
  });
  protected readonly deploys = computed(() => {
    const pipeline = this.pipeline();
    return pipeline !== null && deploys(pipeline);
  });
  protected readonly carried = computed(() =>
    this.jobMode() === 'auto' ? carriedOver(this.existingJob()) : [],
  );
  protected readonly pipelineName = computed(() => {
    const pipeline = this.pipeline();
    return pipeline ? pipelineLabel(pipeline) : '';
  });
  protected readonly review = computed(() => this.reviewLines());
  protected readonly jenkinsfile = computed(() => {
    const done = this.done();
    return (
      done?.job?.jenkinsfile ??
      (done?.pipeline ? jenkinsfile([done.pipeline], this.settings()?.platform.jenkinsLibrary) : '')
    );
  });
  protected readonly nextLabel = computed(() =>
    this.step() < 4 ? `Next: ${STEPS[this.step() + 1]}` : 'Create the pipeline',
  );

  constructor() {
    effect(() => {
      const labels = this.template()?.agentLabels;
      const agents = this.sources.controls.agents;
      if (labels?.length && !agents.dirty && !agents.value) {
        agents.setValue(joinWords(labels, ', '));
      }
    });
    effect(() => {
      const credentials = this.template()?.bitbucketCredentialsId;
      const control = this.job.controls.credentialsId;
      if (credentials && !control.dirty && !control.value) {
        control.setValue(credentials);
      }
    });
    effect(() => {
      const url = this.settings()?.platform.jenkinsUrl;
      const control = this.job.controls.jenkinsUrl;
      if (url && !control.dirty && !control.value) {
        control.setValue(url);
      }
    });
  }

  hasUnsavedChanges(): boolean {
    return !this.done() && (this.basics.dirty || this.sources.dirty || this.job.dirty);
  }

  protected canRevisit(index: number): boolean {
    return index < this.step() && !this.done() && !this.saving();
  }

  protected goTo(index: number): void {
    if (this.canRevisit(index)) {
      this.checked.set(false);
      this.step.set(index);
    }
  }

  protected back(): void {
    this.goTo(this.step() - 1);
  }

  protected next(): void {
    this.checked.set(true);
    if (!this.ready()) {
      return;
    }
    if (this.step() === 4) {
      this.create();
      return;
    }
    if (this.step() === 2) {
      this.proposeJob();
    }
    this.checked.set(false);
    this.step.update((step) => step + 1);
  }

  protected chooseMode(mode: ProductMode): void {
    this.mode.set(mode);
    if (mode === 'new') {
      this.basics.controls.productId.setValue(null);
    }
  }

  /** Reads the sources of the repository. Changes nothing, in OneView or in Bitbucket. */
  protected detect(): void {
    const { repositoryUrl, branch } = this.sources.controls;
    repositoryUrl.markAsTouched();
    if (!repositoryUrl.value.trim() || repositoryUrl.invalid || this.inspecting()) {
      this.inspectError.set(
        repositoryUrl.value.trim() ? null : 'Give the address of the repository first.',
      );
      return;
    }
    this.inspecting.set(true);
    this.inspectError.set(null);
    this.setupApi
      .inspectSources(repositoryUrl.value.trim(), branch.value.trim() || null)
      .pipe(finalize(() => this.inspecting.set(false)))
      .subscribe({
        next: (inspection) => this.useInspection(inspection),
        error: (error) => {
          this.inspection.set(null);
          this.inspectError.set(errorMessage(error));
        },
      });
  }

  /** Reads the existing Jenkins job and its build. Changes nothing in Jenkins. */
  protected readJob(): void {
    this.jobUrl.markAsTouched();
    if (!this.jobUrl.value.trim() || this.jobUrl.invalid || this.reading()) {
      return;
    }
    this.reading.set(true);
    this.readError.set(null);
    this.setupApi
      .readJenkinsJob(this.jobUrl.value.trim())
      .pipe(finalize(() => this.reading.set(false)))
      .subscribe({
        next: (job) => this.useJob(job),
        error: (error) => {
          this.existingJob.set(null);
          this.readError.set(errorMessage(error));
        },
      });
  }

  protected forgetJob(): void {
    this.existingJob.set(null);
    this.readError.set(null);
    this.jobUrl.reset('');
  }

  protected copied(): void {
    this.notifier.success('Jenkinsfile copied to the clipboard');
  }

  /** Tries the Jenkins job again after the pipeline was saved and only the job failed. */
  protected retryJob(): void {
    const done = this.done();
    if (!done?.pipeline || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.createJob(done.pipeline)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe((result) => this.done.set({ ...done, ...result }));
  }

  protected restart(): void {
    this.done.set(null);
    this.pipeline.set(null);
    this.mode.set('new');
    this.target.set('VM');
    this.inspection.set(null);
    this.inspectError.set(null);
    this.existingJob.set(null);
    this.readError.set(null);
    this.jobKind.set('PIPELINE');
    this.chosenJobMode.set('auto');
    this.saveError.set(null);
    this.saveProblems.set([]);
    this.checked.set(false);
    this.basics.reset({ departmentId: this.context.departmentId() });
    this.sources.reset({
      branch: 'main',
      tool: 'GRADLE',
      agents: joinWords(this.template()?.agentLabels, ', '),
    });
    this.jobUrl.reset('');
    this.job.reset({
      jenkinsUrl: this.settings()?.platform.jenkinsUrl ?? '',
      branch: 'main',
      scriptPath: 'Jenkinsfile',
      credentialsId: this.template()?.bitbucketCredentialsId ?? '',
    });
    this.catalogue.reload();
    this.step.set(0);
  }

  private useInspection(inspection: SourceInspection): void {
    this.inspection.set(inspection);
    const tool = detectedTool(inspection);
    if (tool && !DATABASE_TOOLS.includes(tool)) {
      this.sources.controls.tool.setValue(tool);
    }
    this.target.set(detectedTarget(inspection) ?? this.target());
    this.takeAgents();
    if (inspection.jenkinsfilePath) {
      this.job.controls.scriptPath.setValue(inspection.jenkinsfilePath);
    }
    this.job.controls.branch.setValue(inspection.branch);
  }

  private useJob(job: JenkinsJobSnapshot): void {
    this.existingJob.set(job);
    this.takeAgents();
    const { jenkinsUrl, branch, scriptPath, credentialsId } = this.job.controls;
    jenkinsUrl.setValue(job.jenkinsUrl);
    if (job.branch) {
      branch.setValue(job.branch);
    }
    if (job.scriptPath) {
      scriptPath.setValue(job.scriptPath);
    }
    if (job.credentialsId) {
      credentialsId.setValue(job.credentialsId);
    }
    if (job.kind !== 'OTHER') {
      this.jobKind.set(job.kind);
    }
    const repository = this.sources.controls.repositoryUrl;
    if (!repository.value.trim() && job.repositoryUrl) {
      repository.setValue(job.repositoryUrl);
    }
  }

  /** The agents the job or the Jenkinsfile names win over the default, until the user types their own. */
  private takeAgents(): void {
    const found = agentsFrom(this.existingJob(), this.inspection());
    const agents = this.sources.controls.agents;
    if (found.length && !agents.dirty) {
      agents.setValue(found.join(', '));
    }
  }

  /** Proposes the folder of the product and the name of the job, until the user types their own. */
  private proposeJob(): void {
    const { folder, name } = this.job.controls;
    if (!name.dirty) {
      name.setValue(proposedJobName(this.basics.controls.serviceName.value, this.pipeline()!));
    }
    if (folder.dirty) {
      return;
    }
    const code: Observable<string> =
      this.mode() === 'existing' && this.existing.hasValue()
        ? of(this.existing.value().code)
        : this.productsApi
            .suggestCode(this.basics.controls.productName.value.trim())
            .pipe(catchError(() => of('')));
    code.subscribe((value) => {
      if (!folder.dirty) {
        folder.setValue(proposedFolder(value));
      }
    });
  }

  private ready(): boolean {
    const basics = this.basics.controls;
    const sources = this.sources.controls;
    switch (this.step()) {
      case 0:
        this.basics.markAllAsTouched();
        return (
          this.pipeline() !== null &&
          basics.departmentId.valid &&
          basics.serviceName.valid &&
          !this.serviceTaken() &&
          (this.mode() === 'new'
            ? basics.productName.value.trim() !== '' &&
              basics.productName.valid &&
              basics.ownerTeam.valid &&
              basics.contactEmail.valid
            : this.existing.hasValue())
        );
      case 1:
        this.sources.markAllAsTouched();
        return (
          this.sources.valid &&
          !this.databaseOnly() &&
          (this.pipeline() !== 'NEXUS_IQ' ||
            (sources.nexusIqApplication.value.trim() !== '' &&
              sources.repositoryUrl.value.trim() !== '')) &&
          (!this.deploys() ||
            this.target() !== 'OPENSHIFT' ||
            sources.openShiftProject.value.trim() !== '')
        );
      case 2:
        return this.jobUrl.valid && !this.reading();
      case 3:
        this.job.markAllAsTouched();
        return (
          this.jobMode() === 'manual' ||
          (this.job.valid &&
            this.job.controls.jenkinsUrl.value.trim() !== '' &&
            this.job.controls.name.value.trim() !== '' &&
            sources.repositoryUrl.value.trim() !== '')
        );
      default:
        return !this.saving();
    }
  }

  protected serviceTaken(): boolean {
    const name = this.basics.controls.serviceName.value.trim().toLowerCase();
    return (
      this.mode() === 'existing' &&
      this.existing.hasValue() &&
      this.existing.value().services.some((service) => service.name.toLowerCase() === name)
    );
  }

  private reviewLines(): { label: string; value: string }[] {
    const basics = this.basics.getRawValue();
    const sources = this.sources.getRawValue();
    const job = this.job.getRawValue();
    const lines = [
      {
        label: 'Product',
        value:
          this.mode() === 'new'
            ? `${basics.productName.trim()} (new)`
            : this.existing.hasValue()
              ? this.existing.value().name
              : '',
      },
      { label: 'Service', value: basics.serviceName.trim() },
      { label: 'Pipeline', value: this.pipelineName() },
      {
        label: 'Build tool',
        value: this.tools.find((tool) => tool.value === sources.tool)?.label ?? sources.tool,
      },
      { label: 'AppScan application ID', value: sources.appScanId.trim() },
      { label: 'Jenkins agents', value: commaItems(sources.agents).join(', ') },
      { label: 'Repository', value: sources.repositoryUrl.trim() || 'Not given' },
    ];
    if (this.deploys()) {
      lines.push({
        label: 'Runs on',
        value:
          this.target() === 'OPENSHIFT'
            ? `OpenShift, project ${sources.openShiftProject.trim()}`
            : 'Virtual machines',
      });
    }
    if (this.pipeline() === 'NEXUS_IQ') {
      lines.push({ label: 'Nexus IQ application', value: sources.nexusIqApplication.trim() });
    }
    lines.push({
      label: 'Jenkins job',
      value:
        this.jobMode() === 'auto'
          ? `OneView creates the ${this.jobKind() === 'MULTIBRANCH' ? 'Multibranch Pipeline' : 'Pipeline'} job ${[job.folder.trim(), job.name.trim()].filter(Boolean).join('/')} in ${job.jenkinsUrl.trim()}; every change of the sources starts it`
          : 'You create it yourself; OneView shows the Jenkinsfile and the settings afterwards',
    });
    if (this.carried().length) {
      lines.push({ label: 'Taken from the existing job', value: this.carried().join(', ') });
    }
    return lines;
  }

  private create(): void {
    const pipeline = this.pipeline()!;
    const basics = this.basics.getRawValue();
    const sources = this.sources.getRawValue();
    const service = wizardService(
      {
        serviceName: basics.serviceName,
        appScanId: sources.appScanId,
        tool: sources.tool,
        target: this.target(),
        openShiftProject: sources.openShiftProject,
        nexusIqApplication: sources.nexusIqApplication,
        repositoryUrl: sources.repositoryUrl,
      },
      pipeline,
    );
    const agents = commaItems(sources.agents);
    const defaults = this.settings()?.serviceDefaults ?? null;
    const stored = this.mode() === 'existing' ? this.existing.value()! : null;
    this.saving.set(true);
    this.saveError.set(null);
    this.saveProblems.set([]);
    const services = stored ? [...stored.services.map(fromService), service] : [service];
    const saved: Observable<Product> = stored
      ? this.productsApi.update(
          stored.id,
          productRequest(stored, services, pipeline, null, this.template(), defaults, agents),
          pipeline,
        )
      : this.productsApi.suggestCode(basics.productName.trim()).pipe(
          catchError(() => of('')),
          switchMap((code) =>
            this.productsApi.create(
              productRequest(
                {
                  departmentId: basics.departmentId,
                  name: basics.productName,
                  code,
                  ownerTeam: basics.ownerTeam,
                  contactEmail: basics.contactEmail,
                },
                services,
                pipeline,
                null,
                this.template(),
                defaults,
                agents,
              ),
              pipeline,
            ),
          ),
        );
    saved
      .pipe(
        switchMap((product) =>
          this.pipelinesApi.listForProduct(product.id).pipe(
            catchError(() => of([])),
            map(
              (list) =>
                list
                  .find((entry) => entry.serviceName === service.name)
                  ?.pipelines.find((candidate) => candidate.type === pipeline) ?? null,
            ),
            switchMap((created) =>
              (created && this.jobMode() === 'auto'
                ? this.createJob(created)
                : of({ job: null, jobError: null })
              ).pipe(map((result) => ({ product, pipeline: created, ...result }))),
            ),
          ),
        ),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: (done) => {
          this.done.set(done);
          this.step.set(STEPS.length);
        },
        error: (error) => {
          this.saveError.set(errorMessage(error));
          this.saveProblems.set(
            fieldProblems(error).map((problem) => problemText(problem, services)),
          );
        },
      });
  }

  /** Creates the Jenkins job; a failure is a result, because the pipeline itself is already saved. */
  private createJob(pipeline: Pipeline): Observable<Pick<Done, 'job' | 'jobError'>> {
    const form = this.job.getRawValue();
    return this.setupApi
      .createJenkinsJob(
        pipeline.id,
        jobRequest(
          { ...form, kind: this.jobKind() },
          this.sources.controls.repositoryUrl.value,
          this.existingJob(),
          `${pipeline.serviceName}: ${this.pipelineName()} pipeline of the DevSecOps library, created by ITSS COE OneView`,
        ),
      )
      .pipe(
        map((job) => ({ job, jobError: null })),
        catchError((error) => of({ job: null, jobError: errorMessage(error) })),
      );
  }
}
