package com.bbh.itss.coe.oneview.domain.catalog

import spock.lang.Specification

import static com.bbh.itss.coe.oneview.domain.catalog.BitbucketAuthType.BASIC
import static com.bbh.itss.coe.oneview.domain.catalog.BitbucketAuthType.BEARER
import static com.bbh.itss.coe.oneview.domain.catalog.BitbucketType.CLOUD
import static com.bbh.itss.coe.oneview.domain.catalog.BitbucketType.SERVER
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.ANDROID
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.DOTNET
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.FLUTTER
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.GRADLE
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.IOS
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.LIQUIBASE
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.MAVEN
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.MSBUILD
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.NPM
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.PIP
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.SQL
import static com.bbh.itss.coe.oneview.domain.catalog.DeployTarget.OPENSHIFT
import static com.bbh.itss.coe.oneview.domain.catalog.DeployTarget.VM
import static com.bbh.itss.coe.oneview.domain.catalog.MetricsSettings.DEFAULTS
import static com.bbh.itss.coe.oneview.domain.catalog.ToolCommand.NONE
import static com.bbh.itss.coe.oneview.domain.shared.Sections.messages
import static com.bbh.itss.coe.oneview.domain.shared.Sections.problems
import static com.bbh.itss.coe.oneview.domain.shared.Sections.reported
import static com.bbh.itss.coe.oneview.domain.shared.Sections.written

class ConfigSectionsSpec extends Specification {

    static final String APP_ID = '109f44ac-cc06-4ca0-884e-d944904f7019'
    static final String REPO = 'https://bitbucket.bbh.com/scm/ta/cert.git'
    /** A Liquibase step of a Gradle, Android, Maven or .NET service, and the block the library reads for it. */
    static final ToolCommand STEP = ToolCommand.builder().tasks(['validate', 'updateSQL'])
            .flags(['--changelog-file=db/changelog.xml', '--url=offline:oracle']).directory(' db ')
            .environment(['LIQUIBASE_LOG_LEVEL=INFO']).label(' Changelog ').returnStdout(true).build()
    static final Map STEP_BLOCK = [commands: ['validate', 'updateSQL'],
                                   flags   : ['--changelog-file=db/changelog.xml', '--url=offline:oracle'], dir: 'db',
                                   env     : [LIQUIBASE_LOG_LEVEL: 'INFO'], label: 'Changelog', returnStdout: true]
    static final String VERSION_MESSAGE = "must be a SonarScanner version such as 5.0.1.3006: at most 50 letters," +
            " digits, '.', '_' and '-'"

    def "build settings default the source directory, trim the paths and start without a command"() {
        when:
        def build = new BuildSettings(MAVEN, ' ', ' /opt/jdk-17 ', null, ' target/cert.jar ', null)

        then:
        build == new BuildSettings(MAVEN, '.', '/opt/jdk-17', false, 'target/cert.jar', NONE)
        written(build) == [buildTool: 'maven', sourceDir: '.', javaPath: '/opt/jdk-17', build: [buildPath: 'target/cert.jar']]
        new BuildSettings(GRADLE, null, ' ', false, ' ', null).javaPath() == null
        new BuildSettings(GRADLE, null, null, false, ' ', null).buildPath() == null
    }

    def "a Maven build writes its goals, flags, directory, Maven installation and environment under build.maven"() {
        given:
        def command = ToolCommand.builder().tasks(['clean', 'verify']).flags(['-B', '-s', 'settings.xml'])
                .directory(' gui ').mavenHome(' /opt/maven ').environment(['MAVEN_OPTS=-Xmx1g']).build()

        expect:
        written(new BuildSettings(MAVEN, 'gui', '/jdk', false, null, command)) ==
                [buildTool: 'maven', sourceDir: 'gui', javaPath: '/jdk',
                 build    : [maven: [goals : ['clean', 'verify'], flags: ['-B', '-s', 'settings.xml'], dir: 'gui',
                                     mvnPath: '/opt/maven', env: [MAVEN_OPTS: '-Xmx1g']]]]
    }

    def "automatic build tool setup is written only when it is enabled, and a Flutter build writes no command"() {
        expect:
        written(new BuildSettings(GRADLE, 'app', null, true, null, ToolCommand.of(['build'], []))) ==
                [buildTool: 'gradle', sourceDir: 'app', buildToolAutoSetup: true, build: [gradle: [tasks: ['build']]]]
        written(new BuildSettings(FLUTTER, null, null, false, 'build/app.apk', ToolCommand.of(['assemble'], ['-q']))) ==
                [buildTool: 'flutter', sourceDir: '.', build: [buildPath: 'build/app.apk']]
        written(new BuildSettings(LIQUIBASE, 'sql', '/jdk', false, null, ToolCommand.of(['updateSQL'], ['--changelog-file=changelog.xml']))) ==
                [buildTool: 'liquibase', sourceDir: 'sql', javaPath: '/jdk',
                 build    : [liquibase: [commands: ['updateSQL'], flags: ['--changelog-file=changelog.xml']]]]
    }

    def "a #tool build is written as buildTool #name with its commands under build.dotnet, which the library reads for both"() {
        expect:
        written(new BuildSettings(tool, 'src', null, false, null, ToolCommand.of(['msbuild Orders.sln /t:Build'], ['/m']))) ==
                [buildTool: name, sourceDir: 'src', build: [dotnet: [commands: ['msbuild Orders.sln /t:Build'], flags: ['/m']]]]

        where:
        tool    || name
        DOTNET  || 'dotnet'
        MSBUILD || 'msbuild'
    }

    def "a #tool build with java path #javaPath, auto setup #autoSetup and tasks #tasks reports #fields"() {
        expect:
        problems(new BuildSettings(tool, null, javaPath, autoSetup, null, ToolCommand.of(tasks, []))) == fields

        where:
        tool      | javaPath | autoSetup | tasks         || fields
        GRADLE    | null     | false     | ['build']     || ['javaPath']
        MAVEN     | null     | null      | ['verify']    || ['javaPath']
        GRADLE    | '/jdk'   | false     | []            || ['command.tasks']
        MAVEN     | null     | true      | []            || ['command.tasks']
        GRADLE    | null     | false     | []            || ['javaPath', 'command.tasks']
        GRADLE    | '/jdk'   | false     | ['build']     || []
        MAVEN     | null     | true      | ['verify']    || []
        LIQUIBASE | null     | false     | ['updateSQL'] || []
        LIQUIBASE | '/jdk'   | false     | []            || []
        FLUTTER   | null     | false     | []            || ['javaPath']
        FLUTTER   | '/jdk'   | true      | []            || ['autoSetup']
        FLUTTER   | null     | true      | []            || ['javaPath', 'autoSetup']
        FLUTTER   | '/jdk'   | false     | []            || []
        DOTNET    | null     | false     | []            || []
        MSBUILD   | null     | false     | []            || []
        MSBUILD   | null     | false     | ['msbuild']   || []
    }

    def "build settings start without a Liquibase step, and keep the one they are given"() {
        expect:
        new BuildSettings(GRADLE, null, '/jdk', false, null, null).liquibase() == NONE
        new BuildSettings(GRADLE, null, '/jdk', false, null, null, null).liquibase() == NONE
        new BuildSettings(GRADLE, null, '/jdk', false, null, null, STEP).liquibase() == STEP
        new BuildSettings(GRADLE, null, '/jdk', false, null, null, STEP).liquibase().directory() == 'db'
    }

    def "the Liquibase step after a #tool build is written under build.liquibase, after the command of the build"() {
        when:
        def build = written(new BuildSettings(tool, null, '/jdk', false, 'out/app.zip', ToolCommand.of([task], []), STEP))

        then:
        build.buildTool == name
        build.build.keySet() as List == ['buildPath', block, 'liquibase']
        build.build.liquibase == STEP_BLOCK

        where:
        tool    || name      | block    | task
        GRADLE  || 'gradle'  | 'gradle' | 'build'
        MAVEN   || 'maven'   | 'maven'  | 'verify'
        ANDROID || 'android' | 'gradle' | 'assembleRelease'
        DOTNET  || 'dotnet'  | 'dotnet' | 'dotnet build -c Release'
        MSBUILD || 'msbuild' | 'dotnet' | 'msbuild Orders.sln /t:Build'
    }

    def "a #tool build writes no Liquibase step and refuses one, since the library runs none after it"() {
        given:
        def build = new BuildSettings(tool, null, '/jdk', false, null, NONE, ToolCommand.of(['validate'], []))

        expect:
        written(build).build?.liquibase == null
        reported(build).findAll { it.field.startsWith('liquibase') }.collect { [it.field, it.message] } ==
                [['liquibase.tasks', "must be empty for ${name}: the library runs a Liquibase step only after a Gradle,".toString()
                        + ' Android, Maven or .NET build']]
        problems(new BuildSettings(tool, null, '/jdk', false, null, NONE, NONE)).findAll { it.startsWith('liquibase') } == []

        where:
        tool      || name
        FLUTTER   || 'Flutter'
        LIQUIBASE || 'Liquibase'
        SQL       || 'SQL and PL/SQL files'
        NPM       || 'npm'
        PIP       || 'pip (Python)'
        IOS       || 'iOS'
    }

    def "a Liquibase step after a #tool build that sets #description reports #fields"() {
        expect:
        reported(new BuildSettings(tool, null, '/jdk', false, null, ToolCommand.of([task], []), step))
                .collect { [it.field, it.message] } == fields

        where:
        description               | tool    | task           | step                                                                     || fields
        'commands'                | GRADLE  | 'build'        | ToolCommand.of(['update-testing-rollback'], [])                          || []
        'flags only'              | MAVEN   | 'verify'       | ToolCommand.of([], ['--changelog-file=db/changelog.xml'])                || []
        'every key'               | ANDROID | 'assemble'     | STEP                                                                     || []
        'commands'                | DOTNET  | 'dotnet build' | ToolCommand.of(['validate'], [])                                         || []
        'only a step label'       | GRADLE  | 'build'        | ToolCommand.builder().label('Changelog').build()                         || [['liquibase.tasks', 'add Liquibase commands or flags: without them the library skips the step']]
        'only a folder'           | MSBUILD | 'msbuild'      | ToolCommand.builder().directory('db').build()                            || [['liquibase.tasks', 'add Liquibase commands or flags: without them the library skips the step']]
        'a Maven home'            | MAVEN   | 'verify'       | ToolCommand.builder().tasks(['validate']).mavenHome('/opt/maven').build() || [['liquibase.mavenHome', 'must be empty: the Liquibase step runs the liquibase command, not Maven']]
        'too many commands'       | GRADLE  | 'build'        | ToolCommand.of(['update-testing-rollback'] * 50, [])                    || [['liquibase.tasks', 'is too long: all entries together may take at most 1000 bytes']]
    }

    def "OpenShift deployment needs the application name, and the artifact name while it delivers to Nexus"() {
        expect:
        problems(new DeploymentSettings(OPENSHIFT, ' ', null, 'gui-1.0.jar', null)) == ['appName', 'artifactName']
        problems(new DeploymentSettings(OPENSHIFT, 'gui', null, null, false)) == ['artifactName']
        problems(new DeploymentSettings(OPENSHIFT, null, 'gui.jar', null, null)) == ['appName']
        problems(new DeploymentSettings(OPENSHIFT, 'gui', 'gui.jar', null, null)) == []
        problems(new DeploymentSettings(OPENSHIFT, ' ', null, null, true)) == ['appName']
        problems(new DeploymentSettings(VM, null, null, null, null)) == []
        messages(new DeploymentSettings(OPENSHIFT, null, null, null, null)) ==
                ['is required for OpenShift deployment', 'is required for OpenShift deployment']
    }

    def "deployment settings write the target, the names and the base artifact name, and deliver to Nexus by default"() {
        when:
        def deployment = new DeploymentSettings(OPENSHIFT, ' gui ', ' gui.jar ', ' gui-1.0.0.jar ', true)

        then:
        written(deployment) == [deployTarget    : 'openshift', appName: 'gui', artifactName: 'gui.jar',
                                baseArtifactName: 'gui-1.0.0.jar']
        written(new DeploymentSettings(VM, ' ', '', ' ', null)) == [deployTarget: 'vm']
        new DeploymentSettings(VM, ' ', '', ' ', null) == new DeploymentSettings(VM, null, null, null, false)
        [deployment.deliversToNexus(OPENSHIFT), deployment.deliversToNexus(VM)] == [false, false]
        new DeploymentSettings(VM, null, null, null, null).with { [deliversToNexus(VM), deliversToNexus(OPENSHIFT)] } ==
                [true, false]
    }

    def "AppScan settings store the application ID in lower case and start at the library's defaults"() {
        when:
        def appScan = new AppScanSettings(' 109F44AC-CC06-4CA0-884E-D944904F7019 ', [' src ', '', 'src', null], null,
                null, null, null, null, ' ', null, null, ' ', ' ', ' ')

        then:
        appScan == new AppScanSettings(APP_ID, ['src'], [], true, false, false, false, null, NONE, false, null, null,
                null)
        written { appScan.writeTo(it, GRADLE) } ==
                [appId: APP_ID, includedDirs: 'src', dast: [enabled: false]]
        written { AppScanSettings.of(APP_ID).writeTo(it, MAVEN) } ==
                [appId: APP_ID, dast: [enabled: false]]
        AppScanSettings.of(null).applicationId() == null
    }

    def "every AppScan option that differs from the library's default is written"() {
        given:
        def compileCommand = ToolCommand.builder().tasks(['compileJava']).flags(['--offline']).directory('gui')
                .mavenHome('/opt/maven').environment(['JAVA_OPTS=-Xmx2g']).build()
        def appScan = new AppScanSettings(APP_ID, ['src/main', ' lib '], ['src/test'], false, true, true, true,
                ' /opt/appscan ', compileCommand, true, ' https://rdl1.testbbh.com ', ' p-1 ', ' cert-gui-sast ')

        expect:
        written { appScan.writeTo(it, GRADLE) } ==
                [appId      : APP_ID, includedDirs: 'src/main,lib', excludedDirs: 'src/test', appscanPath: '/opt/appscan',
                 asoc       : [doCompile: false, sourceCodeOnly: true,
                               useAppScanConfig: true, insecureTls: true,
                               gradle   : [tasks: ['compileJava'], flags: ['--offline'], dir: 'gui',
                                           env  : [JAVA_OPTS: '-Xmx2g']]],
                 sast       : [scanName: 'cert-gui-sast'],
                 dast       : [enabled: true, targetUrl: 'https://rdl1.testbbh.com', presenceId: 'p-1']]
    }

    def "the AppScan compile command goes under asoc.#key for #tool"() {
        given:
        def appScan = AppScanSettings.builder().applicationId(APP_ID)
                .compileCommand(ToolCommand.builder().tasks(['compile']).mavenHome('/opt/maven').build()).build()

        expect:
        written { appScan.writeTo(it, tool) }.asoc == asoc

        where:
        tool      || key         | asoc
        GRADLE    || 'gradle'    | [gradle: [tasks: ['compile']]]
        MAVEN     || 'maven'     | [maven: [goals: ['compile'], mvnPath: '/opt/maven']]
        LIQUIBASE || 'liquibase' | [liquibase: [commands: ['compile']]]
        DOTNET    || 'dotnet'    | [dotnet: [commands: ['compile']]]
        MSBUILD   || 'dotnet'    | [dotnet: [commands: ['compile']]]
        FLUTTER   || 'none'      | null
    }

    def "every service needs its AppScan application ID, and enabled DAST needs a target URL"() {
        expect:
        reported { dast(null).validate(it, GRADLE) }.collect { [it.field, it.message] } ==
                [['dastTargetUrl', 'is required when DAST is enabled']]
        problems { dast('https://x').validate(it, GRADLE) } == []
        problems { AppScanSettings.of(APP_ID).validate(it, MAVEN) } == []
        reported { AppScanSettings.of(' ').validate(it, MAVEN) }.collect { [it.field, it.message] } ==
                [['applicationId', 'is required']]
    }

    def "a #tool compile command that sets #description reports #fields"() {
        given:
        def appScan = AppScanSettings.builder().applicationId(APP_ID).compile(compile)
                .compileCommand(command).build()

        expect:
        problems { appScan.validate(it, tool) } == fields

        where:
        description              | tool      | compile | command                                                           || fields
        'nothing'                | GRADLE    | true    | NONE                                                              || []
        'only a step label'      | GRADLE    | true    | ToolCommand.builder().label('Compile').build()                    || ['compileCommand.tasks']
        'only returnStdout'      | MAVEN     | true    | ToolCommand.builder().returnStdout(true).build()                  || ['compileCommand.tasks']
        'only a step label'      | LIQUIBASE | true    | ToolCommand.builder().label('Compile').build()                    || []
        'a label, not compiling' | GRADLE    | false   | ToolCommand.builder().label('Compile').build()                    || []
        'a label'                | FLUTTER   | true    | ToolCommand.builder().label('Compile').build()                    || []
        'tasks and a label'      | GRADLE    | true    | ToolCommand.builder().tasks(['classes']).label('Compile').build() || []
    }

    def "AppScan settings start without a Liquibase step of the compile, and keep the one they are given"() {
        expect:
        AppScanSettings.of(APP_ID).liquibaseCompileCommand() == NONE
        new AppScanSettings(APP_ID, null, null, null, null, null, null, null, null, null, null, null, null)
                .liquibaseCompileCommand() == NONE
        AppScanSettings.builder().applicationId(APP_ID).liquibaseCompileCommand(STEP).build().liquibaseCompileCommand() ==
                STEP
    }

    def "the Liquibase step after the AppScan compile of a #tool service is written under asoc.liquibase, after the compile command"() {
        given:
        def appScan = AppScanSettings.builder().applicationId(APP_ID).compileCommand(ToolCommand.of(['compile'], []))
                .liquibaseCompileCommand(STEP).build()

        expect:
        written { appScan.writeTo(it, tool) }.asoc == [(block): [(key): ['compile']], liquibase: STEP_BLOCK]
        written { AppScanSettings.builder().applicationId(APP_ID).compile(false).liquibaseCompileCommand(STEP).build()
                .writeTo(it, tool) }.asoc == [doCompile: false, liquibase: STEP_BLOCK]

        where:
        tool   || block    | key
        GRADLE || 'gradle' | 'tasks'
        MAVEN  || 'maven'  | 'goals'
    }

    def "the AppScan compile of a #tool service writes no Liquibase step and refuses one, since the library runs none after it"() {
        given:
        def appScan = AppScanSettings.builder().applicationId(APP_ID).liquibaseCompileCommand(STEP).build()

        expect:
        written { appScan.writeTo(it, tool) }.asoc?.liquibase == null
        reported { appScan.validate(it, tool) }.collect { [it.field, it.message] } ==
                [['liquibaseCompileCommand.tasks', "must be empty for ${name}: the library runs a Liquibase step of the".toString()
                        + ' scan only after the Gradle or Maven compile']]

        where:
        tool      || name
        ANDROID   || 'Android'
        DOTNET    || 'C# / .NET'
        MSBUILD   || 'C# / .NET Framework (MSBuild)'
        FLUTTER   || 'Flutter'
        LIQUIBASE || 'Liquibase'
        NPM       || 'npm'
        PIP       || 'pip (Python)'
        IOS       || 'iOS'
    }

    def "a Liquibase step after the AppScan compile of a #tool service that sets #description reports #fields"() {
        given:
        def appScan = AppScanSettings.builder().applicationId(APP_ID).compileCommand(ToolCommand.of(['compile'], []))
                .liquibaseCompileCommand(step).build()

        expect:
        reported { appScan.validate(it, tool) }.collect { [it.field, it.message] } == fields

        where:
        description         | tool   | step                                                                       || fields
        'commands'          | GRADLE | ToolCommand.of(['updateSQL'], [])                                          || []
        'every key'         | MAVEN  | STEP                                                                       || []
        'only the variables' | GRADLE | ToolCommand.builder().environment(['LIQUIBASE_OUTPUT_FILE=db.sql']).build() || [['liquibaseCompileCommand.tasks', 'add Liquibase commands or flags: without them the library skips the step']]
        'a Maven home'      | MAVEN  | ToolCommand.builder().flags(['--url=offline:oracle']).mavenHome('/opt/maven').build() || [['liquibaseCompileCommand.mavenHome', 'must be empty: the Liquibase step runs the liquibase command, not Maven']]
    }

    def "SonarQube settings trim their values and switch the badges off by default"() {
        when:
        def sonar = new SonarSettings(' ', ' cert ', ' ', ' ', ' ', ' ', null, null, null, null, ' ')

        then:
        sonar == new SonarSettings(null, 'cert', null, null, null, null, false, false, NONE, null, null)
        SonarSettings.of(' CertScanner ', 'cert', null) == new SonarSettings('CertScanner', 'cert', null, null, null, null,
                false, false, NONE, null, null)
        SonarSettings.NONE == new SonarSettings(' ', '', null, null, null, null, false, false, null, null, null)
    }

    def "SonarQube settings write every tools.sonar key with the analysis command of the build tool"() {
        given:
        def sonar = new SonarSettings(' CertScanner ', ' cert-scanner ', ' SonarQube BBH ', ' sonar-creds ',
                ' sonar-token ', ' sqb_1a2b ', true, true, ToolCommand.of(['sonar:sonar'], ['-Dsonar.branch.name=main']), null,
                null)

        expect:
        written { sonar.writeTo(it, MAVEN) } ==
                [tools: [sonar: [projectName     : 'CertScanner', projectKey: 'cert-scanner',
                                 installationName: 'SonarQube BBH', credentialsId: 'sonar-creds',
                                 authToken       : 'sonar-token', badgeToken: 'sqb_1a2b', addBadges: true, fullBadges: true,
                                 maven           : [goals: ['sonar:sonar'], flags: ['-Dsonar.branch.name=main']]]]]
        written { sonar.writeTo(it, GRADLE) }.tools.sonar.gradle == [tasks: ['sonar:sonar'], flags: ['-Dsonar.branch.name=main']]
        written { sonar.writeTo(it, LIQUIBASE) }.tools.sonar.liquibase == [commands: ['sonar:sonar'], flags: ['-Dsonar.branch.name=main']]
        written { sonar.writeTo(it, MSBUILD) }.tools.sonar.dotnet == [commands: ['sonar:sonar'], flags: ['-Dsonar.branch.name=main']]
        written { sonar.writeTo(it, FLUTTER) }.tools.sonar.keySet() as List ==
                ['projectName', 'projectKey', 'installationName', 'credentialsId', 'authToken', 'badgeToken', 'addBadges',
                 'fullBadges']
        written { SonarSettings.of('CertScanner', 'cert', null).writeTo(it, GRADLE) } ==
                [tools: [sonar: [projectName: 'CertScanner', projectKey: 'cert']]]
        written { SonarSettings.NONE.writeTo(it, GRADLE) } == [:]
    }

    def "SonarQube settings trim the SonarScanner version and start without one"() {
        expect:
        SonarSettings.builder().sonarScannerVersion(' 5.0.1.3006 ').build().sonarScannerVersion() == '5.0.1.3006'
        SonarSettings.builder().sonarScannerVersion(' ').build() == SonarSettings.NONE
        new SonarSettings(null, 'cert', null, null, null, null, null, null, null, null, null).sonarScannerVersion() == null
        SonarSettings.of('Cert', 'cert', null).sonarScannerVersion() == null
    }

    def "the SonarScanner version of a #tool service is written under tools.sonar, after the analysis command"() {
        given:
        def sonar = SonarSettings.builder().projectKey('cert').command(command).sonarScannerVersion('5.0.1.3006').build()

        when:
        def written = written { sonar.writeTo(it, tool) }.tools.sonar

        then:
        written.keySet() as List == keys
        written.sonarScannerVersion == version

        where:
        tool      | command                                    || keys                                              | version
        GRADLE    | ToolCommand.of(['sonar'], [])              || ['projectKey', 'gradle', 'sonarScannerVersion']    | '5.0.1.3006'
        MAVEN     | ToolCommand.of(['sonar:sonar'], [])        || ['projectKey', 'maven', 'sonarScannerVersion']     | '5.0.1.3006'
        ANDROID   | ToolCommand.of(['sonar'], [])              || ['projectKey', 'gradle', 'sonarScannerVersion']    | '5.0.1.3006'
        LIQUIBASE | ToolCommand.of([], ['-Dsonar.sources=db']) || ['projectKey', 'liquibase', 'sonarScannerVersion'] | '5.0.1.3006'
        SQL       | ToolCommand.of([], ['-Dsonar.sources=db']) || ['projectKey', 'liquibase', 'sonarScannerVersion'] | '5.0.1.3006'
        NPM       | ToolCommand.of([], ['-Dsonar.sources=src']) || ['projectKey', 'npm', 'sonarScannerVersion']      | '5.0.1.3006'
        PIP       | ToolCommand.of([], ['-Dsonar.sources=src']) || ['projectKey', 'pip', 'sonarScannerVersion']      | '5.0.1.3006'
        IOS       | ToolCommand.of([], ['-Dsonar.sources=App']) || ['projectKey', 'ios', 'sonarScannerVersion']      | '5.0.1.3006'
        FLUTTER   | ToolCommand.NONE                           || ['projectKey']                                    | null
        DOTNET    | ToolCommand.NONE                           || ['projectKey']                                    | null
        MSBUILD   | ToolCommand.NONE                           || ['projectKey']                                    | null
    }

    def "a SonarScanner version #version of a #tool service reports #fields"() {
        expect:
        reported { SonarSettings.builder().sonarScannerVersion(version).build().validate(it, tool) }
                .collect { [it.field, it.message] } == fields

        where:
        tool      | version      || fields
        GRADLE    | '5.0.1.3006' || []
        MAVEN     | '4.8.0.2856' || []
        LIQUIBASE | '5.0.1.3006' || []
        SQL       | '5.0'        || []
        FLUTTER   | '5.0.1.3006' || []
        NPM       | 'latest'     || []
        IOS       | 'cli_6-2'    || []
        PIP       | null         || []
        GRADLE    | '5.0 beta'   || [['sonarScannerVersion', VERSION_MESSAGE]]
        MAVEN     | '../5.0'     || [['sonarScannerVersion', VERSION_MESSAGE]]
        LIQUIBASE | '5' * 51     || [['sonarScannerVersion', VERSION_MESSAGE]]
        DOTNET    | '5.0.1.3006' || [['sonarScannerVersion', 'must be empty for .NET: the library analyses a .NET service with the SonarScanner for .NET, which does not read it']]
        MSBUILD   | '5.0 beta'   || [['sonarScannerVersion', 'must be empty for .NET: the library analyses a .NET service with the SonarScanner for .NET, which does not read it']]
        DOTNET    | null         || []
    }

    def "the SonarQube badges read the badge token or the credential holding it, not both"() {
        given:
        def badge = { String token, String credentialsId ->
            SonarSettings.builder().badgeToken(token).badgeCredentialsId(credentialsId).build()
        }

        expect:
        written { badge(null, ' sonar-badge ').writeTo(it, GRADLE) } == [tools: [sonar: [badgeCredentialsId: 'sonar-badge']]]
        problems { badge('sqb_1a2b', null).validate(it, GRADLE) } == []
        problems { badge(null, 'sonar-badge').validate(it, GRADLE) } == []
        reported { badge('sqb_1a2b', 'sonar-badge').validate(it, GRADLE) }.collect { [it.field, it.message] } ==
                [['badgeCredentialsId', 'give the badge token or its credentials ID, not both']]
    }

    def "a #tool SonarQube project with key #key and tasks #tasks reports #fields"() {
        expect:
        problems { SonarSettings.of(null, key, ToolCommand.of(tasks, [])).validate(it, tool) } == fields

        where:
        tool      | key    | tasks           || fields
        GRADLE    | 'cert' | []              || ['command.tasks']
        MAVEN     | 'cert' | []              || ['command.tasks']
        LIQUIBASE | 'cert' | []              || []
        GRADLE    | null   | []              || []
        FLUTTER   | 'cert' | []              || []
        MAVEN     | 'cert' | ['sonar:sonar'] || []
        GRADLE    | 'cert' | ['sonarqube']   || []
        LIQUIBASE | 'cert' | ['sonar']       || []
        DOTNET    | 'cert' | []              || []
        MSBUILD   | 'cert' | []              || []
    }

    def "a SonarQube command that sets only a step label or the output switch needs its tasks without a project key too"() {
        expect:
        problems { SonarSettings.of(null, null, ToolCommand.builder().label('Analyse').build()).validate(it, GRADLE) } ==
                ['command.tasks']
        problems { SonarSettings.of(null, null, ToolCommand.builder().returnStdout(true).build()).validate(it, MAVEN) } ==
                ['command.tasks']
    }

    def "a missing #tool command of the #stage is explained in the words of the build tool"() {
        expect:
        messages(validation) == [message]

        where:
        tool     | stage      | validation                                                                        || message
        'Maven'  | 'build'    | new BuildSettings(MAVEN, null, '/jdk', false, null, null)                         || 'add the Maven goals of the build, for example clean verify'
        'Gradle' | 'build'    | new BuildSettings(GRADLE, null, '/jdk', false, null, null)                        || 'add the Gradle tasks of the build, for example clean build'
        'JDK'    | 'build'    | new BuildSettings(GRADLE, null, null, false, null, ToolCommand.of(['build'], [])) || 'set the JDK path or enable automatic build tool setup, the unit tests stage needs one of them'
        'Maven'  | 'analysis' | { SonarSettings.of(null, 'cert', null).validate(it, MAVEN) }                      || 'add the Maven goals of the analysis, for example sonar:sonar'
        'Gradle' | 'analysis' | { SonarSettings.of(null, 'cert', null).validate(it, GRADLE) }                     || 'add the Gradle tasks of the analysis, for example sonarqube'
    }

    def "a Nexus IQ application keeps each scan pattern once and defaults the stage to build"() {
        expect:
        new NexusIqApplication(' cert ', ['**/*.jar', ' ', '**/*.jar', ' **/*.war '], ' ', null) ==
                new NexusIqApplication('cert', ['**/*.jar', '**/*.war'], 'build', false)
        NexusIqApplication.of(' cert ', null) == new NexusIqApplication('cert', [], 'build', false)
        new NexusIqSettings(' ', ' ') == NexusIqSettings.NONE
    }

    def "one Nexus IQ application is written in the string form, with the server override"() {
        expect:
        written {
            new NexusIqSettings(' https://iq.bbh.com ', null)
                    .writeTo(it, [new NexusIqApplication('cert', ['**/*.jar'], ' stage-release ', true)])
        } == [tools: [nexusIq: [serverUrl: 'https://iq.bbh.com', application: 'cert', scanPatterns: ['**/*.jar'],
                                stage    : 'stage-release', failOnNetworkError: true]]]
        written { NexusIqSettings.NONE.writeTo(it, []) } == [:]
    }

    def "the applications of a service stored before the one-application rule are written in the map form, each with the server and credentials in effect"() {
        given:
        def applications = [NexusIqApplication.of('cert-gui', ['**/gui/*.jar']),
                            new NexusIqApplication('cert-batch', ['**/batch/*.jar'], 'release', true)]

        when:
        def nexusIq = written {
            it.set('tools.nexusIq.serverUrl', 'https://tools.bbh.com/IQ').set('tools.nexusIq.credentialsId', 'nexusiqP')
            new NexusIqSettings(null, 'cert-iq').writeTo(it, applications)
        }.tools.nexusIq

        then:
        nexusIq == [serverUrl  : 'https://tools.bbh.com/IQ', credentialsId: 'cert-iq',
                    application: ['cert-gui'  : [scanPatterns: ['**/gui/*.jar'], serverUrl: 'https://tools.bbh.com/IQ',
                                                 credentialsId: 'cert-iq', stage: 'build', failOnNetworkError: false],
                                  'cert-batch': [scanPatterns: ['**/batch/*.jar'], serverUrl: 'https://tools.bbh.com/IQ',
                                                 credentialsId: 'cert-iq', stage: 'release', failOnNetworkError: true]]]
    }

    def "a service scans one Nexus IQ application, which needs a name and scan patterns"() {
        expect:
        reported { NexusIqSettings.NONE.validate(it, [NexusIqApplication.of(' ', [])]) }.collect { [it.field, it.message] } ==
                [['nexusIqApplications[0].application', 'must not be blank'],
                 ['nexusIqApplications[0].scanPatterns', 'add at least one scan pattern for the Nexus IQ application']]
        reported {
            NexusIqSettings.NONE.validate(it, [NexusIqApplication.of('cert', ['**/*.jar']), NexusIqApplication.of('cert', [])])
        }.collect { [it.field, it.message] } ==
                [['nexusIqApplications', 'a service scans one Nexus IQ application'],
                 ['nexusIqApplications[1].scanPatterns', 'add at least one scan pattern for the Nexus IQ application']]
        problems { NexusIqSettings.NONE.validate(it, [NexusIqApplication.of('cert', ['**/*.jar'])]) } == []
        problems { NexusIqSettings.NONE.validate(it, []) } == []
    }

    def "SCM settings trim their values, sign in with a password by default and keep each reviewer once"() {
        when:
        def scm = new ScmSettings(' ', ' ', null, null, ' ', ' ', [' alice ', 'alice', '', 'bob'], ' ', ' ', ' ', ' ')

        then:
        scm == new ScmSettings(null, null, BASIC, null, null, null, ['alice', 'bob'], null, null, null, null)
        ScmSettings.NONE == new ScmSettings(null, null, null, null, null, null, null, null, null, null, null)
        ScmSettings.of(" $REPO ", ' bb-creds ') ==
                new ScmSettings(REPO, 'bb-creds', BASIC, null, null, null, [], null, null, null, null)
        new ScmSettings(REPO, 'bb', null, null, null, null, null, ' https://api.bitbucket.org/2.0 ', ' ta ', ' TA ',
                ' cert ') == new ScmSettings(REPO, 'bb', BASIC, null, null, null, [], 'https://api.bitbucket.org/2.0',
                'ta', 'TA', 'cert')
    }

    def "a repository writes every scm.bitbucket key that is set, and nothing without its URL"() {
        expect:
        written(ScmSettings.NONE) == [:]
        written(new ScmSettings(null, 'bb-creds', BEARER, CLOUD, 'main', 'ssh://git@x/r.git', ['alice'],
                'https://api.bitbucket.org/2.0', 'ta', 'TA', 'cert')) == [:]
        written(new ScmSettings(" $REPO ", 'bb-creds', BEARER, SERVER, ' develop ',
                ' ssh://git@bitbucket.bbh.com:7999/ta/cert.git ', ['alice', '{0b9e-uuid}'],
                ' https://bitbucket.bbh.com/rest/api/1.0 ', null, ' TA ', ' cert-scanner ')) ==
                [scm: [bitbucket: [url        : REPO, credentialsId: 'bb-creds', authType: 'bearer', type: 'server',
                                   targetBranch: 'develop', cloneUrl: 'ssh://git@bitbucket.bbh.com:7999/ta/cert.git',
                                   reviewers  : ['alice', '{0b9e-uuid}'],
                                   apiUrl     : 'https://bitbucket.bbh.com/rest/api/1.0', projectKey: 'TA',
                                   repoSlug   : 'cert-scanner']]]
        written(ScmSettings.of(REPO, 'bb-creds')) ==
                [scm: [bitbucket: [url: REPO, credentialsId: 'bb-creds', authType: 'basic']]]
        written(ScmSettings.builder().repositoryUrl(REPO).authType(BASIC).type(CLOUD).workspace('ta-workspace')
                .repoSlug('cert').build()).scm.bitbucket ==
                [url: REPO, authType: 'basic', type: 'cloud', workspace: 'ta-workspace', repoSlug: 'cert']
    }

    def "a repository needs the credentials GoldenFix pushes with"() {
        expect:
        reported { ScmSettings.of(REPO, ' ').validate(it) }.collect { [it.field, it.message] } ==
                [['credentialsId', 'is required to push GoldenFix branches and open pull requests']]
        problems(ScmSettings.of(REPO, 'bb-creds')) == []
        problems(ScmSettings.NONE) == []
    }

    def "the Bitbucket repository keys need the repository URL they refine"() {
        expect:
        problems(ScmSettings.builder().apiUrl(apiUrl).workspace(workspace).projectKey(projectKey).repoSlug(repoSlug)
                .build()) == ['repositoryUrl']
        messages(ScmSettings.builder().apiUrl(apiUrl).workspace(workspace).projectKey(projectKey).repoSlug(repoSlug)
                .build()) ==
                ['is required when the Bitbucket API URL, workspace, project key or repository slug is set']
        problems(ScmSettings.builder().repositoryUrl(REPO).credentialsId('bb').apiUrl(apiUrl).workspace(workspace)
                .projectKey(projectKey).repoSlug(repoSlug).build()) == []

        where:
        apiUrl                          | workspace | projectKey | repoSlug
        'https://api.bitbucket.org/2.0' | null      | null       | null
        null                            | 'ta'      | null       | null
        null                            | null      | 'TA'       | null
        null                            | null      | null       | 'cert'
    }

    def "metrics are on by default, use the test environment and default the project to the product code and service name"() {
        expect:
        new MetricsSettings(null, ' ', ' ', null, null) == DEFAULTS
        DEFAULTS == new MetricsSettings(true, null, 'test', null, null)
        new MetricsSettings(false, ' cert ', ' prod ', null, null) == new MetricsSettings(false, 'cert', 'prod', null, null)
        written(new MetricsSettings(null, 'CERT-gui', null, null, null)) == [influx: [enabled: true, project: 'CERT-gui', env: 'test']]
        written(new MetricsSettings(false, null, 'uat', null, null)) == [influx: [enabled: false, env: 'uat']]
        DEFAULTS.withDefaultProject('CERT', 'gui') == new MetricsSettings(true, 'CERT-gui', 'test', null, null)
        new MetricsSettings(false, null, 'uat', null, null).withDefaultProject('CERT', 'gui') == new MetricsSettings(false, 'CERT-gui', 'uat', null, null)
        new MetricsSettings(true, 'cert-scanner', 'uat', null, null).with { it.withDefaultProject('CERT', 'gui').is(it) }
    }

    def "the per-service InfluxDB, SonarQube and AppScan credentials are written at the library's paths"() {
        expect:
        written(new MetricsSettings(true, 'CERT-gui', null, ' https://influx.bbh.com/write ', ' cert-influx ')) ==
                [influx: [enabled: true, url: 'https://influx.bbh.com/write', credentialsId: 'cert-influx',
                          project: 'CERT-gui', env: 'test']]
        written { SonarSettings.builder().serverUrl(' https://sonar.bbh.com ').build().writeTo(it, GRADLE) } ==
                [tools: [sonar: [serverUrl: 'https://sonar.bbh.com']]]
        written {
            AppScanSettings.builder().applicationId(APP_ID).scanName(' cert-gui-sast ').build().writeTo(it, GRADLE)
        }.subMap(['asoc', 'sast']) == [sast: [scanName: 'cert-gui-sast']]
    }

    private static AppScanSettings dast(String targetUrl) {
        AppScanSettings.builder().applicationId(APP_ID).dastEnabled(true).dastTargetUrl(targetUrl)
                .build()
    }
}
