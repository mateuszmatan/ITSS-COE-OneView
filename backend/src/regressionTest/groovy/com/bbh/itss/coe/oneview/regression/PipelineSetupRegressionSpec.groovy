package com.bbh.itss.coe.oneview.regression

import com.bbh.itss.coe.oneview.application.jenkins.port.out.GenericUserSecretPort
import com.bbh.itss.coe.oneview.support.FakeHttpServer
import com.bbh.itss.coe.oneview.support.OneViewSpecification
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import spock.lang.AutoCleanup

import static com.bbh.itss.coe.oneview.support.ApiJson.product

/**
 * The guided set-up of a pipeline end to end, with stand-ins for Bitbucket and Jenkins. OIS is replaced by a port
 * that gives a fixed secret for the one API key it knows: the certificate OneView signs in to OIS with belongs to
 * its deployment.
 */
@Import(Ois)
class PipelineSetupRegressionSpec extends OneViewSpecification {

    static final String BASIC = 'Basic ' + 'svc-oneview:secret-from-ois'.bytes.encodeBase64()
    static final Map AUTOMATION = [genericUser: 'svc-oneview', oisHost: 'ois-qc.bbh.com', oisApiKey: 'a2a-key']

    @TestConfiguration
    static class Ois {
        @Bean
        @Primary
        GenericUserSecretPort fixedSecret() {
            { String host, String apiKey ->
                if (apiKey != 'a2a-key') {
                    throw new UncheckedIOException("OIS at $host did not give the secret of the generic user (HTTP 401).",
                            new IOException('HTTP 401'))
                }
                'secret-from-ois'
            } as GenericUserSecretPort
        }
    }

    @AutoCleanup
    FakeHttpServer bitbucket = FakeHttpServer.start()

    @AutoCleanup
    FakeHttpServer jenkins = FakeHttpServer.start()

    def cleanup() {
        def current = api.get('/api/jenkins-automation').json
        if (current.genericUser != null) {
            assert api.put('/api/jenkins-automation', [version: current.version]).status == 200
        }
    }

    Map saveAutomation(Map settings = AUTOMATION) {
        def current = api.get('/api/jenkins-automation').json
        def saved = api.put('/api/jenkins-automation', [version: current.version] + settings)
        assert saved.status == 200: saved
        saved.json as Map
    }

    Map jobRequest(Map overrides = [:]) {
        [jenkinsUrl : jenkins.url, folder: 'DevSecOps/PAY', name: 'payments-api', kind: 'PIPELINE',
         repositoryUrl: 'https://bitbucket.bbh.com/projects/PAY/repos/payments-api/browse', branch: 'develop',
         credentialsId: 'bb-reader', parameters: [[type: 'STRING', name: 'TARGET', defaultValue: 'rd']],
         daysToKeep : 30, concurrentBuildsDisabled: true] + overrides
    }

    Map newPipeline() {
        def code = uniqueCode('WIZ')
        def created = createProduct(product(code: code, name: "Wizard $code"))
        pipelineFor(created.services[0].id as long)
    }

    def "the automation starts without settings and names what it needs"() {
        expect:
        with(api.get('/api/jenkins-automation').json) {
            genericUser == null
            ready == false
            oisApiKeySet == false
            missing == ['the generic user', 'the OIS host', 'the OIS API key']
            pollSchedule == 'H/5 * * * *'
            libraryVersion == 'main'
        }
    }

    def "the settings are saved, and the OIS API key is kept but never sent back"() {
        when:
        def saved = saveAutomation(AUTOMATION + [pollSchedule: 'H/10 * * * *'])

        then:
        saved.ready == true
        saved.oisApiKeySet == true
        saved.missing == []
        !saved.containsKey('oisApiKey')
        !api.get('/api/jenkins-automation').body.contains('a2a-key')

        when: 'the settings are saved again without the key'
        def again = saveAutomation(genericUser: 'svc-oneview', oisHost: 'ois-qc.bbh.com:8443')

        then:
        again.ready == true
        again.oisHost == 'ois-qc.bbh.com:8443'
        again.version == saved.version + 1
        jdbc.queryForObject('SELECT OIS_API_KEY FROM DSO_JENKINS_AUTOMATION', String) == 'a2a-key'

        and: 'settings read at an older version are refused'
        api.put('/api/jenkins-automation', AUTOMATION + [version: saved.version]).status == 409
    }

    def "settings that cannot work are refused on their fields"() {
        when:
        def refused = api.put('/api/jenkins-automation', [version: 0] + AUTOMATION + change)

        then:
        refused.status == 400
        refused.body.contains(field)

        where:
        change                                    | field
        [oisHost: 'https://ois-qc.bbh.com/']      | 'oisHost'
        [pollSchedule: 'now <script>']            | 'pollSchedule'
        [libraryRepositoryUrl: 'dsoenhanced']     | 'libraryRepositoryUrl'
    }

    def "the connection check signs in to Jenkins with the secret from OIS, and says why when it cannot"() {
        given:
        jenkins.on('GET', '/whoAmI/api/json', { request ->
            request.headers.authorization == BASIC ? [body: '{"name":"OneView service","authenticated":true,"anonymous":false}']
                    : [status: 401]
        })

        expect: 'without settings'
        with(api.post('/api/jenkins-automation/check', [jenkinsUrl: jenkins.url]).json) {
            ok == false
            message.contains('an administrator has to set the generic user, the OIS host, the OIS API key')
        }

        when:
        saveAutomation()
        def check = api.post('/api/jenkins-automation/check', [jenkinsUrl: jenkins.url])

        then:
        check.json == [ok: true, message: "OIS gave the secret and $jenkins.url knows the generic user as OneView service." as String]
        !check.body.contains('secret-from-ois')

        when: 'OIS does not know the API key'
        saveAutomation(AUTOMATION + [oisApiKey: 'wrong-key'])

        then:
        with(api.post('/api/jenkins-automation/check', [jenkinsUrl: jenkins.url]).json) {
            ok == false
            message.startsWith('OIS at ois-qc.bbh.com did not give the secret')
        }
    }

    def "the sources of a repository are read and what they show is detected, #who"() {
        given:
        if (automation) {
            saveAutomation()
        }
        def files = '/rest/api/1.0/projects/PAY/repos/payments-api'
        bitbucket.on('GET', "$files/files.*", [body: '{"values":["build.gradle","Jenkinsfile","openshift/deployment.yaml",' +
                '"gradle/wrapper/gradle-wrapper.properties","src/main/java/App.java"],"isLastPage":true}'])
        bitbucket.on('GET', "$files/raw/build.gradle.*", [body: "java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }"])
        bitbucket.on('GET', "$files/raw/gradle/wrapper/gradle-wrapper.properties.*",
                [body: 'distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14-bin.zip'])
        bitbucket.on('GET', "$files/raw/Jenkinsfile.*", [body: "@Library('DevSecOpsJenkinsLibrary') _\n" +
                "devSecOpsPipeline(agentNames: ['linux-agent'], projectNames: 'payments-api')\n"])

        when:
        def read = api.post('/api/pipeline-setup/sources',
                [repositoryUrl: "$bitbucket.url/projects/PAY/repos/payments-api/browse" as String, branch: 'develop'])

        then:
        read.status == 200
        with(read.json) {
            branch == 'develop'
            readAsGenericUser == automation
            stack.buildTool == 'GRADLE'
            stack.javaVersion == '21'
            jenkinsfilePath == 'Jenkinsfile'
            jenkinsfile.libraries == ['DevSecOpsJenkinsLibrary']
            jenkinsfile.entryPoint == 'devSecOpsPipeline'
            jenkinsfile.pipelineType == 'FULL'
            jenkinsfile.hasPipelineKey == false
            jenkinsfile.agentLabels == ['linux-agent']
            jenkinsfile.serviceNames == ['payments-api']
        }
        bitbucket.requests*.method.unique() == ['GET']
        bitbucket.requests*.headers*.authorization.unique() == [automation ? BASIC : null]
        bitbucket.requests.every { it.target.contains('at=develop') }

        where:
        who                   | automation
        'anonymously'         | false
        'as the generic user' | true
    }

    def "sources that cannot be read are told as they are"() {
        expect:
        with(api.post('/api/pipeline-setup/sources', [repositoryUrl: 'https://github.com/bbh/payments'])) {
            status == 400
            body.contains('must be the address of the repository in Bitbucket')
        }
        with(api.post('/api/pipeline-setup/sources', [repositoryUrl: "$bitbucket.url/projects/PAY/repos/missing" as String])) {
            status == 503
            body.contains('Bitbucket has no such repository, or no branch main in it, for an anonymous reader')
        }
        api.post('/api/pipeline-setup/sources', [repositoryUrl: ' ']).status == 400
    }

    def "an existing Jenkins job is read with its build, and nothing in Jenkins is changed"() {
        given:
        jenkins.on('GET', '/job/legacy/job/payments/config.xml', [contentType: 'application/xml', body: '''\
            <flow-definition>
              <description>Payments build</description>
              <properties>
                <hudson.model.ParametersDefinitionProperty><parameterDefinitions>
                  <hudson.model.StringParameterDefinition><name>TARGET</name><defaultValue>rd</defaultValue></hudson.model.StringParameterDefinition>
                  <hudson.model.PasswordParameterDefinition><name>TOKEN</name><defaultValue>{AQAAABAAAAAQ}</defaultValue></hudson.model.PasswordParameterDefinition>
                </parameterDefinitions></hudson.model.ParametersDefinitionProperty>
                <org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty><triggers>
                  <hudson.triggers.TimerTrigger><spec>H 2 * * *</spec></hudson.triggers.TimerTrigger>
                </triggers></org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty>
              </properties>
              <definition class="org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition">
                <scm class="hudson.plugins.git.GitSCM"><userRemoteConfigs><hudson.plugins.git.UserRemoteConfig>
                  <url>https://bitbucket.bbh.com/scm/pay/payments-api.git</url><credentialsId>bb-reader</credentialsId>
                </hudson.plugins.git.UserRemoteConfig></userRemoteConfigs>
                <branches><hudson.plugins.git.BranchSpec><name>*/develop</name></hudson.plugins.git.BranchSpec></branches></scm>
                <scriptPath>Jenkinsfile</scriptPath>
              </definition>
            </flow-definition>'''.stripIndent()])
        jenkins.on('GET', '/job/legacy/job/payments/42/api/json.*',
                [body: '{"number":42,"result":"SUCCESS","actions":[{"parameters":[{"name":"TARGET","value":"qc"}]}]}'])

        when:
        def read = api.post('/api/pipeline-setup/jenkins-job', [jobUrl: "$jenkins.url/job/legacy/job/payments/42/" as String])

        then:
        read.status == 200
        with(read.json) {
            kind == 'PIPELINE'
            jobPath == 'legacy/payments'
            description == 'Payments build'
            repositoryUrl == 'https://bitbucket.bbh.com/scm/pay/payments-api.git'
            branch == 'develop'
            credentialsId == 'bb-reader'
            scriptPath == 'Jenkinsfile'
            parameters*.name == ['TARGET', 'TOKEN']
            parameters*.defaultValue == ['rd', null]
            triggers == [[kind: 'TIMER', name: 'Build periodically', schedule: 'H 2 * * *']]
            lastBuild.number == 42
            lastBuild.parameters == [TARGET: 'qc']
        }
        !read.body.contains('AQAAABAAAAAQ')
        jenkins.requests*.method.unique() == ['GET']

        and: 'an address that is not one of a job, and a job Jenkins does not have'
        api.post('/api/pipeline-setup/jenkins-job', [jobUrl: jenkins.url]).status == 400
        with(api.post('/api/pipeline-setup/jenkins-job', [jobUrl: "$jenkins.url/job/missing/" as String])) {
            status == 503
            body.contains('Jenkins has nothing at')
        }
    }

    def "OneView creates the Jenkins job of a pipeline as the generic user and notes it on the pipeline"() {
        given:
        saveAutomation(AUTOMATION + [pollSchedule: 'H/10 * * * *'])
        def pipeline = newPipeline()
        jenkins.on('GET', '/crumbIssuer/api/json', [body: '{"crumbRequestField":"Jenkins-Crumb","crumb":"c-123"}'])
        jenkins.on('POST', '.*/createItem\\?name=.*', [status: 200])

        when:
        def created = api.post("/api/pipelines/$pipeline.id/jenkins-job", jobRequest())

        then:
        created.status == 201
        def jobUrl = "$jenkins.url/job/DevSecOps/job/PAY/job/payments-api/" as String
        with(created.json) {
            pipelineId == pipeline.id
            it.jobUrl == jobUrl
            jobPath == 'DevSecOps/PAY/payments-api'
            kind == 'PIPELINE'
            trigger == 'Jenkins polls branch develop of the repository on the schedule H/10 * * * * and builds when it changed.'
            jenkinsfilePath == 'Jenkinsfile'
            jenkinsfile ==~ /@Library\('DevSecOpsJenkinsLibrary'\) _\n\ndevSecOpsPipeline\(pipelineKey: '[0-9a-f-]{36}'\)\n/
            libraryAttached == false
        }

        and: 'Jenkins got two folders and the job, each with the crumb and the login'
        def posts = jenkins.requests.findAll { it.method == 'POST' }
        posts*.target == ['/createItem?name=DevSecOps', '/job/DevSecOps/createItem?name=PAY',
                          '/job/DevSecOps/job/PAY/createItem?name=payments-api']
        posts.every { it.headers.authorization == BASIC && it.headers.'jenkins-crumb' == 'c-123' }
        def config = posts.last().body
        config.startsWith("<?xml version='1.1' encoding='UTF-8'?>\n<flow-definition>")
        config.contains('<url>https://bitbucket.bbh.com/scm/pay/payments-api.git</url>\n<credentialsId>bb-reader</credentialsId>')
        config.contains('<name>*/develop</name>')
        config.contains('<scriptPath>Jenkinsfile</scriptPath>')
        config.contains('<hudson.triggers.SCMTrigger>\n<spec>H/10 * * * *</spec>')
        config.contains('<hudson.model.StringParameterDefinition>\n<name>TARGET</name>\n<defaultValue>rd</defaultValue>')
        config.contains('<daysToKeep>30</daysToKeep>')
        config.contains('DisableConcurrentBuildsJobProperty')

        and: 'the pipeline knows its job'
        api.get("/api/pipelines/$pipeline.id").json.jenkinsJob == jobUrl
    }

    def "a Multibranch job carries the DevSecOps library when its repository is set"() {
        given:
        saveAutomation(AUTOMATION + [libraryRepositoryUrl: 'https://bitbucket.bbh.com/scm/dso/dsoenhanced.git',
                                     libraryCredentialsId: 'bb-reader'])
        def pipeline = newPipeline()
        jenkins.on('POST', '/createItem\\?name=.*', [status: 200])

        when:
        def created = api.post("/api/pipelines/$pipeline.id/jenkins-job", jobRequest(kind: 'MULTIBRANCH', folder: null))

        then:
        created.status == 201
        created.json.libraryAttached == true
        created.json.trigger.startsWith('Jenkins scans the branches of the repository every 5 minutes')
        def config = jenkins.requests.find { it.method == 'POST' }.body
        config.contains('<org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject>')
        config.contains('<remote>https://bitbucket.bbh.com/scm/pay/payments-api.git</remote>')
        config.contains('<interval>300000</interval>')
        config.contains('<name>DevSecOpsJenkinsLibrary</name>')
        config.contains('<remote>https://bitbucket.bbh.com/scm/dso/dsoenhanced.git</remote>\n<credentialsId>bb-reader</credentialsId>')
    }

    def "OneView creates nothing where a job exists, where the automation is not set up, or where the job cannot work"() {
        given:
        def pipeline = newPipeline()
        jenkins.on('GET', '/job/DevSecOps/job/PAY/job/payments-api/api/json.*', [body: '{"name":"payments-api"}'])

        expect: 'without the automation'
        with(api.post("/api/pipelines/$pipeline.id/jenkins-job", jobRequest())) {
            status == 409
            body.contains('skip this step and do it yourself')
        }

        when:
        saveAutomation()

        then: 'the job exists'
        with(api.post("/api/pipelines/$pipeline.id/jenkins-job", jobRequest())) {
            status == 409
            body.contains('OneView never changes an existing job')
        }

        and: 'the request is not one for a job'
        api.post("/api/pipelines/$pipeline.id/jenkins-job", jobRequest(name: 'a/b')).status == 400
        api.post("/api/pipelines/$pipeline.id/jenkins-job", jobRequest(kind: 'OTHER')).status == 400
        api.post("/api/pipelines/$pipeline.id/jenkins-job", jobRequest(jenkinsUrl: 'jenkins')).status == 400
        api.post("/api/pipelines/$pipeline.id/jenkins-job",
                jobRequest(parameters: [[type: 'STRING', name: 'bad name']])).status == 400
        api.post('/api/pipelines/987654321/jenkins-job', jobRequest()).status == 404

        and: 'Jenkins was only read, and the pipeline has no job'
        jenkins.requests*.method.unique() == ['GET']
        api.get("/api/pipelines/$pipeline.id").json.jenkinsJob == null
    }
}
