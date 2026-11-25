package com.bbh.itss.coe.oneview.adapter.in.web

import com.bbh.itss.coe.oneview.adapter.in.web.ConfigImportResponse.ImportedPipeline
import com.bbh.itss.coe.oneview.adapter.in.web.ConfigImportResponse.ImportedService
import com.bbh.itss.coe.oneview.adapter.in.web.ServiceDto.MetricsSettingsDto
import com.bbh.itss.coe.oneview.adapter.in.web.ServiceDto.NexusIqApplicationDto
import com.bbh.itss.coe.oneview.adapter.in.web.ServiceDto.NodeBuildSettingsDto
import com.bbh.itss.coe.oneview.adapter.in.web.ServiceDto.SshTargetDto
import com.bbh.itss.coe.oneview.domain.catalog.BuildSettings
import com.bbh.itss.coe.oneview.domain.catalog.BuildTool
import com.bbh.itss.coe.oneview.domain.catalog.DatabaseTarget
import com.bbh.itss.coe.oneview.domain.catalog.DatabaseTargetSettings
import com.bbh.itss.coe.oneview.domain.catalog.ScmSettings
import com.bbh.itss.coe.oneview.domain.catalog.NexusDeliverySettings
import com.bbh.itss.coe.oneview.domain.catalog.ServiceSettings
import com.bbh.itss.coe.oneview.domain.catalog.SonarSettings
import com.bbh.itss.coe.oneview.domain.catalog.NodeBuildSettings
import com.bbh.itss.coe.oneview.domain.catalog.ToolCommand
import com.bbh.itss.coe.oneview.domain.catalog.UnitTestSettings
import com.bbh.itss.coe.oneview.domain.dsoconfig.DsoConfigBuilder
import com.bbh.itss.coe.oneview.domain.settings.GlobalSettingsValues
import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException
import org.yaml.snakeyaml.Yaml
import spock.lang.Shared
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import static com.bbh.itss.coe.oneview.adapter.in.web.ConfigFileParser.LIMITS
import static com.bbh.itss.coe.oneview.adapter.in.web.ConfigFileParser.MAX_LENGTH
import static com.bbh.itss.coe.oneview.adapter.in.web.ConfigFileParser.SELF_REFERENCE
import static com.bbh.itss.coe.oneview.adapter.in.web.ConfigInput.SECRET
import static com.bbh.itss.coe.oneview.adapter.in.web.ConfigInput.UNKNOWN
import static com.bbh.itss.coe.oneview.adapter.in.web.ServiceConfigReader.APPSCAN_KEY
import static com.bbh.itss.coe.oneview.adapter.in.web.ServiceConfigReader.GLOBAL
import static com.bbh.itss.coe.oneview.adapter.in.web.ServiceConfigReader.LONG_PATHS
import static com.bbh.itss.coe.oneview.adapter.in.web.ServiceConfigReader.MAX_PROJECTS
import static com.bbh.itss.coe.oneview.adapter.in.web.ServiceConfigReader.NODE_WITH_DATABASE
import static com.bbh.itss.coe.oneview.adapter.in.web.ServiceConfigReader.NODE_WITH_FLUTTER
import static com.bbh.itss.coe.oneview.adapter.in.web.ServiceConfigReader.NOT_A_DATABASE
import static com.bbh.itss.coe.oneview.adapter.in.web.ServiceConfigReader.NOT_FOR_DATABASE
import static com.bbh.itss.coe.oneview.adapter.in.web.ServiceConfigReader.NO_PRODUCTION
import static com.bbh.itss.coe.oneview.adapter.in.web.ServiceConfigReader.NO_PROJECT
import static com.bbh.itss.coe.oneview.adapter.in.web.ServiceConfigReader.PIPELINE
import static com.bbh.itss.coe.oneview.adapter.in.web.ServiceConfigReader.RUN_TIME
import static com.bbh.itss.coe.oneview.adapter.in.web.ServiceConfigReader.SECRET_IN_URL
import static com.bbh.itss.coe.oneview.adapter.in.web.ServiceConfigReader.TOOLS_IN_DEFAULTS
import static com.bbh.itss.coe.oneview.domain.catalog.BitbucketAuthType.BASIC
import static com.bbh.itss.coe.oneview.domain.catalog.BitbucketAuthType.BEARER
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.FLUTTER
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.GRADLE
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.LIQUIBASE
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.MAVEN
import static com.bbh.itss.coe.oneview.domain.catalog.DeployTarget.OPENSHIFT
import static com.bbh.itss.coe.oneview.domain.catalog.DeployTarget.VM
import static com.bbh.itss.coe.oneview.domain.catalog.NexusIqSettings.ONE_APPLICATION
import static com.bbh.itss.coe.oneview.domain.catalog.Region.QC
import static com.bbh.itss.coe.oneview.domain.catalog.Region.RD
import static com.bbh.itss.coe.oneview.domain.catalog.TestJobType.LOCAL
import static com.bbh.itss.coe.oneview.domain.catalog.TestJobType.REMOTE
import static com.bbh.itss.coe.oneview.domain.catalog.TestStage.PERFORMANCE
import static com.bbh.itss.coe.oneview.domain.catalog.TestStage.REGRESSION
import static com.bbh.itss.coe.oneview.domain.catalog.TestStage.SMOKE
import static com.bbh.itss.coe.oneview.domain.pipeline.PipelineType.EXTENDED
import static com.bbh.itss.coe.oneview.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.coe.oneview.domain.pipeline.PipelineType.NEXUS_IQ
import static com.bbh.itss.coe.oneview.domain.pipeline.PipelineType.SECURITY
import static com.bbh.itss.coe.oneview.support.CatalogFixtures.copy
import static com.bbh.itss.coe.oneview.support.Fixtures.appScan
import static com.bbh.itss.coe.oneview.support.Fixtures.build
import static com.bbh.itss.coe.oneview.support.Fixtures.database
import static com.bbh.itss.coe.oneview.support.Fixtures.databaseDefaults
import static com.bbh.itss.coe.oneview.support.Fixtures.databaseSettings
import static com.bbh.itss.coe.oneview.support.Fixtures.databaseTargets
import static com.bbh.itss.coe.oneview.support.Fixtures.deployment
import static com.bbh.itss.coe.oneview.support.Fixtures.fullCommand
import static com.bbh.itss.coe.oneview.support.Fixtures.fullSettings
import static com.bbh.itss.coe.oneview.support.Fixtures.globalSettings
import static com.bbh.itss.coe.oneview.support.Fixtures.pipeline
import static com.bbh.itss.coe.oneview.support.Fixtures.product
import static com.bbh.itss.coe.oneview.support.Fixtures.settings
import static com.bbh.itss.coe.oneview.support.LibraryConfig.projectsAsRun

class ServiceConfigReaderSpec extends Specification {

    static final ServiceSettings MAVEN_VM = fullSettings()
    static final ServiceSettings GRADLE_OPENSHIFT = gradleOpenShift(false)
    static final ServiceSettings GRADLE_OPENSHIFT_NO_DELIVERY = gradleOpenShift(true)
    static final ServiceSettings FLUTTER_VM = settings(build: build(tool: FLUTTER), flutter: MAVEN_VM.flutter(),
            tests: MAVEN_VM.tests(), testJobs: MAVEN_VM.testJobs(), urbanCode: MAVEN_VM.urbanCode(),
            urbanCodeApplications: MAVEN_VM.urbanCodeApplications(), sshTargets: MAVEN_VM.sshTargets(),
            appScan: MAVEN_VM.appScan(), sonar: MAVEN_VM.sonar(), scm: MAVEN_VM.scm(), metrics: MAVEN_VM.metrics())
    static final ServiceSettings GRADLE_VM = settings(build: build(tool: GRADLE),
            delivery: ToolCommand.builder().flags(['--info']).environment(['GRADLE_OPTS=-Xmx1g']).build(),
            nexusDelivery: NexusDeliverySettings.builder().artifacts(['build/libs/*.jar', 'build/docs.zip docs'])
                    .group('com.bbh.cert').artifact('cert-api').repositoryUrl('http://nexus.bbh.com/repository/snapshots')
                    .allowInsecureProtocol(true).pull(ToolCommand.builder().flags(['--offline']).build())
                    .pullArtifacts(['cert-api.jar', 'docs.zip docs']).pullOutputDirectory('nexus').releaseEnabled(false)
                    .releaseRepositoryUrl('http://nexus.bbh.com/repository/releases').releaseCredentialsId('nexus-release')
                    .build(),
            tests: MAVEN_VM.tests(), testJobs: MAVEN_VM.testJobs(), urbanCode: MAVEN_VM.urbanCode(),
            urbanCodeApplications: MAVEN_VM.urbanCodeApplications(), sshTargets: MAVEN_VM.sshTargets(),
            appScan: MAVEN_VM.appScan(), sonar: MAVEN_VM.sonar(), scm: MAVEN_VM.scm(), metrics: MAVEN_VM.metrics())
    static final ServiceSettings NPM_VM = settings(build: build(tool: BuildTool.NPM, javaPath: null, command: ToolCommand.NONE),
            nodeBuild: NodeBuildSettings.builder().directory('web').commands(['npm ci', 'npm run build:prod']).build(),
            delivery: ToolCommand.of(['deploy:deploy-file'], ['-DgroupId=com.bbh.cert', '-DartifactId=cert-web',
                                                             '-DrepositoryId=bbh-snapshots',
                                                             '-Durl=http://nexus.bbh.com/repository/snapshots']),
            nexusDelivery: NexusDeliverySettings.builder().artifacts(['web/dist/cert-web.tgz'])
                    .releaseRepositoryUrl('https://nexus.bbh.com/repository/releases').releaseRepositoryId('bbh-releases')
                    .build(),
            urbanCode: MAVEN_VM.urbanCode(), urbanCodeApplications: MAVEN_VM.urbanCodeApplications(),
            sshTargets: MAVEN_VM.sshTargets(), scm: MAVEN_VM.scm(), metrics: MAVEN_VM.metrics())
    static final ServiceSettings DOTNET_VM = settings(build: build(tool: BuildTool.DOTNET, javaPath: null, command: ToolCommand.NONE),
            nexusDelivery: NexusDeliverySettings.builder().nugetSource('https://nexus.bbh.com/repository/nuget-snapshots/')
                    .nugetCredentialsId('nuget-key').nugetReleaseSource('https://nexus.bbh.com/repository/nuget-releases/')
                    .nugetReleaseCredentialsId('nuget-release-key').nugetPackagePattern('src/**/*.nupkg').build(),
            urbanCode: MAVEN_VM.urbanCode(), urbanCodeApplications: MAVEN_VM.urbanCodeApplications(),
            sshTargets: MAVEN_VM.sshTargets(), scm: MAVEN_VM.scm(), metrics: MAVEN_VM.metrics())
    /** A .NET Framework service that the library builds with NuGet.exe and MSBuild.exe, with commands of its own. */
    static final ServiceSettings MSBUILD_VM = settings(
            build: build(tool: BuildTool.MSBUILD, sourceDir: 'src', javaPath: null, command: ToolCommand.builder()
                    .tasks(['nuget restore Orders.sln', 'msbuild Orders.sln /t:Build /p:Configuration=Release'])
                    .flags(['/m']).directory('src').environment(['MSBUILDDISABLENODEREUSE=1']).label('MSBuild')
                    .build()),
            unitTests: new UnitTestSettings(ToolCommand.of(['vstest.console.exe Orders.Tests.dll /Logger:trx'], []),
                    '**/*.trx', null, null, null, null),
            appScan: appScan(compileCommand: ToolCommand.of(['msbuild Orders.sln /t:Build'], [])),
            sonar: SonarSettings.of('Orders', 'bbh-orders', ToolCommand.of([], ['/d:sonar.cs.vstest.reportsPaths=**/*.trx'])),
            nexusDelivery: DOTNET_VM.nexusDelivery(), urbanCode: MAVEN_VM.urbanCode(),
            urbanCodeApplications: MAVEN_VM.urbanCodeApplications(), sshTargets: MAVEN_VM.sshTargets(),
            scm: MAVEN_VM.scm(), metrics: MAVEN_VM.metrics())
    static final ServiceSettings LIQUIBASE_VM = settings(
            build: build(tool: LIQUIBASE, sourceDir: 'db', javaPath: null),
            unitTests: MAVEN_VM.unitTests(), tests: MAVEN_VM.tests(), testJobs: MAVEN_VM.testJobs(),
            urbanCode: MAVEN_VM.urbanCode(), urbanCodeApplications: MAVEN_VM.urbanCodeApplications(),
            sshTargets: MAVEN_VM.sshTargets(), appScan: MAVEN_VM.appScan(), sonar: MAVEN_VM.sonar(),
            scm: MAVEN_VM.scm(), metrics: MAVEN_VM.metrics())

    @Shared
    JsonMapper json = JsonMapper.builder().build()

    GlobalSettingsValues global = globalSettings()
    DsoConfigBuilder builder = new DsoConfigBuilder(global)
    ServiceConfigReader reader = new ServiceConfigReader(global, json)

    def "a #tool service on #target (delivery disabled: #disabled) read back from its config, in YAML or JSON, runs the same"() {
        given:
        Map config = builder.productConfig(product(services: [[name: 'gui', settings: settings]]))

        when:
        def fromYaml = imported(new Yaml().dump(config))
        def fromJson = imported(json.writeValueAsString(config))

        then:
        [fromYaml, fromJson].every { it.services()*.name() == ['gui'] && it.pipeline() == null && it.notes() == [] }
        asRun(rendered(fromYaml.services()[0])) == asRun(config)
        asRun(rendered(fromJson.services()[0])) == asRun(config)
        fromYaml.services()[0].notes() == notes
        fromJson.services()[0] == fromYaml.services()[0]

        where:
        settings                     || notes
        MAVEN_VM                     || []
        GRADLE_VM                    || []
        NPM_VM                       || []
        DOTNET_VM                    || []
        MSBUILD_VM                   || []
        GRADLE_OPENSHIFT             || [problem('deploy.openshift.rd.buildTag', RUN_TIME),
                                         problem('deploy.openshift.rd.internalDockerUrl', RUN_TIME)]
        GRADLE_OPENSHIFT_NO_DELIVERY || []
        FLUTTER_VM                   || []
        LIQUIBASE_VM                 || []

        tool = settings.build().tool()
        target = settings.deployment().target()
        disabled = settings.deployment().nexusDeliveryDisabled()
    }

    def "a Liquibase service with database changes read back from its config runs the same, without a note"() {
        given:
        Map config = builder.productConfig(product(services: [[name: 'orders-db', settings: ORDERS_DB]]))

        when:
        def result = imported(new Yaml().dump(config))

        then:
        result.notes() == []
        result.services()[0].notes() == []
        result.services()[0].service().build().tool() == LIQUIBASE
        asRun(rendered(result.services()[0])) == asRun(config)
    }

    def "a Liquibase project with database changes leaves out #key with a note, once"() {
        when:
        def service = imported(new Yaml().dump(merged(ORDERS, extra))).services()[0]

        then:
        service.notes() == [problem(key, message)]
        service.service().database().changelogFile() == 'db/changelog-master.xml'
        service.service().databaseTargets().isEmpty()

        where:
        extra                                                                        || key                       | message
        [database: [rd: [password: 'x', username: 'scott'].subMap(['password'])]]   || 'database.rd.password'    | SECRET
        [database: [test: [user: 'scott']]]                                         || 'database.test.user'      | SECRET
        [database: [qc: [url: 'jdbc:oracle:thin:scott/tiger@//qc-db.bbh.com:1521/X']]] || 'database.qc.url'      | SECRET_IN_URL
        [database: [prod: [url: 'jdbc:oracle:thin:@//prod-db.bbh.com:1521/X']]]    || 'database.prod'           | NO_PRODUCTION
        [database: [live: [credentialsId: 'live']]]                                 || 'database.live'           | NO_PRODUCTION
        [database: [allowedTargets: ['rd-db.bbh.com']]]                             || 'database.allowedTargets' | GLOBAL
        [database: [rd: [port: 1521]]]                                              || 'database.rd.port'        | UNKNOWN
        [tools: [liquibase: [home: '/opt/liquibase']]]                              || 'tools.liquibase'         | TOOLS_IN_DEFAULTS
        [tools: [utplsql: [url: 'https://tools.bbh.com/u.zip']]]                    || 'tools.utplsql'           | TOOLS_IN_DEFAULTS
        [deploy: [vm: [rd: [host: 'rdltaapps1.testbbh.com']]]]                      || 'deploy'                  | NOT_FOR_DATABASE
        [deployTarget: 'openshift']                                                 || 'deployTarget'            | NOT_FOR_DATABASE
        [appName: 'orders']                                                         || 'appName'                 | NOT_FOR_DATABASE
        [delivery: [liquibase: [commands: ['deploy']]]]                             || 'delivery'                | NOT_FOR_DATABASE
        [build: [gradle: [tasks: ['build']]]]                                       || 'build.gradle'            | BUILDS_LIQUIBASE
        [build: [maven: [goals: ['verify']]]]                                       || 'build.maven'             | BUILDS_LIQUIBASE
    }

    def "a Liquibase project with database changes imports main's Liquibase commands, scans and tests with the database"() {
        when:
        def service = imported(new Yaml().dump(merged(new Yaml().load(BILLING_DB), [database: ORDERS.database])))
                .services()[0]
        def plain = imported(BILLING_DB).services()[0].service()

        then:
        service.notes() == [problem('staticAnalysis.parallel', GLOBAL), problem('deployTarget', NOT_FOR_DATABASE),
                            problem('appName', NOT_FOR_DATABASE), problem('deploy', NOT_FOR_DATABASE),
                            problem('build.node.enabled', NODE_WITH_DATABASE)]
        service.service().database().changelogFile() == 'db/changelog-master.xml'
        KEPT.collect { service.service()."$it"() } == KEPT.collect { plain."$it"() }
        plain.nodeBuild().enabled()
        !service.service().nodeBuild().enabled()
        service.service().nodeBuild().directory() == plain.nodeBuild().directory()
        service.service().deployment().appName() == null
        service.service().sshTargets().isEmpty()
        plain.deployment().appName() == 'billing'
        plain.database().changelogFile() == null
        plain.databaseTargets().isEmpty()
    }

    /** What a Liquibase service imports alike with database changes and without; its Node.js build is switched off. */
    static final List<String> KEPT = ['build', 'unitTests', 'appScan', 'sonar', 'nexusIqApplications',
                                      'goldenFix']

    def "a database block without a Liquibase changelog is left out with a note: #project"() {
        expect:
        imported(new Yaml().dump(project)).services()[0].notes() == [problem('database', NOT_A_DATABASE)]

        where:
        project << [[buildTool: 'gradle', database: [changelogFile: 'db/changelog-master.xml']],
                    [buildTool: 'liquibase', database: [searchPath: 'db', rd: [credentialsId: 'orders-rd']]]]
    }

    def "buildTool #name is a service of SQL and PL/SQL files: its database block is read without a changelog file"() {
        when:
        def service = imported(new Yaml().dump([buildTool: name, sourceDir: 'db', buildToolAutoSetup: true,
                                               database: [searchPath: 'src', rd: [url: 'jdbc:oracle:thin:@//rd-db.bbh.com:1521/ORDERS_RD',
                                                                                  credentialsId: 'orders-db-rd']]])).services()[0]

        then:
        service.service().build().tool() == BuildTool.SQL
        service.service().database().changelogFile() == null
        service.service().database().searchPath() == 'src'
        service.service().databaseTargets()[DatabaseTarget.RD].credentialsId() == 'orders-db-rd'
        !service.notes().any { it.field() in ['buildTool', 'database'] }

        where:
        name << ['sql', 'plsql', 'PL/SQL', 'pl-sql']
    }

    def "buildTool #name is imported as #tool, under every name the library accepts for it"() {
        expect:
        imported("buildTool: ${name}\nappId: 109f44ac-cc06-4ca0-884e-d944904f7019\n".toString()).services()[0]
                .service().build().tool() == tool

        where:
        name           || tool
        'npm'          || BuildTool.NPM
        'node'         || BuildTool.NPM
        'Angular'      || BuildTool.NPM
        'yarn'         || BuildTool.NPM
        'pip'          || BuildTool.PIP
        'python'       || BuildTool.PIP
        'poetry'       || BuildTool.PIP
        'android'      || BuildTool.ANDROID
        'ios'          || BuildTool.IOS
        'xcode'        || BuildTool.IOS
        'cocoapods'    || BuildTool.IOS
        'mvn'          || BuildTool.MAVEN
        'gradlew'      || BuildTool.GRADLE
        'dotnet'       || BuildTool.DOTNET
        'csharp'       || BuildTool.DOTNET
        'c#'           || BuildTool.DOTNET
        '.net'         || BuildTool.DOTNET
        'net'          || BuildTool.DOTNET
        'dotnet-cli'   || BuildTool.DOTNET
        'fsharp'       || BuildTool.DOTNET
        'vbnet'        || BuildTool.DOTNET
        'Cake'         || BuildTool.DOTNET
        'nuke'         || BuildTool.DOTNET
        'msbuild'      || BuildTool.MSBUILD
        'MSBuild'      || BuildTool.MSBUILD
        'vs'           || BuildTool.MSBUILD
        'VisualStudio' || BuildTool.MSBUILD
        'netfx'        || BuildTool.MSBUILD
    }

    def "a Python service is read with its command lines, the folder of its tests and its SonarScanner arguments"() {
        when:
        def service = imported(new Yaml().dump([buildTool: 'pip', sourceDir: 'api',
                                               build: [pip: [commands: ['pip install -r requirements.txt', 'python -m build']],
                                                       commands: ['make']],
                                               tests: [unitTests: [commands: ['pytest --junitxml=junit.xml'], dir: 'tests']],
                                               tools: [sonar: [pip: [flags: ['-Dsonar.python.version=3.12'], sources: 'src']]]]))
                .services()[0]
        def settings = service.service()

        then:
        settings.build().tool() == BuildTool.PIP
        settings.build().command().tasks().join(' ') == 'pip install -r requirements.txt && python -m build'
        settings.unitTests().command().tasks().join(' ') == 'pytest --junitxml=junit.xml'
        settings.unitTests().command().directory() == 'tests'
        settings.sonar().command().flags() == ['-Dsonar.python.version=3.12', '-Dsonar.sources=src']
        service.notes().find { it.field() == 'build.commands' }.message() == 'not imported: build.pip.commands wins over it'
        !service.notes().any { it.field().startsWith('tests.') || it.field().startsWith('tools.sonar') || it.field() == 'buildTool' }
    }

    def "an npm service is read with its Node.js build, its test command and the SonarScanner arguments of tools.sonar.node"() {
        when:
        def service = imported(new Yaml().dump([buildTool: 'angular',
                                               build: [node: [dir: 'web', commands: ['npm ci', 'npm run build -- --configuration production']]],
                                               tests: [unitTests: [tasks: 'npm test -- --watch=false']],
                                               tools: [sonar: [node: [flags: ['-Dsonar.exclusions=**/*.spec.ts']],
                                                               gradle: [tasks: ['sonarqube']]]]])).services()[0]
        def settings = service.service()

        then:
        settings.build().tool() == BuildTool.NPM
        settings.build().command().tasks() == []
        settings.nodeBuild().directory() == 'web'
        settings.nodeBuild().commands() == ['npm ci', 'npm run build -- --configuration production']
        settings.unitTests().command().tasks().join(' ') == 'npm test -- --watch=false'
        settings.sonar().command().flags() == ['-Dsonar.exclusions=**/*.spec.ts']
        service.notes().find { it.field() == 'tools.sonar.gradle.tasks' || it.field() == 'tools.sonar.gradle' }.message() ==
                'not imported: the service builds with npm'
    }

    def "an Android service is read with the Gradle blocks, and an iOS service with build.ios.commands or build.commands"() {
        when:
        def android = imported(new Yaml().dump([buildTool: 'android', javaPath: '/opt/jdk-17',
                                               build: [gradle: [tasks: ['clean', 'assembleRelease']]],
                                               tests: [unitTests: [gradle: [tasks: ['testReleaseUnitTest']]]],
                                               tools: [sonar: [gradle: [tasks: ['sonarqube']]]]])).services()[0].service()
        def ios = imported(new Yaml().dump([buildTool: 'swift', build: [commands: ['xcodebuild -scheme App build']],
                                           tests: [unitTests: [commands: ['xcodebuild -scheme App test']]]])).services()[0].service()

        then:
        android.build().tool() == BuildTool.ANDROID
        android.build().command().tasks() == ['clean', 'assembleRelease']
        android.unitTests().command().tasks() == ['testReleaseUnitTest']
        android.sonar().command().tasks() == ['sonarqube']
        ios.build().tool() == BuildTool.IOS
        ios.build().command().tasks().join(' ') == 'xcodebuild -scheme App build'
        ios.unitTests().command().tasks().join(' ') == 'xcodebuild -scheme App test'
    }

    def "an MSBuild service reads the msbuild blocks, which the library takes first, and renders them as the dotnet blocks"() {
        when:
        def service = imported(new Yaml().dump([buildTool: 'msbuild',
                                               build: [msbuild: [commands: ['msbuild Orders.sln /t:Rebuild'], flags: ['/m']],
                                                       dotnet: [commands: ['dotnet build']]],
                                               tests: [unitTests: [msbuild: [commands: ['vstest.console.exe Orders.Tests.dll']]]],
                                               tools: [sonar: [dotnet: [flags: ['/d:sonar.verbose=true']]]],
                                               asoc: [msbuild: [:], dotnet: [commands: ['dotnet build']]]]), 'orders').services()[0]
        def settings = service.service()
        def project = rendered(service).projects['orders']

        then:
        settings.build().tool() == BuildTool.MSBUILD
        settings.build().command().tasks() == ['msbuild Orders.sln /t:Rebuild']
        settings.build().command().flags() == ['/m']
        settings.unitTests().command().tasks() == ['vstest.console.exe Orders.Tests.dll']
        settings.sonar().command().flags() == ['/d:sonar.verbose=true']
        settings.appScan().compileCommand().tasks() == []
        settings.appScan().compileCommand().flags() == []
        service.notes() as Set == [problem('build.msbuild', dotnetBlock('build')),
                                   problem('build.dotnet', 'not imported: build.msbuild wins over it'),
                                   problem('tests.unitTests.msbuild', dotnetBlock('tests.unitTests')),
                                   problem('asoc.dotnet', 'not imported: asoc.msbuild wins over it')] as Set
        project.buildTool == 'msbuild'
        project.build.dotnet == [commands: ['msbuild Orders.sln /t:Rebuild'], flags: ['/m']]
        !project.build.containsKey('msbuild')
        project.tests.unitTests == [dotnet: [commands: ['vstest.console.exe Orders.Tests.dll']]]
        project.tools.sonar.dotnet == [flags: ['/d:sonar.verbose=true']]
        !project.asoc.containsKey('dotnet')
        !project.asoc.containsKey('msbuild')
    }

    def "a C# / .NET service reads the dotnet blocks, and an msbuild block only where it has no dotnet block, as the library does"() {
        when:
        def service = imported(new Yaml().dump([buildTool: 'csharp',
                                               build: [dotnet: [commands: ['dotnet build -c Release']],
                                                       msbuild: [commands: ['msbuild Orders.sln']]],
                                               tests: [unitTests: [msbuild: [commands: ['dotnet test']]]],
                                               tools: [sonar: [msbuild: '/d:sonar.verbose=true']]])).services()[0]
        def settings = service.service()

        then:
        settings.build().tool() == BuildTool.DOTNET
        settings.build().command().tasks() == ['dotnet build -c Release']
        settings.unitTests().command().tasks() == ['dotnet test']
        settings.sonar().command().tasks() == []
        settings.sonar().command().flags() == []
        service.notes() as Set == [problem('build.msbuild', 'not imported: build.dotnet wins over it'),
                                   problem('tests.unitTests.msbuild', dotnetBlock('tests.unitTests')),
                                   problem('tools.sonar.msbuild', 'not imported: expected a map')] as Set
    }

    def "for buildTool #tool the import takes the #winner of #block, which the library runs first, and notes #losers"() {
        given:
        String path = block.substring(0, block.lastIndexOf('.'))
        String kept = "${path}.${tool == 'liquibase' ? 'liquibase' : 'dotnet'}.commands"

        when:
        def service = imported(new Yaml().dump([buildTool: tool] + at(block, keys)), 'orders').services()[0]

        then:
        commandAt(service.service(), path).tasks() == keys[winner]
        service.notes() as Set == (losers.collect { problem("${block}.${it}".toString(),
                "not imported: ${block}.${winner} wins over it".toString()) }
                + (block.endsWith('.msbuild') ? [problem(block, dotnetBlock(path))] : [])) as Set
        ConfigInput.valueAt(rendered(service).projects['orders'], kept) == keys[winner]

        where:
        tool        | block                       | keys                                                                                                    || winner     | losers
        'msbuild'   | 'build.msbuild'             | [commands: ['nuget restore Orders.sln', 'msbuild Orders.sln /t:Build'], tasks: ['msbuild Legacy.sln']] || 'commands' | ['tasks']
        'msbuild'   | 'tests.unitTests.msbuild'   | [commands: ['vstest.console.exe A.dll'], tasks: ['dotnet test']]                                       || 'commands' | ['tasks']
        'msbuild'   | 'build.msbuild'             | [goals: ['msbuild A.sln']]                                                                              || 'goals'    | []
        'msbuild'   | 'asoc.msbuild'              | [tasks: ['msbuild A.sln /t:Build'], goals: ['msbuild B.sln']]                                           || 'tasks'    | ['goals']
        'dotnet'    | 'build.dotnet'              | [commands: ['dotnet build -c Release'], tasks: ['dotnet build'], goals: ['dotnet pack']]               || 'commands' | ['tasks', 'goals']
        'dotnet'    | 'tools.sonar.dotnet'        | [tasks: ['dotnet build Orders.sln'], goals: ['dotnet build']]                                          || 'tasks'    | ['goals']
        'liquibase' | 'build.liquibase'           | [commands: ['validate'], tasks: ['status']]                                                             || 'commands' | ['tasks']
        'liquibase' | 'tests.unitTests.liquibase' | [tasks: ['validate'], commands: ['status']]                                                             || 'commands' | ['tasks']
    }

    def "for buildTool #tool a command block that is not a map is noted as not imported, since the library ignores it too: #build"() {
        when:
        def service = imported(new Yaml().dump([buildTool: tool, build: build])).services()[0]

        then:
        service.service().build().tool().name().equalsIgnoreCase(tool)
        service.service().build().command().tasks() == []
        service.notes() as Set == noted.collect { problem("build.${it}".toString(), 'not imported: expected a map') } as Set

        where:
        tool      | build                                   || noted
        'msbuild' | [dotnet: 'msbuild Orders.sln /t:Build'] || ['dotnet']
        'msbuild' | [dotnet: 'x', msbuild: 'y']             || ['dotnet', 'msbuild']
        'msbuild' | [dotnet: ['msbuild Orders.sln']]        || ['dotnet']
        'dotnet'  | [dotnet: 'dotnet build']                || ['dotnet']
        'dotnet'  | [dotnet: 'x', msbuild: 'y']             || ['dotnet', 'msbuild']
        'gradle'  | [gradle: 'build']                       || ['gradle']
    }

    def "the .NET blocks of a #tool service are left out with a note"() {
        when:
        def service = imported(new Yaml().dump([buildTool: tool, build: [msbuild: [commands: ['msbuild Orders.sln']],
                                                                         dotnet : [commands: ['dotnet build']]]]))
                .services()[0]

        then:
        service.notes().findAll { it.field().startsWith('build.') } as Set == [
                problem('build.msbuild', "not imported: the service builds with ${tool}".toString()),
                problem('build.dotnet', "not imported: the service builds with ${tool}".toString())] as Set

        where:
        tool << ['gradle', 'maven', 'liquibase']
    }

    def "buildTool sql with #key is imported as Liquibase, as the library runs it, with a note"() {
        when:
        def service = imported(new Yaml().dump([buildTool: 'sql'] + project)).services()[0]

        then:
        service.service().build().tool() == LIQUIBASE
        service.notes().find { it.field() == 'buildTool' }.message() == ('imported as Liquibase: buildTool sql is for SQL'
                + ' and PL/SQL files without a changelog, and the library runs this service as buildTool liquibase'
                + " because ${key} is set")

        where:
        key                         | project
        'database.changelogFile'    | [database: [changelogFile: 'db/changelog-master.xml']]
        'build.liquibase'           | [build: [liquibase: [commands: ['updateSQL']]]]
        'tests.unitTests.liquibase' | [tests: [unitTests: [liquibase: [commands: ['validate']]]]]
    }

    def "the Maven home of build.maven fills that of the package unless the package names its own: #maven"() {
        when:
        def service = imported(new Yaml().dump(merged(ORDERS, [build: [maven: maven]] + extra))).services()[0]

        then:
        service.service().database().mvnPath() == mvnPath
        service.notes() == notes

        where:
        maven                                         | extra                                             || mvnPath        | notes
        [mvnPath: '/opt/maven']                       | [:]                                               || '/opt/maven'   | []
        [mvnPath: '/opt/maven', goals: ['verify']]    | [:]                                               || '/opt/maven'   | [problem('build.maven', BUILDS_LIQUIBASE)]
        [mvnPath: '/opt/maven']                       | [database: [package: [mvnPath: '/opt/maven-3']]]  || '/opt/maven-3' | [problem('build.maven', BUILDS_LIQUIBASE)]
    }

    def "repositories equal to the Library defaults and a package named after the project come back empty"() {
        given:
        useDatabaseDefaults()
        def own = [id: 'own-snapshots', url: 'https://nexus.bbh.com/own']

        when:
        def service = imported(new Yaml().dump(merged(ORDERS, [database: [package: [artifactId: 'orders-db',
                snapshotRepository: own, releaseRepository: [id: 'bbh-db-releases',
                url: 'https://tools.bbh.com/nexus/repository/db-releases']]]])), 'orders-db').services()[0]
        def renamed = imported(new Yaml().dump(merged(ORDERS, [database: [package: [artifactId: 'orders-db']]])),
                'orders').services()[0]

        then:
        service.notes() == []
        with(service.service().database()) {
            [artifactId(), releaseRepositoryId(), releaseRepositoryUrl()] == [null, null, null]
            [snapshotRepositoryId(), snapshotRepositoryUrl()] == [own.id, own.url]
        }
        renamed.service().database().artifactId() == 'orders-db'
    }

    def "the orders-db project of the library's layout (#layout) imports without a note and renders its database block"() {
        given:
        useDatabaseDefaults()
        String file = getClass().getResource("/library-layouts/layout-${layout}.yaml").text
        Map database = new Yaml().load(file).projects['orders-db'].database
        database.package.keySet().removeAll(['snapshotRepository', 'releaseRepository'])

        when:
        def ordersDb = imported(file).services().find { it.name() == 'orders-db' }

        then:
        ordersDb.notes() == []
        ordersDb.service().build().tool() == LIQUIBASE
        rendered(ordersDb).projects['orders-db'].database == database

        where:
        layout << ['a', 'b', 'c']
    }

    def "values equal to the Library defaults come back empty, so the service keeps inheriting them"() {
        when:
        def service = imported('''
            appId: 109f44ac-cc06-4ca0-884e-d944904f7019
            tests:
              maxParallel: 20
            goldenFix:
              enabled: true
              onlyDirectDependencies: true
              minThreatLevel: 7
              verify:
                maxAttempts: 3
                timeoutMinutes: 45
            '''.stripIndent()).services()[0]

        then:
        service.notes() == []
        service.service().tests().maxParallel() == null
        with(service.service().goldenFix()) {
            [enabled(), verifyMaxAttempts()] == [null, null]
            [minThreatLevel(), verifyTimeoutMinutes()] == [7, 45]
        }
    }

    def "Node.js build values equal to the Library defaults or to the library's own defaults come back empty"() {
        given:
        global = globalSettings { copy(it, serviceDefaults: copy(it.serviceDefaults(), nodeInstallationName: 'NodeJS-20',
                npmRegistry: 'https://nexus.bbh.com/repository/npm-group/')) }
        reader = new ServiceConfigReader(global, json)

        when:
        def service = imported('''
            appId: 109f44ac-cc06-4ca0-884e-d944904f7019
            build:
              node:
                enabled: true
                commands: [npm ci, npm run build]
                installationName: NodeJS-20
                registry: https://nexus.bbh.com/repository/npm-group/
                npmrcCredentialsId: cert-npmrc
                timeoutMin: 30
                nexusIqScan: true
            '''.stripIndent()).services()[0]

        then:
        service.notes() == []
        service.service().nodeBuild() ==
                new NodeBuildSettingsDto(true, null, [], null, null, [], null, 'cert-npmrc', null, null)
    }

    def "Node.js build keys without enabled: true come back as a build that is switched off and keeps its values"() {
        when:
        def service = imported('''
            appId: 109f44ac-cc06-4ca0-884e-d944904f7019
            build:
              node:
                dir: frontend
                commands: npm run build:prod
                env:
                  CI: true
                timeoutMin: 45
                nexusIqScan: false
            goldenFix:
              versionCatalogs: [gradle/libs.versions.toml, platform/pom.xml]
            '''.stripIndent()).services()[0]

        then:
        service.notes() == []
        service.service().nodeBuild() ==
                new NodeBuildSettingsDto(false, 'frontend', ['npm run build:prod'], null, null, ['CI=true'], null, null, 45, false)
        service.service().versionCatalogs() == ['gradle/libs.versions.toml', 'platform/pom.xml']
        service.service().goldenFix().enabled() == null
    }

    def "listed catalogs are imported without ./ and a folder is left out with a note that asks for its file: #catalogs"() {
        when:
        def service = imported("""
            appId: 109f44ac-cc06-4ca0-884e-d944904f7019
            goldenFix:
              versionCatalogs: ${catalogs}
            """.stripIndent()).services()[0]

        then:
        service.notes() == (folder ? [problem('goldenFix.versionCatalogs',
                "not imported: ${folder} is a folder; name the file in it, such as platform/pom.xml".toString())] : [])
        service.service().versionCatalogs() == kept

        where:
        catalogs                                       || kept                                     | folder
        '[./platform/pom.xml, gradle//deps.toml]'      || ['platform/pom.xml', 'gradle/deps.toml'] | null
        '[gradle/libs.versions.toml, platform/bom/]'   || []                                       | 'platform/bom/'
        './'                                           || []                                       | './'
    }

    def "coverage reports are imported one per line and a list longer than the portal keeps is left out with a note: #reports"() {
        when:
        def service = imported("""
            appId: 109f44ac-cc06-4ca0-884e-d944904f7019
            coverage:
              reportPath: ${reports}
            """.stripIndent()).services()[0]

        then:
        service.notes() == (kept ? [] : [problem('coverage.reportPath', LONG_PATHS)])
        service.service().unitTests()?.coverageReportPath() == kept

        where:
        reports                                                || kept
        'build/jacoco.xml'                                     || 'build/jacoco.xml'
        'a/jacoco.xml, web/coverage/lcov.info'                 || 'a/jacoco.xml, web/coverage/lcov.info'
        "['**/build/reports/jacoco/test/*.xml', web/lcov.info]" || '**/build/reports/jacoco/test/*.xml\nweb/lcov.info'
        "[${(1..20).collect { "module-${it}/build/reports/jacoco/test/jacocoTestReport.xml" }.join(', ')}]" || null
    }

    def "a Flutter config that enables the Node.js build imports it switched off with a note"() {
        when:
        def service = imported('''
            appId: 109f44ac-cc06-4ca0-884e-d944904f7019
            buildTool: flutter
            build:
              node:
                enabled: true
                dir: web
            '''.stripIndent()).services()[0]

        then:
        service.notes() == [problem('build.node.enabled', NODE_WITH_FLUTTER)]
        service.service().nodeBuild().enabled() == false
        service.service().nodeBuild().directory() == 'web'
    }

    def "the delivery switch is read from the service's own deploy target and the other target's is left out"() {
        when:
        def service = imported('''
            appId: 109f44ac-cc06-4ca0-884e-d944904f7019
            deployTarget: vm
            deploy:
              vm:
                rd:
                  host: rdltaapps9.testbbh.com
              openshift:
                rd:
                  nexusDeliveryEnabled: false
            '''.stripIndent()).services()[0]

        then:
        !service.service().deployment().nexusDeliveryDisabled()
        service.notes() == [problem('deploy.openshift.rd.nexusDeliveryEnabled', 'not imported: the service deploys to vm')]
    }

    def "delivery.nexusDeliveryEnabled wins over the switch of the deploy target, which is read without it"() {
        when:
        def service = imported(config.stripIndent()).services()[0]

        then:
        service.service().deployment().nexusDeliveryDisabled() == disabled
        service.notes() == notes

        where:
        config                                                                     || disabled | notes
        LEGACY_SWITCH                                                              || true     | []
        LEGACY_SWITCH + 'delivery: {nexusDeliveryEnabled: true}\n'                 || false    | [problem('deploy.vm.rd.nexusDeliveryEnabled', 'not imported: delivery.nexusDeliveryEnabled wins over it')]
        'deployTarget: vm\ndelivery: {nexusDeliveryEnabled: false}\n'             || true     | []
    }

    def "a Maven service imports delivery.push.maven and leaves out delivery.maven, which it replaces"() {
        when:
        def service = imported('''
            buildTool: maven
            deployTarget: vm
            delivery:
              maven: {goals: [deploy:deploy-file], flags: [-Dold=1]}
              push:
                maven:
                  goals: [deploy:deploy-file]
                  flags: [-DrepositoryId=bbh-snapshots]
                  artifacts: [target/*.jar, {path: target/docs.zip, classifier: docs}]
              pull:
                group: com.bbh.cert
                artifact: cert-gui
                maven:
                  goals: [org.apache.maven.plugins:maven-dependency-plugin:3.6.1:get]
                  artifacts: [{path: cert-gui.jar}]
            '''.stripIndent()).services()[0]
        def delivery = service.service().nexusDelivery()

        then:
        service.notes() == [problem('delivery.maven', 'not imported: delivery.push.maven replaces it')]
        service.service().delivery().tasks() == ['deploy:deploy-file']
        service.service().delivery().flags() == ['-DrepositoryId=bbh-snapshots']
        delivery.artifacts() == ['target/*.jar', 'target/docs.zip docs']
        delivery.group() == 'com.bbh.cert'
        delivery.artifact() == 'cert-gui'
        delivery.pull().tasks() == ['org.apache.maven.plugins:maven-dependency-plugin:3.6.1:get']
        delivery.pullArtifacts() == ['cert-gui.jar']
    }

    def "the release of stage 12 is read for the upload of a service on virtual machines, and a value of the Library defaults comes back unset"() {
        when:
        def maven = imported('''
            buildTool: maven
            deployTarget: vm
            delivery:
              maven: {goals: [deploy:deploy-file]}
              release:
                enabled: true
                repositoryUrl: https://nexus.bbh.com/repository/releases
                repositoryId: bbh-releases
                credentialsId: nexus-release
            '''.stripIndent()).services()[0]
        def gradle = imported('''
            buildTool: gradle
            deployTarget: vm
            delivery:
              release: {enabled: false, repositoryId: bbh-releases, credentialsId: nexus-release}
            '''.stripIndent()).services()[0]
        def flutter = imported('''
            buildTool: flutter
            deployTarget: vm
            delivery:
              release: {enabled: false}
            '''.stripIndent()).services()[0]
        def openShift = imported('''
            buildTool: maven
            deployTarget: openshift
            delivery:
              release: {repositoryUrl: https://nexus.bbh.com/repository/releases}
            '''.stripIndent()).services()[0]

        then:
        maven.notes() == [problem('delivery.release.credentialsId',
                'not imported: a Maven upload logs in to the release repository with delivery.release.repositoryId')]
        with(maven.service().nexusDelivery()) {
            releaseEnabled() == null
            releaseRepositoryUrl() == 'https://nexus.bbh.com/repository/releases'
            releaseRepositoryId() == 'bbh-releases'
            releaseCredentialsId() == null
        }
        gradle.notes() == [problem('delivery.release.repositoryId',
                'not imported: a Gradle upload logs in to the release repository with delivery.release.credentialsId')]
        with(gradle.service().nexusDelivery()) {
            releaseEnabled() == false
            releaseCredentialsId() == 'nexus-release'
            releaseRepositoryId() == null
        }
        flutter.notes() == [problem('delivery.release',
                'not imported: the library releases no artifact of a flutter service in stage 12')]
        openShift.notes() == [problem('delivery.release',
                'not imported: an OpenShift service delivers its image, and stage 12 releases no artifact of it')]
        openShift.service().nexusDelivery().releaseRepositoryUrl() == null
    }

    def "an npm service reads the Maven upload of its package from delivery.push.maven"() {
        when:
        def service = imported('''
            buildTool: npm
            deployTarget: vm
            build:
              node: {dir: web, commands: [npm ci, npm run build]}
            delivery:
              maven: {goals: [deploy:deploy-file]}
              push:
                maven:
                  goals: [deploy:deploy-file]
                  flags: [-DgroupId=com.bbh.cert, -DartifactId=cert-web]
                  artifacts: [web/dist/cert-web.tgz]
            '''.stripIndent()).services()[0]

        then:
        service.notes() == [problem('delivery.maven', 'not imported: the service builds with npm')]
        service.service().delivery().tasks() == ['deploy:deploy-file']
        service.service().delivery().flags() == ['-DgroupId=com.bbh.cert', '-DartifactId=cert-web']
        service.service().nexusDelivery().artifacts() == ['web/dist/cert-web.tgz']
    }

    def "a .NET service reads its NuGet feeds from delivery.nuget, else delivery.dotnet, with the other names the library reads"() {
        when:
        def nuget = imported('''
            buildTool: dotnet
            deployTarget: vm
            delivery:
              nuget:
                source: https://nexus.bbh.com/nuget-snapshots/
                repositoryUrl: https://nexus.bbh.com/other/
                apiKeyCredentialsId: nuget-key
                releaseSource: https://nexus.bbh.com/nuget-releases/
                packagePattern: '**/*.nupkg'
              dotnet:
                source: https://nexus.bbh.com/legacy/
              release:
                repositoryUrl: https://nexus.bbh.com/releases
            '''.stripIndent()).services()[0]
        def dotnet = imported('''
            buildTool: dotnet
            deployTarget: vm
            delivery:
              dotnet:
                repositoryUrl: https://nexus.bbh.com/legacy/
                credentialsId: legacy-key
                releaseCredentialsId: release-key
            '''.stripIndent()).services()[0]
        def maven = imported('''
            buildTool: maven
            deployTarget: vm
            delivery:
              maven: {goals: [deploy:deploy-file]}
              nuget: {credentialsId: nuget-key}
            '''.stripIndent()).services()[0]

        then:
        nuget.notes() as Set == [
                problem('delivery.nuget.repositoryUrl', 'not imported: delivery.nuget.source wins over it'),
                problem('delivery.dotnet', 'not imported: delivery.nuget replaces it'),
                problem('delivery.release',
                        'not imported: a .NET service releases its NuGet package to delivery.nuget.releaseSource')] as Set
        with(nuget.service().nexusDelivery()) {
            nugetSource() == 'https://nexus.bbh.com/nuget-snapshots/'
            nugetCredentialsId() == 'nuget-key'
            nugetReleaseSource() == 'https://nexus.bbh.com/nuget-releases/'
            nugetPackagePattern() == '**/*.nupkg'
        }
        dotnet.notes() == []
        with(dotnet.service().nexusDelivery()) {
            nugetSource() == 'https://nexus.bbh.com/legacy/'
            nugetCredentialsId() == 'legacy-key'
            nugetReleaseCredentialsId() == 'release-key'
        }
        maven.notes() == [problem('delivery.nuget', 'not imported: the service builds with maven')]
    }

    def "a Gradle service keeps one group of its upload and download and leaves out another task or group with a note"() {
        when:
        def service = imported('''
            buildTool: gradle
            deployTarget: vm
            delivery:
              gradle: {tasks: [publish]}
              push:
                gradle:
                  group: com.bbh.cert
                  artifact: cert-api
                  repositoryUrl: https://nexus.bbh.com/repository/snapshots
                  tasks: [publish]
                  artifacts: [build/libs/*.jar]
              pull:
                group: com.bbh.other
                gradle:
                  tasks: [pullNexusArtifacts]
                  artifacts: [cert-api.jar]
            '''.stripIndent()).services()[0]
        def delivery = service.service().nexusDelivery()

        then:
        service.notes() as Set == [
                problem('delivery.gradle', 'not imported: a Gradle service uploads with delivery.push.gradle'),
                problem('delivery.pull.group', 'not imported: the portal uploads and downloads with one group, com.bbh.cert'),
                problem('delivery.push.gradle.tasks', 'not imported: the Gradle step of the Nexus delivery runs only publishNexusDeliveryPublicationToNexusRepository')] as Set
        delivery.group() == 'com.bbh.cert'
        delivery.repositoryUrl() == 'https://nexus.bbh.com/repository/snapshots'
        delivery.artifacts() == ['build/libs/*.jar']
        delivery.pullArtifacts() == ['cert-api.jar']
    }

    def "a Liquibase SQL/PL-SQL config imports build, unit test, SAST and SonarQube sources, inclusions and exclusions"() {
        when:
        def importedService = imported('''
            projects:
              billing-db:
                buildTool: liquibase
                sourceDir: db
                appId: 109f44ac-cc06-4ca0-884e-d944904f7019
                staticAnalysis:
                  parallel: true
                dynamicAnalysis:
                  parallel: true
                pluginRepositories:
                  enabled: false
                  gradle: https://nexus.example.com/gradle-plugins/
                build:
                  liquibase:
                    commands: updateSQL
                    flags:
                      - --changelog-file=db/changelog/db.changelog-master.xml
                      - --url=offline:oracle
                tests:
                  unitTests:
                    liquibase:
                      tasks: validate
                      flags: --changelog-file=db/changelog/db.changelog-master.xml
                asoc:
                  liquibase:
                    commands: updateSQL
                    flags: --changelog-file=db/changelog/db.changelog-master.xml
                tools:
                  sonar:
                    projectKey: com.bbh:billing-db
                    projectName: Billing-DB
                    liquibase:
                      sources: [db/changelog, db/plsql]
                      inclusions: "**/*.sql,**/*.pls,**/*.pks,**/*.pkb"
                      exclusions: "**/testdata/**"
                      flags:
                        - -Dsonar.plsql.file.suffixes=.sql,.pls,.pks,.pkb
            '''.stripIndent()).services()[0]
        def db = importedService.service()

        then:
        importedService.name() == 'billing-db'
        importedService.notes() == [problem('staticAnalysis.parallel', GLOBAL), problem('dynamicAnalysis.parallel', GLOBAL),
                                    problem('pluginRepositories.enabled', GLOBAL), problem('pluginRepositories.gradle', GLOBAL)]
        db.build().tool() == LIQUIBASE
        db.build().sourceDir() == 'db'
        db.build().command().tasks() == ['updateSQL']
        db.build().command().flags() == ['--changelog-file=db/changelog/db.changelog-master.xml', '--url=offline:oracle']
        db.unitTests().command().tasks() == ['validate']
        db.unitTests().command().flags() == ['--changelog-file=db/changelog/db.changelog-master.xml']
        db.appScan().compileCommand().tasks() == ['updateSQL']
        db.sonar().projectKey() == 'com.bbh:billing-db'
        db.sonar().command().flags() == [
                '-Dsonar.plsql.file.suffixes=.sql,.pls,.pks,.pkb',
                '-Dsonar.sources=db/changelog,db/plsql',
                '-Dsonar.inclusions=**/*.sql,**/*.pls,**/*.pks,**/*.pkb',
                '-Dsonar.exclusions=**/testdata/**'
        ]
    }

    def "Cypress unit test settings are imported from tests.unitTests.cypress"() {
        when:
        def imported = imported('''
            projects:
              frontend:
                buildTool: maven
                deployTarget: openshift
                tests:
                  unitTests:
                    cypress:
                      enabled: true
                      command: npx cypress run --component
                      reportsDir: cypress/results
        '''.stripIndent()).services()[0].service()

        then:
        imported.unitTests().cypressEnabled()
        imported.unitTests().cypressCommand() == 'npx cypress run --component'
        imported.unitTests().cypressReportsDir() == 'cypress/results'
    }

    def "a hand-written config.yaml gives a service per project, inherits the global values and notes what it leaves out"() {
        when:
        def result = imported(getClass().getResource('/config-import/CertScanner-config.yaml').text)
        def gui = result.services()[0].service()
        def api = result.services()[1].service()

        then:
        result.services()*.name() == ['gui', 'backend-api']
        result.pipeline() == null
        result.notes() == []
        result.services()[0].notes() == [problem('asoc.keyId', APPSCAN_KEY), problem('jenkins.pipeline.extendedPipeline', PIPELINE),
                                         problem('asoc.keySecret', SECRET), problem('influx.token', SECRET),
                                         problem('sca.scanName', UNKNOWN), problem('dast.scanName', UNKNOWN)]
        result.services()[1].notes() == [problem('asoc.keyId', APPSCAN_KEY), problem('asoc.keySecret', SECRET), problem('influx.token', SECRET),
                                         problem('sca.scanName', UNKNOWN), problem('dast.scanName', UNKNOWN),
                                         problem('build.maven.javaPath', UNKNOWN)]
        !json.writeValueAsString(result).contains('O4c95brMewp')

        with(gui) {
            [build.tool(), build.sourceDir(), build.javaPath()] == [GRADLE, '.', '/usr/lib/jvm/java-17-openjdk']
            [build.command().tasks(), build.command().flags()] == [['clean', 'build', 'bootJar'], ['--refresh-dependencies']]
            [appScan.applicationId(), appScan.scanName()] == ['209f44ac-dd06-4ca0-884e-d944904f8020', 'CertScanner-GUI-SAST']
            [appScan.dastEnabled(), appScan.dastTargetUrl(), appScan.dastPresenceId()] ==
                    [true, 'http://rdltaapps1.testbbh.com', null]
            metrics == new MetricsSettingsDto(true, 'CertScanner', 'test', null, null)
            [sonar.serverUrl(), sonar.installationName(), sonar.credentialsId(), sonar.authTokenCredentialsId()] ==
                    [null, null, 'sonarqube-token', 'sonarqube-token']
            [sonar.addBadges(), sonar.fullBadges(), sonar.command().tasks()] == [true, true, ['sonarqube']]
            [nexusIq.serverUrl(), nexusIq.credentialsId()] == [null, null]
            nexusIqApplications == [new NexusIqApplicationDto('CertValidityMonitoring-GUI', ['**/gui/build/libs/*.jar'],
                    'build', false)]
            [scm.repositoryUrl(), scm.authType(), scm.targetBranch(), scm.reviewers()] ==
                    ['https://bitbucket.bbh.com/projects/TA/repos/cert-scanner', BASIC, null, []]
            goldenFix.enabled() == null
            [unitTests.command().tasks(), unitTests.resultPattern()] ==
                    [['test', 'jacocoTestReport', 'jacocoTestCoverageVerification'], 'build/test-results/test/*.xml']
            [tests.regressionMaxParallel(), tests.smokeMaxParallel()] == [5, 20]
            testJobs.collect { [it.stage(), it.name(), it.type(), it.job(), it.timeoutMinutes(), it.credentialsId()] } == [
                    [SMOKE, 'CertScanner-GUI - login smoke', REMOTE,
                     'https://jenkins-a.bbh.com/job/smoke/job/cert-scanner-login', 15, 'remote-jenkins-api-token'],
                    [SMOKE, 'CertScanner-GUI - certificate list smoke', REMOTE,
                     'https://jenkins-a.bbh.com/job/smoke/job/cert-scanner-list', 15, 'remote-jenkins-api-token'],
                    [SMOKE, 'CertScanner-GUI - notification smoke', REMOTE, 'smoke/cert-scanner-notifications', 15,
                     'remote-jenkins-api-token'],
                    [SMOKE, 'CertScanner-GUI - local smoke', LOCAL, 'cert-scanner/smoke-tests', 15,
                     'remote-jenkins-api-token'],
                    [SMOKE, null, REMOTE, 'https://jenkins-c.bbh.com/job/smoke/job/cert-scanner-dashboard', 15,
                     'remote-jenkins-api-token'],
                    [SMOKE, null, REMOTE, 'https://jenkins-b.bbh.com/job/smoke/job/cert-scanner-expiry', 15,
                     'remote-jenkins-api-token'],
                    [REGRESSION, 'CertScanner-GUI - regression', LOCAL, 'cert-scanner/regression-tests', 60, null],
                    [REGRESSION, 'CertScanner-GUI - regression (certificates)', LOCAL,
                     'cert-scanner/regression-certificates', 60, null],
                    [PERFORMANCE, 'CertScanner-GUI - performance', LOCAL, 'cert-scanner/performance-tests', 120, null]]
            testJobs[2].remoteJenkins() == 'jenkins-b'
            testJobs[2].parameters() == 'ENV=rd'
            [urbanCode.siteName(), urbanCode.deployProcess()] == [null, null]
            urbanCodeApplications*.applicationName() == ['CertScanner-GUI']
            urbanCodeApplications[0].components().collect { [it.componentName(), it.baseDir(), it.fileIncludePatterns()] } ==
                    [['CertScanner-GUI-app', 'gui/build/libs', '*.jar']]
            sshTargets == [(RD): SshTargetDto.builder().deployDir('/opt/ta/CertScanner/gui/deployment').build(),
                           (QC): SshTargetDto.builder().deployDir('/opt/ta/CertScanner/gui/deployment').build()]
        }
        with(api) {
            [build.tool(), build.buildPath(), build.command().tasks(), build.command().flags()] ==
                    [MAVEN, 'target/*.jar', ['clean', 'verify'], ['-B', '-U']]
            build.command().environment() == ['MAVEN_OPTS=-Xms512m -Xmx1g']
            [deployment.target(), deployment.appName(), deployment.artifactName()] ==
                    [OPENSHIFT, 'certscanner-api', 'certscanner-api.jar']
            with(openShiftTargets[RD]) {
                [projectBuild(), dockerRepoPush(), certDir(), nexusAuthFile(), projectDeployment(), configPath(),
                 routeHostname()] == ['ta-certscanner-build', 'docker-qc.tools.bbh.com/ta/certscanner-api',
                                      '/etc/pki/openshift', '/home/jenkins/.docker/nexus-auth.json', 'ta-certscanner-rd',
                                      'openshift/config-rd.yaml', 'certscanner-api-rd.apps.ocp-rd.testbbh.com']
            }
            openShiftTargets[QC].projectDeployment() == 'ta-certscanner-qc'
            sonar.command().tasks() == ['sonar:sonar']
        }
    }

    def "a rendered #type pipeline config names the pipeline's type and agents and leaves its jobs to the pipeline"() {
        given:
        def certScanner = product(services: [[name: 'gui', id: 10L, settings: MAVEN_VM]])
        Map config = builder.pipelineConfig(certScanner, certScanner.services()[0], pipeline(type: type,
                agentLabels: ['linux-agent', 'docker'], extendedPipelineJob: 'CERT/gui-extended',
                securityPipelineJob: 'CERT/gui-security'))

        when:
        def result = imported(new Yaml().dump(config))

        then:
        result.pipeline() == new ImportedPipeline(type, ['linux-agent', 'docker'])
        result.notes() == notes
        result.services()[0].notes() == serviceNotes
        asRun(rendered(result.services()[0])) == asRun(builder.productConfig(certScanner))

        where:
        type     || notes                                             | serviceNotes
        SECURITY || []                                                | [problem('jenkins.pipeline.extendedPipeline', PIPELINE)]
        EXTENDED || [problem('pipeline.securityPipeline', PIPELINE)] | []
        NEXUS_IQ || []                                                | []
    }

    def "a file of one project is named by the request or by its pipeline section, and its defaults fill in what it lacks"() {
        when:
        def named = imported('appId: 109f44ac-cc06-4ca0-884e-d944904f7019\nbuildTool: maven', 'gui')
        def fromPipeline = imported('''
            pipeline:
              type: full
              projectNames: api
            platform:
              jenkinsUrl: https://jenkins.bbh.com
              jenkinsLibrary: DevSecOpsJenkinsLibrary
            defaults:
              buildTool: maven
              sourceDir: app
              tests:
                maxParallel: 20
              coverage:
                minLine: 80
            appId: 109f44ac-cc06-4ca0-884e-d944904f7019
            deployTarget: openshift
            '''.stripIndent())

        then:
        named.services()*.name() == ['gui']
        named.services()[0].service().build().tool() == MAVEN
        fromPipeline.services()*.name() == ['api']
        fromPipeline.pipeline() == new ImportedPipeline(FULL, [])
        with(fromPipeline.services()[0].service()) {
            [build.tool(), build.sourceDir(), deployment.target()] == [MAVEN, 'app', OPENSHIFT]
        }
        fromPipeline.notes() == [problem('platform.jenkinsUrl', GLOBAL), problem('defaults.coverage.minLine', GLOBAL)]
        fromPipeline.services()[0].notes() == []
        imported('appId: 109f44ac-cc06-4ca0-884e-d944904f7019').services()[0].service().deployment().target() == VM
    }

    def "a value the portal cannot hold is left out with a note while the rest of the service is imported"() {
        when:
        def result = imported('''
            projects:
              gui:
                buildTool: ant
                appId: 109f44ac-cc06-4ca0-884e-d944904f7019
                build:
                  gradle:
                    tasks: clean build
                    env: [A=1]
                  maven:
                    goals: [verify]
                tests:
                  maxParallel: many
                  smoke:
                    required: perhaps
                    defaults:
                      token: s3cret-default
                    jobs:
                      - job: smoke
                        auth: {$class: TokenAuth, token: s3cret}
                        token: s3cret
                      - [nested]
                    urls: https://jenkins.bbh.com/job/smoke
                scm:
                  bitbucket:
                    url: https://bitbucket.bbh.com/scm/ta/cert.git
                    authType: token
                    reviewers: alice, bob
                tools:
                  sonar:
                    projectKey: [a, b]
                  nexusIq:
                    application:
                      cert-gui: {scanPatterns: ['**/*.jar'], stage: release, serverUrl: https://iq.other.com}
                      cert-api: {scanPatterns: ['**/*.war']}
                deploy:
                  vm:
                    rd:
                      nexusDeliveryEnabled: false
                custom: value
            other: value
            '''.stripIndent())
        def gui = result.services()[0].service()

        then:
        result.notes() == [problem('other', UNKNOWN)]
        result.services()[0].notes() as Set == [
                problem('buildTool', 'not imported: expected one of gradle, maven, flutter, liquibase, dotnet, sql, npm, pip, android, ios, msbuild'),
                problem('tests.maxParallel', 'not imported: expected a whole number'),
                problem('tests.smoke.required', 'not imported: expected true or false'),
                problem('tools.sonar.projectKey', 'not imported: expected a single value'),
                problem('build.gradle.env', 'not imported: expected a map of variables'),
                problem('build.maven', 'not imported: the service builds with gradle'),
                problem('tests.smoke.jobs[0].auth', 'not imported: expected CredentialsAuth with its credentials ID'),
                problem('tests.smoke.jobs[0].token', SECRET),
                problem('tests.smoke.jobs[1]', 'not imported: expected a map'),
                problem('tests.smoke.urls', 'not imported: expected a list'),
                problem('tests.smoke.defaults.token', SECRET),
                problem('tools.nexusIq.application.cert-gui.serverUrl', UNKNOWN),
                problem('tools.nexusIq.application.cert-api', 'not imported: ' + ONE_APPLICATION),
                problem('custom', UNKNOWN)] as Set
        !json.writeValueAsString(result).contains('s3cret')
        gui.build().tool() == GRADLE
        gui.build().command().tasks() == ['clean', 'build']
        gui.testJobs()*.job() == ['smoke']
        [gui.scm().authType(), gui.scm().reviewers()] == [BEARER, ['alice', 'bob']]
        gui.nexusIqApplications() == [new NexusIqApplicationDto('cert-gui', ['**/*.jar'], 'release', false)]
        gui.deployment().nexusDeliveryDisabled()
    }

    def "versions and dates stay text, YAML booleans and whole numbers keep their type"() {
        expect:
        ConfigFileParser.parse('version: 1.10\ndate: 2024-01-01\nskip: yes\ncount: 12\nratio: 0.5', json) ==
                [version: '1.10', date: '2024-01-01', skip: true, count: 12, ratio: '0.5']
    }

    def "content that is not a config file is refused at its line and column, never echoing the file: #content"() {
        when:
        imported(content)

        then:
        def e = thrown(InvalidRequestException)
        e.problems()*.field == ['content']
        e.problems()*.message == [message]
        !e.problems()*.message.join().contains('s3cret')

        where:
        content                                                                     || message
        'asoc:\n  keySecret: s3cret\n  keyId: [bbh_key\n'                           || 'line 4, column 1: is not valid YAML'
        'asoc:\n  keyId: s3cret\n  keyId: bbh_key\n'                                || 'line 3, column 3: holds a duplicate key'
        'projects:\n\tgui: {}\n'                                                    || 'line 2, column 1: is not valid YAML'
        'asoc:\n  keySecret: "ab\\uZZZZs3cret"\n'                                   || 'line 2, column 19: is not valid YAML'
        'asoc:\n  keySecret: "ab\\qs3cret"\n'                                       || 'line 2, column 18: is not valid YAML'
        '{"projects": {"gui": '                                                     || 'line 1, column 22: is not valid JSON'
        '- gui\n- api\n'                                                            || 'line 1, column 1: ' + NO_PROJECT
        'projects: []\n'                                                            || 'line 1, column 1: ' + NO_PROJECT
        'projects:\n  gui: s3cret\n'                                                || 'line 1, column 1: ' + NO_PROJECT
        'version: "3.9"\nservices:\n  web:\n    image: nginx\n'                     || 'line 1, column 1: ' + NO_PROJECT
        'spring:\n  application:\n    name: ledger\nserver:\n  port: 8080\n'        || 'line 1, column 1: ' + NO_PROJECT
        'projects:\n' + (0..MAX_PROJECTS).collect { "  p$it: {appId: a}\n" }.join() || 'holds more than 50 projects'
        'projects:\n  svc: &x\n    buildTool: gradle\n    custom: *x\n'             || SELF_REFERENCE
        'projects:\n  svc:\n    custom: &y {inner: {back: *y}}\n'                   || SELF_REFERENCE
        'a: &l [x, *l]\n'                                                           || SELF_REFERENCE
        'a: &a [x]\nb: [' + (['*a'] * 21).join(', ') + ']\n'                        || LIMITS
        'a: ' + '[' * 31 + ']' * 31                                                 || LIMITS
        'a: ' + 'x' * MAX_LENGTH                                                    || LIMITS
    }

    /** A Liquibase service with every database setting that is not the library's default. */
    static final String LEGACY_SWITCH = '''
            deployTarget: vm
            deploy:
              vm:
                rd:
                  nexusDeliveryEnabled: false
            '''.stripIndent()

    static final ServiceSettings ORDERS_DB = databaseSettings(
            build: new BuildSettings(LIQUIBASE, 'orders-db', '/usr/lib/jvm/java-17-openjdk', true, null, null),
            database: database().toBuilder().searchPath('src/main/resources').oracleVersion('23.4').defaultSchema('APP')
                    .liquibaseSchema('LB').contexts('rd, !test').labels('core').requireRollback(true).tag(false)
                    .lockWaitMinutes(10).compileCheck(false).recompile(false).ignoreObjects(['PKG_LEGACY'])
                    .ignoreFiles(['db/legacy/old.sql']).artifactId('orders-schema').mvnPath('/opt/maven')
                    .snapshotRepositoryId('own-snapshots').snapshotRepositoryUrl('https://nexus.bbh.com/own')
                    .testReset(true).utplsqlPaths('orders').utplsqlOwner('APP_OWNER').utplsqlTimeoutMinutes(60).build(),
            databaseTargets: databaseTargets() + [(DatabaseTarget.RD): DatabaseTargetSettings.builder()
                    .url('jdbc:oracle:thin:@rd-db1.bbh.com:1521,rd-db2.bbh.com:1521/ORDERS_RD').credentialsId('orders-rd')
                    .contexts('rd').labels('core').defaultSchema('APP').liquibaseSchema('LB').parameters(['a=1'])
                    .timeoutMinutes(45).build()],
            appScan: appScan(includedDirs: ['orders-db'], useConfigFile: true, insecureTls: true, clientPath: '/opt/appscan',
                    scanName: 'orders-db-sast'),
            sonar: SonarSettings.of('Orders DB', 'bbh-orders-db', ToolCommand.of([], ['-Dsonar.sources=db'])),
            scm: ScmSettings.of('https://bitbucket.bbh.com/projects/ORD/repos/orders', 'bitbucket-http-credentials'),
            tests: MAVEN_VM.tests(), testJobs: MAVEN_VM.testJobs(), metrics: MAVEN_VM.metrics())
    static final String BUILDS_LIQUIBASE = 'not imported: the service builds with liquibase'
    /** A Liquibase project with the keys of main's Liquibase support, a Node.js build, scans and a VM deployment. */
    static final String BILLING_DB = '''
        buildTool: liquibase
        sourceDir: db
        deployTarget: vm
        appName: billing
        appId: 109f44ac-cc06-4ca0-884e-d944904f7019
        staticAnalysis:
          parallel: true
        build:
          liquibase:
            commands: updateSQL
            flags: [--changelog-file=db/changelog/db.changelog-master.xml]
          node:
            enabled: true
            dir: web
        tests:
          unitTests:
            liquibase:
              commands: validate
            unitTestResult: '**/utplsql/*.xml'
        asoc:
          liquibase:
            commands: updateSQL
        dast:
          enabled: true
          targetUrl: https://billing.rd.bbh.com
        tools:
          sonar:
            projectKey: com.bbh:billing-db
            liquibase:
              sources: [db/changelog, db/plsql]
          nexusIq:
            application: billing-db
            scanPatterns: ['**/*.jar']
        goldenFix:
          enabled: false
        deploy:
          vm:
            rd:
              host: rdltaapps9.testbbh.com
        '''.stripIndent()
    static final Map ORDERS = [buildTool: 'liquibase', javaPath: '/usr/lib/jvm/java-17-openjdk',
                               database : [changelogFile: 'db/changelog-master.xml']]

    private void useDatabaseDefaults() {
        global = copy(globalSettings(), database: databaseDefaults())
        builder = new DsoConfigBuilder(global)
        reader = new ServiceConfigReader(global, json)
    }

    private static Map merged(Map base, Map own) {
        (base.keySet() + own.keySet()).collectEntries { key ->
            [key, base[key] instanceof Map && own[key] instanceof Map ? merged(base[key] as Map, own[key] as Map)
                    : own.containsKey(key) ? own[key] : base[key]]
        }
    }

    private ConfigImportResponse imported(String content, String serviceName = null) {
        reader.read(ConfigFileParser.parse(content, json), serviceName)
    }

    private Map asRun(Map config) {
        projectsAsRun(config, global.defaultsConfig())
    }

    private static ServiceSettings gradleOpenShift(boolean nexusDeliveryDisabled) {
        settings(build: build(tool: GRADLE, autoSetup: true, buildPath: 'build/libs/*.jar', command: fullCommand('build')),
                unitTests: MAVEN_VM.unitTests(), tests: MAVEN_VM.tests(), testJobs: MAVEN_VM.testJobs(),
                deployment: deployment(target: OPENSHIFT, appName: 'cert', artifactName: 'cert.jar',
                        nexusDeliveryDisabled: nexusDeliveryDisabled),
                openShiftTargets: MAVEN_VM.openShiftTargets(), appScan: MAVEN_VM.appScan(), sonar: MAVEN_VM.sonar(),
                nexusIq: MAVEN_VM.nexusIq(), nexusIqApplications: MAVEN_VM.nexusIqApplications(), scm: MAVEN_VM.scm(),
                goldenFix: MAVEN_VM.goldenFix(), metrics: MAVEN_VM.metrics(), nodeBuild: MAVEN_VM.nodeBuild(),
                versionCatalogs: MAVEN_VM.versionCatalogs())
    }

    private Map rendered(ImportedService imported) {
        builder.productConfig(product(services: [[name: imported.name(), settings: imported.service().toDraft().settings()]]))
    }

    private static String dotnetBlock(String path) {
        "imported as ${path}.dotnet: the library reads ${path}.dotnet for buildTool dotnet and msbuild alike"
    }

    /** The value at a dotted key, as nested maps: at('build.dotnet', [commands: ['x']]) is [build: [dotnet: [commands: ['x']]]]. */
    private static Map at(String key, Object value) {
        key.split('\\.').reverse().inject(value) { inner, name -> [(name): inner] } as Map
    }

    /** The command of the service that the import fills from the command block under a path, such as build. */
    private static commandAt(ServiceDto service, String path) {
        switch (path) {
            case 'build': return service.build().command()
            case 'tests.unitTests': return service.unitTests().command()
            case 'tools.sonar': return service.sonar().command()
            case 'asoc': return service.appScan().compileCommand()
            default: throw new IllegalArgumentException(path)
        }
    }

    private static InvalidRequestException.FieldProblem problem(String field, String message) {
        new InvalidRequestException.FieldProblem(field, message)
    }
}
