import {
  AgileApplication,
  AgileAssessment,
  AgileModel,
  DatabaseDefaults,
  DatabaseSettings,
  DatabaseTargetSettings,
  DecorationFinding,
  Department,
  DoraSummary,
  GlobalSettings,
  CreatedJenkinsJob,
  JenkinsAutomation,
  JenkinsJobSnapshot,
  SourceInspection,
  MonitoringOverview,
  MonitoringStatus,
  Pipeline,
  PipelineHealth,
  PipelineKey,
  NodeBuildSettings,
  PipelineMonitoring,
  PipelineRun,
  PortfolioActivity,
  ProductHealth,
  ProductMonitoring,
  Product,
  ProductSummary,
  PullRequestDecoration,
  QualityApplication,
  QualityDefaults,
  Service,
  ServicePipelines,
  ServiceTemplate,
  ToolCommand,
} from '../core/models';

export function command(overrides: Partial<ToolCommand> = {}): ToolCommand {
  return {
    tasks: [],
    flags: [],
    directory: null,
    mavenHome: null,
    environment: [],
    label: null,
    returnStdout: false,
    ...overrides,
  };
}

export function nodeBuild(overrides: Partial<NodeBuildSettings> = {}): NodeBuildSettings {
  return {
    enabled: false,
    directory: null,
    commands: [],
    installationName: null,
    nodePath: null,
    environment: [],
    registry: null,
    npmrcCredentialsId: null,
    timeoutMinutes: null,
    nexusIqScan: null,
    ...overrides,
  };
}

export function service(overrides: Partial<Service> = {}): Service {
  return {
    id: 10,
    name: 'gui',
    description: 'Angular front end',
    build: {
      tool: 'GRADLE',
      sourceDir: '.',
      javaPath: '/usr/lib/jvm/java-17-openjdk',
      autoSetup: false,
      buildPath: 'build/libs/*.jar',
      command: command({ tasks: ['clean', 'build'], flags: ['--refresh-dependencies'] }),
      liquibase: command(),
    },
    nodeBuild: nodeBuild(),
    unitTests: {
      command: command({ tasks: ['test', 'jacocoTestReport'] }),
      resultPattern: 'build/test-results/test/*.xml',
      rootDir: null,
      reportOutDir: null,
      allowEmptyResults: false,
      coverageReportPath: null,
      cypressEnabled: false,
      cypressCommand: null,
      cypressReportsDir: null,
      liquibase: command(),
    },
    tests: {
      maxParallel: null,
      smokeMaxParallel: 10,
      regressionMaxParallel: null,
      performanceMaxParallel: null,
      smokeRequired: true,
      regressionRequired: true,
      performanceRequired: true,
      smokePollIntervalSec: null,
      regressionPollIntervalSec: null,
      performancePollIntervalSec: null,
    },
    testJobs: [
      {
        stage: 'SMOKE',
        name: 'smoke',
        type: 'LOCAL',
        job: 'CERT/gui-smoke',
        timeoutMinutes: 30,
        parameters: null,
        remoteJenkins: null,
        remoteJenkinsUrl: null,
        credentialsId: null,
        pollIntervalSec: null,
        tokenCredentialsId: null,
        abortTriggeredJob: false,
        overrideTrustAllCertificates: false,
        preventRemoteBuildQueue: false,
        trustAllCertificates: false,
        useCrumbCache: false,
        useJobInfoCache: false,
      },
    ],
    deployment: {
      target: 'VM',
      appName: null,
      artifactName: null,
      baseArtifactName: null,
      nexusDeliveryDisabled: false,
    },
    delivery: command(),
    nexusDelivery: {
      artifacts: [],
      group: null,
      artifact: null,
      repositoryUrl: null,
      allowInsecureProtocol: false,
      pull: command(),
      pullArtifacts: [],
      pullOutputDirectory: null,
      releaseEnabled: null,
      releaseRepositoryUrl: null,
      releaseRepositoryId: null,
      releaseCredentialsId: null,
      nugetSource: null,
      nugetCredentialsId: null,
      nugetReleaseSource: null,
      nugetReleaseCredentialsId: null,
      nugetPackagePattern: null,
    },
    urbanCode: {
      siteName: null,
      deployProcess: null,
      skipWait: false,
      deployWithSnapshot: true,
      updateSnapshotComponents: false,
      includeOnlyDeployVersions: true,
      deployOnlyChanged: false,
      deployDescription: null,
      requestProperties: null,
    },
    urbanCodeApplications: [
      {
        applicationName: 'CERT-GUI',
        order: 1,
        environments: ['DV', 'RD'],
        snapshotName: null,
        siteName: null,
        deployProcess: null,
        skipWait: null,
        deployWithSnapshot: null,
        updateSnapshotComponents: null,
        includeOnlyDeployVersions: null,
        deployOnlyChanged: null,
        deployDescription: null,
        description: null,
        requestProperties: null,
        components: [
          {
            componentName: 'CERT-GUI-app',
            baseDir: 'build/libs',
            fileIncludePatterns: '*.jar',
            fileExcludePatterns: null,
            versionPrefix: null,
            version: null,
            incrementalVersion: true,
            extensions: null,
            charset: null,
            pushDescription: null,
            versionProperties: null,
            versionDescription: null,
          },
        ],
      },
    ],
    sshTargets: {
      QC: {
        host: null,
        user: null,
        deployDir: '/opt/cert/gui',
        deployScript: null,
        versionFile: null,
      },
    },
    openShiftTargets: {},
    appScan: {
      applicationId: '109f44ac-cc06-4ca0-884e-d944904f7019',
      scanName: null,
      includedDirs: [],
      excludedDirs: [],
      compile: true,
      sourceCodeOnly: false,
      useConfigFile: false,
      insecureTls: false,
      clientPath: null,
      compileCommand: command(),
      liquibaseCompileCommand: command(),
      dastEnabled: false,
      dastTargetUrl: null,
      dastPresenceId: null,
    },
    sonar: {
      projectName: 'CertScanner GUI',
      projectKey: 'cert-gui',
      installationName: null,
      credentialsId: null,
      authTokenCredentialsId: null,
      badgeToken: null,
      badgeCredentialsId: null,
      addBadges: false,
      fullBadges: false,
      command: command({ tasks: ['sonarqube'] }),
      serverUrl: null,
      sonarScannerVersion: null,
    },
    nexusIq: { serverUrl: null, credentialsId: null },
    nexusIqApplications: [
      {
        application: 'cert-gui',
        scanPatterns: ['**/build/libs/*.jar'],
        stage: 'build',
        failOnNetworkError: false,
      },
    ],
    scm: {
      repositoryUrl: 'https://bitbucket.bbh.com/projects/CERT/repos/gui',
      credentialsId: 'bitbucket-http-credentials',
      authType: 'BASIC',
      type: null,
      targetBranch: null,
      cloneUrl: null,
      reviewers: [],
      apiUrl: null,
      workspace: null,
      projectKey: null,
      repoSlug: null,
    },
    goldenFix: {
      enabled: null,
      minThreatLevel: null,
      ecosystems: [],
      goldenVersionTypes: [],
      excludeDirs: [],
      verifyMaxAttempts: null,
      verifyTimeoutMinutes: null,
      verifyMavenCommand: null,
      verifyGradleCommand: null,
      verifyNpmCommand: null,
      verifyPipCommand: null,
      verifyPubCommand: null,
      verifyNugetCommand: null,
      commitAuthorName: null,
      commitAuthorEmail: null,
      timeZone: null,
    },
    versionCatalogs: [],
    versionCatalogRepo: null,
    metrics: {
      enabled: true,
      influxProject: 'CERT-gui',
      influxEnv: 'test',
      influxUrl: null,
      influxCredentialsId: null,
    },
    flutter: {
      platform: null,
      modules: [],
      testModules: [],
      testSubmodules: [],
      testSubplugins: [],
      signingPasswordCredentialsId: null,
      prodLicenseCredentialsId: null,
      testLicenseCredentialsId: null,
      deliveryGroup: null,
      deliveryArtifact: null,
      deliveryPlugin: null,
      sonarSources: null,
      sonarTests: null,
      sonarFlutterPlugin: false,
      dartAnalyzeCommand: null,
    },
    ...overrides,
  };
}

export function anotherService(overrides: Partial<Service> = {}): Service {
  const stored = service();
  return service({
    id: 11,
    name: 'api',
    sonar: { ...stored.sonar, projectKey: 'cert-api' },
    metrics: { ...stored.metrics, influxProject: null },
    ...overrides,
  });
}

export function databaseSettings(overrides: Partial<DatabaseSettings> = {}): DatabaseSettings {
  return {
    changelogFile: 'db/changelog-master.xml',
    searchPath: null,
    oracleVersion: null,
    defaultSchema: null,
    liquibaseSchema: null,
    contexts: null,
    labels: null,
    parameters: [],
    requireRollback: null,
    tag: null,
    lockWaitMinutes: null,
    compileCheck: null,
    recompile: null,
    ignoreObjects: [],
    ignoreFiles: [],
    groupId: null,
    artifactId: null,
    nexusDelivery: null,
    mvnPath: null,
    snapshotRepositoryId: null,
    snapshotRepositoryUrl: null,
    releaseRepositoryId: null,
    releaseRepositoryUrl: null,
    testReset: null,
    utplsql: null,
    utplsqlPaths: null,
    utplsqlOwner: null,
    utplsqlSourcePath: null,
    utplsqlTestPath: null,
    utplsqlTimeoutMinutes: null,
    ...overrides,
  };
}

export function databaseTarget(
  overrides: Partial<DatabaseTargetSettings> = {},
): DatabaseTargetSettings {
  return {
    url: null,
    credentialsId: null,
    contexts: null,
    labels: null,
    defaultSchema: null,
    liquibaseSchema: null,
    parameters: [],
    timeoutMinutes: null,
    ...overrides,
  };
}

/** The orders-db service of layout (a): a Liquibase service with database changes. */
export function databaseService(overrides: Partial<Service> = {}): Service {
  const stored = service();
  const appId = '6f0c2a9e-4b1d-4e57-9a43-0d8e2f6b7c10';
  return service({
    id: 12,
    name: 'orders-db',
    description: null,
    build: { ...stored.build, tool: 'LIQUIBASE', buildPath: null, command: command() },
    unitTests: { ...stored.unitTests, command: command(), resultPattern: null },
    testJobs: [],
    urbanCodeApplications: [],
    sshTargets: {},
    appScan: {
      ...stored.appScan,
      applicationId: appId,
      compile: false,
    },
    sonar: { ...stored.sonar, projectName: null, projectKey: 'bbh-orders-db', command: command() },
    nexusIqApplications: [],
    scm: { ...stored.scm, repositoryUrl: 'https://bitbucket.bbh.com/projects/ORD/repos/orders' },
    metrics: { ...stored.metrics, influxProject: null },
    flutter: null,
    database: databaseSettings({
      parameters: ['app_role=orders_app_role'],
      groupId: 'com.bbh.orders',
      utplsql: true,
      utplsqlSourcePath: 'db/code',
      utplsqlTestPath: 'db/test',
    }),
    databaseTargets: {
      TEST: databaseTarget({
        url: 'jdbc:oracle:thin:@//dev-db.bbh.com:1521/ORDERS_CI',
        credentialsId: 'orders-db-test',
      }),
      RD: databaseTarget({
        url: 'jdbc:oracle:thin:@//rd-db.bbh.com:1521/ORDERS_RD',
        credentialsId: 'orders-db-rd',
      }),
      QC: databaseTarget({
        url: 'jdbc:oracle:thin:@tcps://qc-db.bbh.com:2484/ORDERS_QC?ssl_server_dn_match=true',
        credentialsId: 'orders-db-qc',
        parameters: ['app_role=orders_qc_role'],
      }),
    },
    ...overrides,
  });
}

export function product(overrides: Partial<Product> = {}): Product {
  return {
    id: 1,
    version: 3,
    code: 'CERT',
    name: 'CertScanner',
    description: 'TLS certificate scanner',
    ownerTeam: 'Technology Architecture',
    contactEmail: 'arch@bbh.com',
    departmentId: 3,
    updatedAt: '2026-10-04T08:00:00Z',
    services: [service()],
    ...overrides,
  };
}

export function productSummary(overrides: Partial<ProductSummary> = {}): ProductSummary {
  return {
    id: 1,
    code: 'CERT',
    name: 'CertScanner',
    description: 'TLS certificate scanner',
    ownerTeam: 'Technology Architecture',
    departmentId: 3,
    departmentName: 'Corporate Technology',
    serviceCount: 2,
    pipelineCount: 3,
    activePipelineCount: 2,
    updatedAt: new Date().toISOString(),
    ...overrides,
  };
}

export function department(overrides: Partial<Department> = {}): Department {
  return {
    id: 3,
    name: 'Corporate Technology',
    version: 0,
    productCount: 1,
    serviceCount: 2,
    pipelineCount: 3,
    activePipelineCount: 2,
    ...overrides,
  };
}

export function pipeline(overrides: Partial<Pipeline> = {}): Pipeline {
  return {
    id: 100,
    productId: 1,
    productCode: 'CERT',
    productName: 'CertScanner',
    serviceId: 10,
    serviceName: 'gui',
    type: 'FULL',
    entryPoint: 'devSecOpsPipeline',
    agentLabels: ['linux-agent'],
    extendedPipelineJob: null,
    securityPipelineJob: null,
    jenkinsJob: 'DevSecOps/CERT/gui-full',
    jenkinsJobUrl: 'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/',
    description: null,
    enabled: true,
    activeKey: {
      id: 1000,
      value: '6f1c2d3e-0000-4abc-9def-123456789abc',
      hint: '6f1c2d3e…9abc',
      status: 'ACTIVE',
      issuedAt: '2026-10-04T08:00:00Z',
      revokedAt: null,
      revokeReason: null,
      lastUsedAt: null,
    },
    influxProjectTag: 'CERT-gui',
    influxEnv: 'test',
    updatedAt: '2026-10-04T08:00:00Z',
    keys: null,
    version: 3,
    ...overrides,
  };
}

export function serviceTemplate(overrides: Partial<ServiceTemplate> = {}): ServiceTemplate {
  return {
    version: 0,
    updatedAt: '2026-10-08T12:00:00Z',
    agentLabels: ['linux-agent'],
    jenkinsJob: 'DevSecOps/{CODE}/{service}-{type}',
    gradleTasks: 'clean build',
    gradleArtifact: 'build/libs/*.jar',
    gradleScanPattern: '**/build/libs/*.jar',
    mavenTasks: 'clean verify',
    mavenArtifact: 'target/*.jar',
    mavenScanPattern: '**/target/*.jar',
    flutterScanPattern: '**/pubspec.lock',
    deliveryTasks: 'deploy:deploy-file',
    nexusIqApplication: '{code}-{service}',
    repositoryUrl: 'https://bitbucket.bbh.com/projects/{CODE}/repos/{code}-{service}',
    bitbucketCredentialsId: 'bitbucket-http-credentials',
    openShiftProject: '{code}-{service}',
    imageRegistry: 'docker-qc.tools.bbh.com',
    healthCheckUrl: '/actuator/health',
    ...overrides,
  };
}

export function revokedKey(overrides: Partial<PipelineKey> = {}): PipelineKey {
  return {
    id: 999,
    value: null,
    hint: '1a2b3c4d…eeff',
    status: 'REVOKED',
    issuedAt: '2026-09-01T08:00:00Z',
    revokedAt: '2026-10-01T09:30:00Z',
    revokeReason: 'Leaked in a build log',
    lastUsedAt: '2026-09-30T10:00:00Z',
    ...overrides,
  };
}

export function servicePipelines(overrides: Partial<ServicePipelines> = {}): ServicePipelines {
  return {
    serviceId: 10,
    serviceName: 'gui',
    description: 'Angular front end',
    buildTool: 'GRADLE',
    deployTarget: 'VM',
    pipelines: [pipeline()],
    ...overrides,
  };
}

export function globalSettings(overrides: Partial<GlobalSettings> = {}): GlobalSettings {
  const zero = { maxCritical: 0, maxHigh: 0, maxMedium: 0 };
  return {
    version: 4,
    updatedAt: '2026-10-04T08:00:00Z',
    platform: {
      jenkinsUrl: 'https://jenkins.bbh.com',
      jenkinsLibrary: 'DevSecOpsJenkinsLibrary',
      asocUrl: 'https://bbh.cloud.appscan.com',
      appScanClientLinuxUrl: 'https://tools.bbh.com/nexus/SAClientUtil_Linux.zip',
      appScanClientWindowsUrl: 'https://tools.bbh.com/nexus/SAClientUtil_Win.zip',
      proxyHost: 'tstproxy.bbh.com',
      proxyPort: 9090,
      proxyUser: 'PROXY_ASOCJenk',
      oisHost: 'oisapi.bbh.com',
      sonarServerUrl: 'https://tools.bbh.com/sonar',
      sonarInstallationName: 'SonarQube',
      nexusIqServerUrl: 'https://tools.bbh.com/IQ',
      nexusIqCredentialsId: 'nexusiqP',
      nexusSnapshotRepositoryUrl: 'http://tools.bbh.com/nexus/content/repositories/snapshots/',
      nexusSnapshotRepositoryId: 'bbh-snapshots',
      influxWriteUrl: 'http://qcwsecopsmon1.testbbh.com:8086/api/v2/write',
      influxCredentialsId: 'influxdb-token',
      iosBuildAgent: 'mac002.bbh.com',
      influxUrl: null,
      influxOrg: null,
      influxBucket: null,
      influxToken: null,
      grafanaName: null,
      grafanaDashboardUrl: null,
      grafanaSecurityDashboardUrl: null,
      grafana2Name: null,
      grafana2DashboardUrl: null,
      grafana2SecurityDashboardUrl: null,
    },
    deployment: {
      urbanCodeSiteName: 'deploy.bbh.com',
      urbanCodeDeployProcess: 'tomcat-app-process',
      rdHost: 'rdltaapps1.testbbh.com',
      qcHost: 'qcltaapps1.testbbh.com',
      sshUser: 'taadmin',
      deployScript: 'scripts/deployment/zero-downtime-deployment.sh',
      versionFile: 'scripts/deployment/version.properties',
    },
    limits: {
      SAST: { ...zero },
      SCA: { ...zero },
      NEXUS_IQ: { maxCritical: 0, maxHigh: 2, maxMedium: 10 },
      DAST: { ...zero },
    },
    scans: {
      coverageMinLine: 60,
      sastPrepareTimeoutMinutes: 120,
      sastPollTimeoutMinutes: 50,
      sastPollIntervalSeconds: 30,
      dastPollTimeoutMinutes: 60,
      dastPollIntervalSeconds: 60,
      dastReportTimeoutMinutes: 30,
      dastReportIntervalSeconds: 30,
      sonarWaitForQualityGate: true,
      sonarQualityGateTimeoutMinutes: 5,
      parallelStaticAnalysis: true,
      parallelDynamicAnalysis: true,
    },
    releaseGate: {
      scanners: ['SAST', 'SCA', 'NEXUS_IQ', 'DAST'],
      requireCoverage: true,
      stateFile: 'release-gate.json',
    },
    serviceDefaults: {
      buildTool: 'MAVEN',
      deployTarget: 'OPENSHIFT',
      sourceDir: 'app',
      testsMaxParallel: 20,
      nodeInstallationName: 'NodeJS-20',
      npmRegistry: null,
      npmrcCredentialsId: null,
      pluginRepositoriesEnabled: true,
      gradlePluginRepository: 'https://tools.bbh.com/nexus/repository/gradle-plugins/',
      mavenPluginRepository: 'https://tools.bbh.com/nexus/repository/maven-public/',
      releaseEnabled: true,
      releaseRepositoryUrl: null,
      releaseRepositoryId: null,
      releaseCredentialsId: null,
    },
    goldenFix: {
      enabled: true,
      minThreatLevel: 2,
      ecosystems: ['maven', 'npm', 'pypi', 'nuget'],
      goldenVersionTypes: [
        'recommended-non-breaking-with-dependencies',
        'recommended-non-breaking',
      ],
      excludeDirs: [],
      verifyMaxAttempts: 3,
      verifyTimeoutMinutes: 20,
      verifyMavenCommand: null,
      verifyGradleCommand: null,
      verifyNpmCommand: null,
      verifyPipCommand: null,
      verifyPubCommand: null,
      verifyNugetCommand: null,
      commitAuthorName: 'DevSecOps GoldenFix',
      commitAuthorEmail: 'devsecops-goldenfix@noreply.local',
      timeZone: null,
    },
    ...overrides,
  };
}

/** The Library defaults of Liquibase database changes, as the regression tests save them. */
export function databaseDefaults(overrides: Partial<DatabaseDefaults> = {}): DatabaseDefaults {
  return {
    liquibaseHome: '/opt/liquibase/4.33.0',
    liquibaseUrl: null,
    liquibaseSha256: null,
    liquibaseJavaVersion: null,
    oracleJdbcPath: '/opt/oracle/ojdbc11.jar',
    oracleJdbcUrl: null,
    oracleJdbcSha256: null,
    utplsqlHome: null,
    utplsqlUrl:
      'https://tools.bbh.com/nexus/repository/raw-tools/utplsql/utPLSQL-cli/3.2.2/utPLSQL-cli.zip',
    utplsqlSha256: '15a3addd921b7f2e415537b47c18a4a9d3e0b70ff0e403943fd4c03b0ba02006',
    allowedTargets: ['dev-db.bbh.com', 'rd-db.bbh.com', 'qc-db.bbh.com'],
    snapshotRepositoryId: 'bbh-db-snapshots',
    snapshotRepositoryUrl: 'https://tools.bbh.com/nexus/repository/db-snapshots',
    releaseRepositoryId: 'bbh-db-releases',
    releaseRepositoryUrl: 'https://tools.bbh.com/nexus/repository/db-releases',
    ...overrides,
  };
}

export function pipelineRun(overrides: Partial<PipelineRun> = {}): PipelineRun {
  return {
    time: '2026-10-04T07:30:00Z',
    result: 'SUCCESS',
    branch: 'develop',
    build: 42,
    durationSeconds: 900,
    commit: 'a28ef0054e42c0ffee',
    job: 'DevSecOps/CERT/gui-full/develop',
    buildUrl: 'https://jenkins.bbh.com/job/DevSecOps/job/CERT/job/gui-full/job/develop/42/',
    stagesTotal: 12,
    passed: 10,
    notRequired: 0,
    warned: 1,
    failed: 1,
    blocked: 0,
    skipped: 0,
    ...overrides,
  };
}

export function decorationFinding(overrides: Partial<DecorationFinding> = {}): DecorationFinding {
  return {
    tool: 'AppScan SAST',
    severity: 'HIGH',
    file: 'src/main/java/com/bbh/cert/Login.java',
    line: 42,
    title: 'SQL Injection',
    description: 'User input reaches a database query.',
    remediation: 'Use a prepared statement.',
    ...overrides,
  };
}

export function pullRequestDecoration(
  overrides: Partial<PullRequestDecoration> = {},
): PullRequestDecoration {
  return {
    id: 31,
    pipelineId: 100,
    recordedAt: '2026-11-26T09:30:00Z',
    job: 'DevSecOps/CERT/gui-full',
    build: 42,
    branch: 'feature/login',
    commit: 'b815d55',
    service: 'gui',
    kind: 'BRANCH',
    repositoryUrl: 'https://bitbucket.bbh.com/projects/CERT/repos/gui',
    pullRequestId: '12',
    pullRequestUrl: 'https://bitbucket.bbh.com/projects/CERT/repos/gui/pull-requests/12',
    title: 'Add the login page',
    sourceBranch: 'feature/login',
    targetBranch: 'main',
    created: false,
    lineComments: 3,
    generalComments: 2,
    limited: false,
    maxComments: 25,
    findingsTotal: 3,
    summary: '## Security findings of gui',
    findings: [
      decorationFinding(),
      decorationFinding({ line: 77, title: 'Cross-site scripting' }),
      decorationFinding({
        tool: 'Nexus IQ',
        severity: null,
        file: 'build.gradle',
        line: null,
        title: 'org.apache.logging.log4j:log4j-core 2.14.1',
        remediation: 'Upgrade to 2.17.1',
      }),
    ],
    ...overrides,
  };
}

export function doraSummary(overrides: Partial<DoraSummary> = {}): DoraSummary {
  return {
    rangeDays: 30,
    runs: 40,
    deployments: 12,
    deploymentsPerWeek: 2.8,
    deploymentFrequencyLevel: 'HIGH',
    leadTimeMedianSeconds: 5400,
    leadTimeLevel: 'ELITE',
    changeFailureRatePercent: 12.5,
    changeFailureRateLevel: 'HIGH',
    meanTimeToRestoreSeconds: 7200,
    timeToRestoreLevel: 'HIGH',
    restores: 3,
    failingSince: null,
    averageDurationSeconds: 900,
    daily: [
      { date: '2026-10-03', runs: 3, failures: 1, deployments: 1 },
      { date: '2026-10-04', runs: 2, failures: 0, deployments: 2 },
    ],
    ...overrides,
  };
}

export function monitoringPipeline(overrides: Partial<Pipeline> = {}): Pipeline {
  const stored = pipeline();
  return pipeline({
    activeKey: { ...stored.activeKey!, value: null },
    keys: [],
    ...overrides,
  });
}

export function pipelineMonitoring(
  overrides: Partial<PipelineMonitoring> = {},
): PipelineMonitoring {
  return {
    pipeline: monitoringPipeline(),
    status: 'SUCCESS',
    lastRun: pipelineRun(),
    dora: doraSummary(),
    recentRuns: [pipelineRun(), pipelineRun({ build: 41, buildUrl: null, result: 'FAILURE' })],
    grafana: [
      {
        name: 'Grafana',
        dashboardUrl: 'https://grafana.bbh.com/d/adzfc54123/pipeline?var-project=CERT-gui',
      },
    ],
    metricsError: null,
    ...overrides,
  };
}

export function pipelineHealth(overrides: Partial<PipelineHealth> = {}): PipelineHealth {
  return {
    pipeline: monitoringPipeline(),
    status: 'SUCCESS',
    lastRun: pipelineRun(),
    ...overrides,
  };
}

export function productMonitoring(overrides: Partial<ProductMonitoring> = {}): ProductMonitoring {
  return {
    productId: 1,
    code: 'CERT',
    name: 'CertScanner',
    description: 'TLS certificate scanner',
    ownerTeam: 'Technology Architecture',
    overall: 'SUCCESS',
    pipelines: [pipelineHealth()],
    metricsError: null,
    ...overrides,
  };
}

export function productHealth(overrides: Partial<ProductHealth> = {}): ProductHealth {
  return {
    productId: 1,
    code: 'CERT',
    name: 'CertScanner',
    ownerTeam: 'Technology Architecture',
    departmentId: 3,
    serviceCount: 2,
    pipelineCount: 3,
    overall: 'SUCCESS',
    statusCounts: { SUCCESS: 2, FAILURE: 1 },
    lastRunAt: '2026-10-04T07:30:00Z',
    ...overrides,
  };
}

export function monitoringOverview(
  overrides: Partial<MonitoringOverview> = {},
): MonitoringOverview {
  return { products: [productHealth()], metricsError: null, ...overrides };
}

export function portfolioActivity(overrides: Partial<PortfolioActivity> = {}): PortfolioActivity {
  return { pipelines: 3, dora: doraSummary(), metricsError: null, ...overrides };
}

export function monitoringStatus(overrides: Partial<MonitoringStatus> = {}): MonitoringStatus {
  return {
    influxConfigured: true,
    influxReachable: true,
    influxError: null,
    ...overrides,
  };
}

export function qualityDefaults(overrides: Partial<QualityDefaults> = {}): QualityDefaults {
  return {
    targets: { coveragePercent: 80, passRatePercent: 95, maxCriticalDefects: 0 },
    version: null,
    updatedAt: null,
    ...overrides,
  };
}

export function qualityApplication(
  overrides: Partial<QualityApplication> = {},
): QualityApplication {
  return {
    productId: 1,
    code: 'CERT',
    name: 'CertScanner',
    departmentId: 3,
    departmentName: 'Corporate Technology',
    enabled: true,
    qaLead: 'Ann Lee',
    sonarProjectKey: 'com.bbh:cert-scanner',
    defectProjectKey: 'CERT',
    dashboardUrl: 'https://grafana.bbh.com/d/cert-quality',
    targets: { coveragePercent: 85, passRatePercent: null, maxCriticalDefects: null },
    effectiveTargets: { coveragePercent: 85, passRatePercent: 95, maxCriticalDefects: 0 },
    version: 2,
    updatedAt: '2026-11-20T09:00:00Z',
    ...overrides,
  };
}

export function notInQuality(overrides: Partial<QualityApplication> = {}): QualityApplication {
  return qualityApplication({
    productId: 2,
    code: 'PAY',
    name: 'PayHub',
    departmentId: 5,
    departmentName: 'Fund Services',
    enabled: false,
    qaLead: null,
    sonarProjectKey: null,
    defectProjectKey: null,
    dashboardUrl: null,
    targets: { coveragePercent: null, passRatePercent: null, maxCriticalDefects: null },
    effectiveTargets: { coveragePercent: 80, passRatePercent: 95, maxCriticalDefects: 0 },
    version: null,
    updatedAt: null,
    ...overrides,
  });
}

export function agileModel(overrides: Partial<AgileModel> = {}): AgileModel {
  return {
    dimensions: [
      { code: 'TEAM', label: 'Team and roles', description: 'How the team is set up.' },
      { code: 'PLANNING', label: 'Planning and delivery', description: 'How work is planned.' },
      { code: 'ENGINEERING', label: 'Engineering practices', description: 'How code is built.' },
      { code: 'PRODUCT', label: 'Product ownership', description: 'How the backlog is owned.' },
      {
        code: 'IMPROVEMENT',
        label: 'Continuous improvement',
        description: 'How the team improves.',
      },
    ],
    frameworks: [
      { code: 'SCRUM', label: 'Scrum', description: null },
      { code: 'KANBAN', label: 'Kanban', description: null },
      { code: 'SAFE', label: 'SAFe', description: null },
      { code: 'OTHER', label: 'Other', description: null },
    ],
    minLevel: 1,
    maxLevel: 5,
    ...overrides,
  };
}

export function agileAssessment(overrides: Partial<AgileAssessment> = {}): AgileAssessment {
  return {
    id: 11,
    productId: 1,
    assessedOn: '2026-11-02',
    assessor: 'Joe Coach',
    levels: { TEAM: 4, PLANNING: 3, ENGINEERING: 3, PRODUCT: 2, IMPROVEMENT: 4 },
    overallLevel: 3.2,
    notes: 'Retrospectives every sprint.',
    ...overrides,
  };
}

export function agileApplication(overrides: Partial<AgileApplication> = {}): AgileApplication {
  return {
    productId: 1,
    code: 'CERT',
    name: 'CertScanner',
    departmentId: 3,
    departmentName: 'Corporate Technology',
    enabled: true,
    teamName: 'Cert Squad',
    framework: 'SCRUM',
    coach: 'Joe Coach',
    version: 1,
    updatedAt: '2026-11-01T09:00:00Z',
    assessments: [
      agileAssessment(),
      agileAssessment({
        id: 7,
        assessedOn: '2026-08-15',
        levels: { TEAM: 3, PLANNING: 2, ENGINEERING: 2, PRODUCT: 2, IMPROVEMENT: 3 },
        overallLevel: 2.4,
        notes: null,
      }),
    ],
    ...overrides,
  };
}

export function notInAgile(overrides: Partial<AgileApplication> = {}): AgileApplication {
  return agileApplication({
    productId: 2,
    code: 'PAY',
    name: 'PayHub',
    departmentId: 5,
    departmentName: 'Fund Services',
    enabled: false,
    teamName: null,
    framework: null,
    coach: null,
    version: null,
    updatedAt: null,
    assessments: [],
    ...overrides,
  });
}

export function jenkinsAutomation(overrides: Partial<JenkinsAutomation> = {}): JenkinsAutomation {
  return {
    version: 2,
    updatedAt: '2026-11-28T09:00:00Z',
    genericUser: 'svc-oneview',
    oisHost: 'ois-qc.bbh.com',
    oisApiKeySet: true,
    pollSchedule: 'H/5 * * * *',
    libraryRepositoryUrl: null,
    libraryVersion: 'main',
    libraryCredentialsId: null,
    ready: true,
    missing: [],
    ...overrides,
  };
}

export function noJenkinsAutomation(): JenkinsAutomation {
  return jenkinsAutomation({
    version: 0,
    updatedAt: null,
    genericUser: null,
    oisHost: null,
    oisApiKeySet: false,
    ready: false,
    missing: ['the generic user', 'the OIS host', 'the OIS API key'],
  });
}

export function sourceInspection(overrides: Partial<SourceInspection> = {}): SourceInspection {
  return {
    stack: {
      buildTool: 'MAVEN',
      primaryLanguage: 'JAVA',
      framework: null,
      deployTarget: 'OPENSHIFT',
      javaVersion: '21',
      gradleVersion: null,
      nodeBuildEnabled: false,
      nodeVersion: null,
      testRunner: null,
      unitTestTasks: 'test',
      buildTasks: 'clean verify',
      liquibaseDetected: false,
      dockerDetected: true,
      detectedMarkers: ['pom.xml'],
      summaryMessage: 'Discovered stack: MAVEN (JAVA)',
      resolvedParameters: [
        { field: 'buildTool', value: 'MAVEN', reason: 'pom.xml found' },
        { field: 'build.javaVersion', value: '21', reason: 'read from the build files' },
        { field: 'deployTarget', value: 'OPENSHIFT', reason: 'openshift folder found' },
      ],
    },
    branch: 'develop',
    jenkinsfilePath: 'Jenkinsfile',
    jenkinsfile: {
      libraries: ['DevSecOpsJenkinsLibrary'],
      entryPoint: 'devSecOpsPipeline',
      pipelineType: 'FULL',
      hasPipelineKey: false,
      agentLabels: ['maven-jdk21'],
      serviceNames: ['payments-api'],
      triggers: [],
      parameters: [],
    },
    readAsGenericUser: true,
    ...overrides,
  };
}

export function jenkinsJob(overrides: Partial<JenkinsJobSnapshot> = {}): JenkinsJobSnapshot {
  return {
    jenkinsUrl: 'https://jenkins-old.bbh.com',
    jobPath: 'legacy/payments',
    jobUrl: 'https://jenkins-old.bbh.com/job/legacy/job/payments/',
    kind: 'PIPELINE',
    description: 'Payments build',
    repositoryUrl: 'https://bitbucket.bbh.com/scm/pay/payments-api.git',
    branch: 'release',
    credentialsId: 'bb-reader',
    scriptPath: 'ci/Jenkinsfile',
    agentLabel: null,
    parameters: [
      { type: 'STRING', name: 'TARGET', defaultValue: 'rd', description: null, choices: [] },
      { type: 'PASSWORD', name: 'TOKEN', defaultValue: null, description: null, choices: [] },
    ],
    retention: { daysToKeep: 30, buildsToKeep: null },
    concurrentBuildsDisabled: true,
    triggers: [{ kind: 'TIMER', name: 'Build periodically', schedule: 'H 2 * * *' }],
    libraries: [],
    inlineScript: null,
    lastBuild: {
      number: 42,
      result: 'SUCCESS',
      parameters: { TARGET: 'qc' },
      branch: 'release',
      repositoryUrl: null,
    },
    notes: [],
    ...overrides,
  };
}

export function createdJenkinsJob(overrides: Partial<CreatedJenkinsJob> = {}): CreatedJenkinsJob {
  return {
    pipelineId: 100,
    jobUrl: 'https://jenkins.bbh.com/job/DevSecOps/job/PAY/job/payments-api-security/',
    jobPath: 'DevSecOps/PAY/payments-api-security',
    kind: 'PIPELINE',
    trigger:
      'Jenkins polls branch release of the repository on the schedule H/5 * * * * and builds when it changed.',
    jenkinsfilePath: 'ci/Jenkinsfile',
    jenkinsfile:
      "@Library('DevSecOpsJenkinsLibrary') _\n\ndevSecOpsSecurityPipeline(pipelineKey: 'the-key')\n",
    libraryAttached: false,
    ...overrides,
  };
}
