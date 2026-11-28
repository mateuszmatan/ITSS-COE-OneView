package com.bbh.itss.coe.oneview.application.jenkins

import com.bbh.itss.coe.oneview.adapter.out.bitbucket.BitbucketHttpRepositoryAdapter.MapRepoFileTree
import com.bbh.itss.coe.oneview.application.discovery.port.out.BitbucketRepositoryPort
import com.bbh.itss.coe.oneview.application.jenkins.port.in.GuidedPipelineSetupUseCase.CreateJenkinsJobCommand
import com.bbh.itss.coe.oneview.application.jenkins.port.out.GenericUserSecretPort
import com.bbh.itss.coe.oneview.application.jenkins.port.out.JenkinsAutomationRepositoryPort
import com.bbh.itss.coe.oneview.application.jenkins.port.out.JenkinsPort
import com.bbh.itss.coe.oneview.application.pipeline.port.out.PipelineRepositoryPort
import com.bbh.itss.coe.oneview.application.settings.port.out.GlobalSettingsRepositoryPort
import com.bbh.itss.coe.oneview.domain.jenkins.BuildRetention
import com.bbh.itss.coe.oneview.domain.jenkins.FolderLibrary
import com.bbh.itss.coe.oneview.domain.jenkins.GenericLogin
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomation
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomationSettings
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobAddress
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSnapshot
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSpec
import com.bbh.itss.coe.oneview.domain.jenkins.JobParameter
import com.bbh.itss.coe.oneview.domain.pipeline.Pipeline
import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.coe.oneview.domain.jenkins.JobKind.MULTIBRANCH
import static com.bbh.itss.coe.oneview.domain.jenkins.JobKind.PIPELINE
import static com.bbh.itss.coe.oneview.domain.jenkins.JobParameter.Type.STRING
import static com.bbh.itss.coe.oneview.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.coe.oneview.support.Fixtures.KEY
import static com.bbh.itss.coe.oneview.support.Fixtures.pipeline
import static com.bbh.itss.coe.oneview.support.Fixtures.revokedKey
import static com.bbh.itss.coe.oneview.support.Fixtures.storedSettings

class GuidedPipelineSetupServiceSpec extends Specification {

    static final GenericLogin LOGIN = new GenericLogin('svc-oneview', 'secret-from-ois')
    static final String REPOSITORY = 'https://bitbucket.bbh.com/projects/PAY/repos/payments-api/browse'
    static final String CLONE = 'https://bitbucket.bbh.com/scm/pay/payments-api.git'

    def ready = JenkinsAutomationSettings.builder().genericUser('svc-oneview').oisHost('ois-qc.bbh.com')
            .oisApiKey('a2a-key').pollSchedule('H/10 * * * *').build()
    Optional<JenkinsAutomation> stored = Optional.empty()

    JenkinsAutomationRepositoryPort automation = Stub() {
        load() >> { stored }
    }
    GenericUserSecretPort secrets = Mock()
    BitbucketRepositoryPort bitbucket = Mock()
    JenkinsPort jenkins = Mock()
    PipelineRepositoryPort pipelines = Mock()
    GlobalSettingsRepositoryPort globalSettings = Stub() {
        load() >> Optional.of(storedSettings('https://jenkins.bbh.com'))
    }
    def service = new GuidedPipelineSetupService(automation, secrets, bitbucket, jenkins, pipelines, globalSettings)

    void automationIs(JenkinsAutomationSettings settings) {
        stored = Optional.of(new JenkinsAutomation(settings, 1, Instant.EPOCH))
    }

    static CreateJenkinsJobCommand command(Map args = [:]) {
        new CreateJenkinsJobCommand('https://jenkins.bbh.com', args.get('folder', 'DevSecOps/PAY') as String,
                args.get('name', 'payments-api') as String, args.get('kind', PIPELINE), args.get('repositoryUrl', REPOSITORY) as String,
                'develop', null, 'bb-reader', 'Payments', [new JobParameter(STRING, 'TARGET', 'rd', null, null)],
                new BuildRetention(30, 20), true, 'H 2 * * *')
    }

    def "the sources are read as the generic user and tell the stack and the Jenkinsfile"() {
        given:
        automationIs(ready)

        when:
        def inspection = service.inspectSources(" $REPOSITORY ", ' ')

        then:
        1 * secrets.fetch('ois-qc.bbh.com', 'a2a-key') >> 'secret-from-ois'
        1 * bitbucket.readSources(REPOSITORY, 'main', LOGIN) >> new MapRepoFileTree(
                ['pom.xml', 'Jenkinsfile'] as Set, [] as Set,
                ['pom.xml'    : '<project><properties><java.version>21</java.version></properties></project>',
                 'Jenkinsfile': "@Library('DevSecOpsJenkinsLibrary') _\ndevSecOpsPipeline(agentNames: ['linux'])\n"])
        inspection.branch() == 'main'
        inspection.readAsGenericUser()
        inspection.jenkinsfilePath() == 'Jenkinsfile'
        inspection.jenkinsfile().pipelineType() == FULL
        inspection.jenkinsfile().agentLabels() == ['linux']
        inspection.stack().buildTool() == 'MAVEN'
    }

    def "without the automation the sources are read anonymously, and a repository without a Jenkinsfile says so"() {
        when:
        def inspection = service.inspectSources(REPOSITORY, 'develop')

        then:
        1 * bitbucket.readSources(REPOSITORY, 'develop', null) >> new MapRepoFileTree(['build.gradle'] as Set, [] as Set, [:])
        0 * secrets._
        !inspection.readAsGenericUser()
        inspection.jenkinsfilePath() == null
        inspection.jenkinsfile() == null
    }

    def "sources without an address are refused before anything is read"() {
        when:
        service.inspectSources(' ', null)

        then:
        thrown(InvalidRequestException)
        0 * bitbucket._
    }

    def "an existing job is only read"() {
        given:
        automationIs(ready)
        def snapshot = JenkinsJobSnapshot.builder().kind(PIPELINE).jobPath('legacy/payments').build()

        when:
        def read = service.readJenkinsJob('https://jenkins.bbh.com/job/legacy/job/payments/42/')

        then:
        1 * secrets.fetch(_, _) >> 'secret-from-ois'
        1 * jenkins.readJob(new JenkinsJobAddress('https://jenkins.bbh.com', ['legacy', 'payments'], 42), LOGIN) >> snapshot
        0 * jenkins._
        read.is(snapshot)
    }

    def "the Jenkins job is created as the generic user and noted on the pipeline"() {
        given:
        automationIs(ready)
        Pipeline pipeline = pipeline(version: 4)
        def folder = new JenkinsJobAddress('https://jenkins.bbh.com', ['DevSecOps', 'PAY'], null)
        JenkinsJobSpec created = null

        when:
        def result = service.createJenkinsJob(20, command())

        then:
        1 * pipelines.loadForUpdate(20) >> Optional.of(pipeline)
        1 * secrets.fetch('ois-qc.bbh.com', 'a2a-key') >> 'secret-from-ois'
        1 * jenkins.exists(folder.child('payments-api'), LOGIN) >> false

        then:
        1 * jenkins.createJob(folder, _, LOGIN) >> { JenkinsJobAddress where, JenkinsJobSpec job, GenericLogin login -> created = job }

        then:
        1 * pipelines.save({ Pipeline saved ->
            saved.settings().jenkinsJob() == 'https://jenkins.bbh.com/job/DevSecOps/job/PAY/job/payments-api/'
        })
        with(created) {
            kind() == PIPELINE
            name() == 'payments-api'
            repositoryUrl() == CLONE
            credentialsId() == 'bb-reader'
            branch() == 'develop'
            scriptPath() == 'Jenkinsfile'
            parameters()*.name() == ['TARGET']
            retention() == new BuildRetention(30, 20)
            concurrentBuildsDisabled()
            pollSchedule() == 'H/10 * * * *'
            timerSchedule() == 'H 2 * * *'
            library() == null
        }
        with(result) {
            pipelineId() == 20
            jobUrl() == 'https://jenkins.bbh.com/job/DevSecOps/job/PAY/job/payments-api/'
            jobPath() == 'DevSecOps/PAY/payments-api'
            kind() == PIPELINE
            trigger() == 'Jenkins polls branch develop of the repository on the schedule H/10 * * * * and builds when it changed.'
            jenkinsfilePath() == 'Jenkinsfile'
            jenkinsfile() == "@Library('DevSecOpsJenkinsLibrary') _\n\ndevSecOpsPipeline(pipelineKey: '$KEY')\n"
            !libraryAttached()
        }
    }

    def "a Multibranch job carries the library when its repository is set, and scans the branches"() {
        given:
        automationIs(ready.toBuilder().libraryRepositoryUrl('https://bitbucket.bbh.com/scm/dso/dsoenhanced.git').build())
        pipelines.loadForUpdate(20) >> Optional.of(pipeline())
        secrets.fetch(_, _) >> 'secret-from-ois'
        JenkinsJobSpec created = null

        when:
        def result = service.createJenkinsJob(20, command(kind: MULTIBRANCH, folder: null))

        then:
        1 * jenkins.createJob(new JenkinsJobAddress('https://jenkins.bbh.com', [], null), _, LOGIN) >> { where, JenkinsJobSpec job, login -> created = job }
        created.library() == new FolderLibrary('DevSecOpsJenkinsLibrary', 'https://bitbucket.bbh.com/scm/dso/dsoenhanced.git', 'main', null)
        result.libraryAttached()
        result.jobUrl() == 'https://jenkins.bbh.com/job/payments-api/'
        result.trigger() == 'Jenkins scans the branches of the repository every 10 minutes and builds every branch' +
                ' that changed and has a Jenkinsfile.'
    }

    def "OneView never changes a job that exists"() {
        given:
        automationIs(ready)
        pipelines.loadForUpdate(20) >> Optional.of(pipeline())
        secrets.fetch(_, _) >> 'secret-from-ois'
        jenkins.exists(_, _) >> true

        when:
        service.createJenkinsJob(20, command())

        then:
        def refused = thrown(IllegalStateException)
        refused.message.startsWith('Jenkins already has https://jenkins.bbh.com/job/DevSecOps/job/PAY/job/payments-api/.')
        0 * jenkins.createJob(*_)
        0 * pipelines.save(_)
    }

    def "without the automation nothing is created and the user is told to do it by hand"() {
        given:
        pipelines.loadForUpdate(20) >> Optional.of(pipeline())

        when:
        service.createJenkinsJob(20, command())

        then:
        def refused = thrown(IllegalStateException)
        refused.message.contains('an administrator has to set the generic user, the OIS host, the OIS API key')
        0 * secrets._
        0 * jenkins._
        0 * pipelines.save(_)
    }

    def "a job that cannot work is refused before OIS or Jenkins is asked"() {
        given:
        automationIs(ready)
        pipelines.loadForUpdate(20) >> Optional.of(pipeline())

        when:
        service.createJenkinsJob(20, command(name: 'a/b', repositoryUrl: ' '))

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems*.field() == ['name', 'repositoryUrl']
        0 * secrets._
        0 * jenkins._
    }

    def "a pipeline without an active key gets no job, and an unknown pipeline is not found"() {
        given:
        automationIs(ready)
        pipelines.loadForUpdate(20) >> Optional.of(pipeline(keys: [revokedKey()]))
        pipelines.loadForUpdate(21) >> Optional.empty()

        when:
        service.createJenkinsJob(20, command())

        then:
        def noKey = thrown(IllegalStateException)
        noKey.message == GuidedPipelineSetupService.NO_KEY

        when:
        service.createJenkinsJob(21, command())

        then:
        thrown(NoSuchElementException)
        0 * jenkins._
    }
}
