import {
  BuildTool,
  CreateJenkinsJobRequest,
  DeployTarget,
  JenkinsJobSnapshot,
  JenkinsfileFacts,
  JobKind,
  SourceInspection,
  pipelineTypeSlug,
} from '../core/models';
import { TOOL_LABELS } from '../shared/build-options';
import { Choice } from '../shared/choice-tiles';
import { WizardPipeline, WizardService, deploys } from '../self-service/self-service-model';

export const STEPS = ['Basics', 'Sources', 'Existing build', 'Jenkins job', 'Review'] as const;

/** One line of what OneView read: what it is, its value and where the value comes from. */
export interface Finding {
  label: string;
  value: string;
  source: string;
}

export const JOB_KINDS: readonly Choice<JobKind>[] = [
  {
    value: 'PIPELINE',
    label: 'Pipeline',
    description: 'Builds one branch of the repository. The usual choice.',
  },
  {
    value: 'MULTIBRANCH',
    label: 'Multibranch Pipeline',
    description: 'Builds every branch of the repository that has a Jenkinsfile.',
  },
];

/** The build tools the guided set-up can start a service with; a database service needs Self-service. */
export const DATABASE_TOOLS: readonly BuildTool[] = ['LIQUIBASE', 'SQL'];

const known = <T extends string>(value: string | null | undefined, labels: Record<T, string>) =>
  value && value in labels ? (value as T) : null;

/** The build tool the sources show, when OneView knows it. */
export function detectedTool(inspection: SourceInspection | null): BuildTool | null {
  return known<BuildTool>(inspection?.stack.buildTool, TOOL_LABELS);
}

export function detectedTarget(inspection: SourceInspection | null): DeployTarget | null {
  const target = inspection?.stack.deployTarget;
  return target === 'VM' || target === 'OPENSHIFT' ? target : null;
}

const SETTING_LABELS: Record<string, string> = {
  buildTool: 'Build tool',
  'build.javaVersion': 'Java version',
  'build.gradle.version': 'Gradle version',
  'build.tasks': 'Build tasks',
  'tests.unitTests.tasks': 'Unit test tasks',
  deployTarget: 'Runs on',
  'tools.sonar.projectKey': 'SonarQube project key',
  'tools.sonar.sources': 'SonarQube sources',
  'tools.sonar.tests': 'SonarQube tests',
};

function settingValue(field: string, value: string): string {
  if (field === 'buildTool') {
    return known<BuildTool>(value, TOOL_LABELS) ? TOOL_LABELS[value as BuildTool] : value;
  }
  if (field === 'deployTarget') {
    return value === 'OPENSHIFT' ? 'OpenShift' : value === 'VM' ? 'Virtual machines' : value;
  }
  return value;
}

/**
 * What the sources showed, one line per setting with the reason the detection gives for it, then
 * what the Jenkinsfile of the repository says.
 */
export function sourceFindings(inspection: SourceInspection): Finding[] {
  const { stack } = inspection;
  const findings: Finding[] = (stack.resolvedParameters ?? []).map((parameter) => ({
    label: SETTING_LABELS[parameter.field] ?? parameter.field,
    value: settingValue(parameter.field, parameter.value),
    source: parameter.reason,
  }));
  if (!findings.length) {
    findings.push({
      label: 'Build tool',
      value: 'Not recognised',
      source: stack.detectedMarkers.join(', ') || 'no build file at the root of the repository',
    });
  }
  if (stack.nodeBuildEnabled) {
    findings.push({
      label: 'Node.js build',
      value: `Yes${stack.nodeVersion ? `, Node.js ${stack.nodeVersion}` : ''}`,
      source: 'package.json',
    });
  }
  if (inspection.jenkinsfilePath) {
    findings.push(...jenkinsfileFindings(inspection.jenkinsfile, inspection.jenkinsfilePath));
  }
  return findings;
}

export function jenkinsfileFindings(facts: JenkinsfileFacts | null, source: string): Finding[] {
  if (!facts) {
    return [{ label: 'Jenkinsfile', value: 'Found, but empty', source }];
  }
  const findings: Finding[] = [];
  const add = (label: string, value: string) => {
    if (value) {
      findings.push({ label, value, source });
    }
  };
  add('Shared libraries', facts.libraries.join(', '));
  add('Pipeline it starts', facts.entryPoint ?? 'Not a pipeline of the DevSecOps library');
  add('Pipeline key', facts.hasPipelineKey ? 'It carries a key' : '');
  add('Jenkins agents', facts.agentLabels.join(', '));
  add('Services it names', facts.serviceNames.join(', '));
  add(
    'Triggers',
    facts.triggers.map((trigger) => `${trigger.name} ${trigger.schedule}`).join(', '),
  );
  add('Parameters', facts.parameters.join(', '));
  return findings;
}

/** What the sources cannot tell, so the user has to give it. */
export function notInSources(pipeline: WizardPipeline, target: DeployTarget | null): string[] {
  const missing = ['The AppScan application ID of the service, from the DevSecOps team'];
  if (pipeline === 'NEXUS_IQ') {
    missing.push('The Nexus IQ application of the service');
  }
  if (deploys(pipeline) && target === 'OPENSHIFT') {
    missing.push('The OpenShift project the service runs in');
  }
  missing.push('The Jenkins agents the pipeline may run on');
  return missing;
}

/** What OneView read from the Jenkins job and the build, one line per setting. */
export function jobFindings(job: JenkinsJobSnapshot): Finding[] {
  const source = 'the configuration of the job';
  const findings: Finding[] = [];
  const add = (label: string, value: string | null | undefined, from = source) => {
    if (value) {
      findings.push({ label, value, source: from });
    }
  };
  add(
    'Kind of job',
    job.kind === 'PIPELINE'
      ? 'Pipeline'
      : job.kind === 'MULTIBRANCH'
        ? 'Multibranch Pipeline'
        : 'Not a Pipeline job',
  );
  add('Description', job.description);
  add('Repository', job.repositoryUrl);
  add('Branch', job.branch);
  add('Jenkins credential for the repository', job.credentialsId);
  add('Jenkinsfile', job.scriptPath);
  add('Jenkins agent', job.agentLabel);
  add(
    'Keeps its builds',
    job.retention
      ? [
          job.retention.daysToKeep ? `${job.retention.daysToKeep} days` : '',
          job.retention.buildsToKeep ? `the last ${job.retention.buildsToKeep} builds` : '',
        ]
          .filter(Boolean)
          .join(', ')
      : '',
  );
  add('Builds at the same time', job.concurrentBuildsDisabled ? 'No, one build at a time' : '');
  add(
    'Triggers',
    job.triggers
      .map((trigger) => `${trigger.name}${trigger.schedule ? ` (${trigger.schedule})` : ''}`)
      .join(', '),
  );
  add('Shared libraries of the job', job.libraries.join(', '));
  for (const parameter of job.parameters) {
    add(
      `Parameter ${parameter.name}`,
      parameter.type === 'PASSWORD'
        ? 'Password, its value is not read'
        : parameter.type === 'CHOICE'
          ? parameter.choices.join(', ')
          : (parameter.defaultValue ?? '') || '(no default)',
    );
  }
  if (job.inlineScript) {
    findings.push(...jenkinsfileFindings(job.inlineScript, 'the pipeline script of the job'));
  }
  const build = job.lastBuild;
  if (build) {
    const from = `build #${build.number}`;
    add('Result', build.result, from);
    add('Branch it built', build.branch, from);
    for (const [name, value] of Object.entries(build.parameters)) {
      add(`Ran with ${name}`, value || '(empty)', from);
    }
  }
  return findings;
}

/** The Jenkins agents the existing job and the Jenkinsfile name, in that order. */
export function agentsFrom(
  job: JenkinsJobSnapshot | null,
  inspection: SourceInspection | null,
): string[] {
  const labels = [
    ...(job?.agentLabel ? [job.agentLabel] : []),
    ...(job?.inlineScript?.agentLabels ?? []),
    ...(inspection?.jenkinsfile?.agentLabels ?? []),
  ];
  return [...new Set(labels)];
}

export interface Basics {
  serviceName: string;
  appScanId: string;
  tool: BuildTool;
  target: DeployTarget;
  openShiftProject: string;
  nexusIqApplication: string;
  repositoryUrl: string;
}

/** The new service as Self-service describes one, so both ways of setting up a pipeline save the same. */
export function wizardService(basics: Basics, pipeline: WizardPipeline): WizardService {
  return {
    id: null,
    name: basics.serviceName.trim(),
    description: '',
    appScanId: basics.appScanId.trim(),
    tool: basics.tool,
    target: deploys(pipeline) ? basics.target : null,
    openShiftProject: basics.target === 'OPENSHIFT' ? basics.openShiftProject.trim() : '',
    nexusIqApplication: pipeline === 'NEXUS_IQ' ? basics.nexusIqApplication.trim() : '',
    repositoryUrl: pipeline === 'NEXUS_IQ' ? basics.repositoryUrl.trim() : '',
  };
}

/** The name OneView proposes for the job, after the product, the service and the pipeline. */
export function proposedJobName(serviceName: string, pipeline: WizardPipeline): string {
  return `${serviceName.trim() || 'service'}-${pipelineTypeSlug(pipeline)}`;
}

export function proposedFolder(productCode: string): string {
  return productCode ? `DevSecOps/${productCode}` : 'DevSecOps';
}

export interface JobForm {
  jenkinsUrl: string;
  folder: string;
  name: string;
  kind: JobKind;
  branch: string;
  scriptPath: string;
  credentialsId: string;
}

/** The job to create: what the user chose, plus everything read from the existing job when one was read. */
export function jobRequest(
  form: JobForm,
  repositoryUrl: string,
  existing: JenkinsJobSnapshot | null,
  description: string,
): CreateJenkinsJobRequest {
  return {
    jenkinsUrl: form.jenkinsUrl.trim(),
    folder: form.folder.trim() || null,
    name: form.name.trim(),
    kind: form.kind,
    repositoryUrl: repositoryUrl.trim(),
    branch: form.branch.trim() || null,
    scriptPath: form.scriptPath.trim() || null,
    credentialsId: form.credentialsId.trim() || null,
    description: existing?.description ?? description,
    parameters: existing?.parameters ?? [],
    daysToKeep: existing?.retention?.daysToKeep ?? null,
    buildsToKeep: existing?.retention?.buildsToKeep ?? null,
    concurrentBuildsDisabled: existing?.concurrentBuildsDisabled ?? false,
    timerSchedule: existing?.triggers.find((trigger) => trigger.kind === 'TIMER')?.schedule ?? null,
  };
}

/** What of the existing job the new job takes over, in words, for the review. */
export function carriedOver(existing: JenkinsJobSnapshot | null): string[] {
  if (!existing) {
    return [];
  }
  const carried: string[] = [];
  if (existing.parameters.length) {
    carried.push(
      `${existing.parameters.length} build ${existing.parameters.length === 1 ? 'parameter' : 'parameters'} (${existing.parameters.map((parameter) => parameter.name).join(', ')})`,
    );
  }
  if (existing.retention) {
    carried.push('how long builds are kept');
  }
  if (existing.concurrentBuildsDisabled) {
    carried.push('one build at a time');
  }
  if (existing.triggers.some((trigger) => trigger.kind === 'TIMER')) {
    carried.push('the timed build');
  }
  if (existing.description) {
    carried.push('the description');
  }
  return carried;
}
