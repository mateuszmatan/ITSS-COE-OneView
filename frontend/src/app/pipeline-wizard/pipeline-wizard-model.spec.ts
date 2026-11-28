import { jenkinsJob, sourceInspection } from '../testing/fixtures';
import {
  agentsFrom,
  carriedOver,
  detectedTarget,
  detectedTool,
  jenkinsfileFindings,
  jobFindings,
  jobRequest,
  notInSources,
  proposedFolder,
  proposedJobName,
  sourceFindings,
  wizardService,
} from './pipeline-wizard-model';

const form = {
  jenkinsUrl: ' https://jenkins.bbh.com ',
  folder: ' DevSecOps/PAY ',
  name: ' payments-api-security ',
  kind: 'PIPELINE' as const,
  branch: ' release ',
  scriptPath: '',
  credentialsId: '',
};

describe('what the sources show', () => {
  it('lists every detected setting with its reason, then what the Jenkinsfile says', () => {
    expect(sourceFindings(sourceInspection())).toEqual([
      { label: 'Build tool', value: 'Maven', source: 'pom.xml found' },
      { label: 'Java version', value: '21', source: 'read from the build files' },
      { label: 'Runs on', value: 'OpenShift', source: 'openshift folder found' },
      { label: 'Shared libraries', value: 'DevSecOpsJenkinsLibrary', source: 'Jenkinsfile' },
      { label: 'Pipeline it starts', value: 'devSecOpsPipeline', source: 'Jenkinsfile' },
      { label: 'Jenkins agents', value: 'maven-jdk21', source: 'Jenkinsfile' },
      { label: 'Services it names', value: 'payments-api', source: 'Jenkinsfile' },
    ]);
  });

  it('says so when nothing was recognised, and names a Node.js build', () => {
    const inspection = sourceInspection({ jenkinsfilePath: null, jenkinsfile: null });
    inspection.stack = {
      ...inspection.stack,
      buildTool: null,
      resolvedParameters: [],
      detectedMarkers: [],
      nodeBuildEnabled: true,
      nodeVersion: '22',
    };

    expect(sourceFindings(inspection)).toEqual([
      {
        label: 'Build tool',
        value: 'Not recognised',
        source: 'no build file at the root of the repository',
      },
      { label: 'Node.js build', value: 'Yes, Node.js 22', source: 'package.json' },
    ]);
    expect(detectedTool(inspection)).toBeNull();
  });

  it('knows the build tool and the target only when OneView has them', () => {
    expect(detectedTool(sourceInspection())).toBe('MAVEN');
    expect(detectedTarget(sourceInspection())).toBe('OPENSHIFT');
    expect(detectedTool(null)).toBeNull();
    expect(detectedTarget(null)).toBeNull();
  });

  it('tells an empty Jenkinsfile and one of another pipeline apart', () => {
    expect(jenkinsfileFindings(null, 'Jenkinsfile')).toEqual([
      { label: 'Jenkinsfile', value: 'Found, but empty', source: 'Jenkinsfile' },
    ]);
    expect(
      jenkinsfileFindings(
        {
          libraries: [],
          entryPoint: null,
          pipelineType: null,
          hasPipelineKey: true,
          agentLabels: [],
          serviceNames: [],
          triggers: [{ kind: 'SCM_POLLING', name: 'pollSCM', schedule: 'H/5 * * * *' }],
          parameters: ['TARGET'],
        },
        'Jenkinsfile',
      ).map((finding) => `${finding.label}: ${finding.value}`),
    ).toEqual([
      'Pipeline it starts: Not a pipeline of the DevSecOps library',
      'Pipeline key: It carries a key',
      'Triggers: pollSCM H/5 * * * *',
      'Parameters: TARGET',
    ]);
  });

  it('names what no repository can tell, by pipeline and target', () => {
    expect(notInSources('SECURITY', null)).toEqual([
      'The AppScan application ID of the service, from the DevSecOps team',
      'The Jenkins agents the pipeline may run on',
    ]);
    expect(notInSources('FULL', 'OPENSHIFT')).toContain(
      'The OpenShift project the service runs in',
    );
    expect(notInSources('NEXUS_IQ', null)).toContain('The Nexus IQ application of the service');
  });
});

describe('what an existing Jenkins job shows', () => {
  it('lists the settings of the job and of its build, never the value of a password', () => {
    expect(
      jobFindings(jenkinsJob()).map((finding) => `${finding.label}: ${finding.value}`),
    ).toEqual([
      'Kind of job: Pipeline',
      'Description: Payments build',
      'Repository: https://bitbucket.bbh.com/scm/pay/payments-api.git',
      'Branch: release',
      'Jenkins credential for the repository: bb-reader',
      'Jenkinsfile: ci/Jenkinsfile',
      'Keeps its builds: 30 days',
      'Builds at the same time: No, one build at a time',
      'Triggers: Build periodically (H 2 * * *)',
      'Parameter TARGET: rd',
      'Parameter TOKEN: Password, its value is not read',
      'Result: SUCCESS',
      'Branch it built: release',
      'Ran with TARGET: qc',
    ]);
    expect(jobFindings(jenkinsJob()).at(-1)!.source).toBe('build #42');
  });

  it('names a job that is not a Pipeline job, and a job without a build', () => {
    const findings = jobFindings(
      jenkinsJob({ kind: 'OTHER', lastBuild: null, parameters: [], retention: null, triggers: [] }),
    );

    expect(findings[0].value).toBe('Not a Pipeline job');
    expect(findings.some((finding) => finding.label === 'Result')).toBe(false);
  });

  it('takes the agents of the job first, then of its script, then of the Jenkinsfile', () => {
    const job = jenkinsJob({
      agentLabel: 'linux',
      inlineScript: { ...sourceInspection().jenkinsfile!, agentLabels: ['maven-jdk21', 'docker'] },
    });

    expect(agentsFrom(job, sourceInspection())).toEqual(['linux', 'maven-jdk21', 'docker']);
    expect(agentsFrom(null, null)).toEqual([]);
  });
});

describe('the Jenkins job OneView creates', () => {
  it('is proposed after the service and the pipeline, in the folder of the product', () => {
    expect(proposedJobName(' payments-api ', 'SECURITY')).toBe('payments-api-security');
    expect(proposedJobName('', 'FULL')).toBe('service-full');
    expect(proposedFolder('PAY')).toBe('DevSecOps/PAY');
    expect(proposedFolder('')).toBe('DevSecOps');
  });

  it('takes over what the existing job has: parameters, retention, one build at a time, the timer', () => {
    expect(
      jobRequest(form, ' https://bitbucket.bbh.com/scm/pay/a.git ', jenkinsJob(), 'new'),
    ).toEqual({
      jenkinsUrl: 'https://jenkins.bbh.com',
      folder: 'DevSecOps/PAY',
      name: 'payments-api-security',
      kind: 'PIPELINE',
      repositoryUrl: 'https://bitbucket.bbh.com/scm/pay/a.git',
      branch: 'release',
      scriptPath: null,
      credentialsId: null,
      description: 'Payments build',
      parameters: jenkinsJob().parameters,
      daysToKeep: 30,
      buildsToKeep: null,
      concurrentBuildsDisabled: true,
      timerSchedule: 'H 2 * * *',
    });
    expect(carriedOver(jenkinsJob())).toEqual([
      '2 build parameters (TARGET, TOKEN)',
      'how long builds are kept',
      'one build at a time',
      'the timed build',
      'the description',
    ]);
  });

  it('is a plain job when no existing job was read', () => {
    expect(
      jobRequest({ ...form, folder: ' ' }, 'https://b/scm/p/a.git', null, 'new'),
    ).toMatchObject({
      folder: null,
      description: 'new',
      parameters: [],
      daysToKeep: null,
      buildsToKeep: null,
      concurrentBuildsDisabled: false,
      timerSchedule: null,
    });
    expect(carriedOver(null)).toEqual([]);
    expect(
      carriedOver(jenkinsJob({ parameters: [jenkinsJob().parameters[0]], retention: null })),
    ).toContain('1 build parameter (TARGET)');
  });
});

describe('the service the guided set-up saves', () => {
  const basics = {
    serviceName: ' payments-api ',
    appScanId: ' app-1 ',
    tool: 'MAVEN' as const,
    target: 'OPENSHIFT' as const,
    openShiftProject: ' payments ',
    nexusIqApplication: ' payments-iq ',
    repositoryUrl: ' https://bitbucket.bbh.com/scm/pay/a.git ',
  };

  it('has a target only when its pipeline deploys, and Nexus IQ settings only for that pipeline', () => {
    expect(wizardService(basics, 'FULL')).toMatchObject({
      name: 'payments-api',
      appScanId: 'app-1',
      tool: 'MAVEN',
      target: 'OPENSHIFT',
      openShiftProject: 'payments',
      nexusIqApplication: '',
      repositoryUrl: '',
    });
    expect(wizardService(basics, 'SAST').target).toBeNull();
    expect(wizardService(basics, 'NEXUS_IQ')).toMatchObject({
      nexusIqApplication: 'payments-iq',
      repositoryUrl: 'https://bitbucket.bbh.com/scm/pay/a.git',
    });
  });
});
