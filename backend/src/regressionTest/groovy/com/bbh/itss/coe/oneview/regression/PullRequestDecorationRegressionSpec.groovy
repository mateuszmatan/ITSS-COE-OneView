package com.bbh.itss.coe.oneview.regression

import com.bbh.itss.coe.oneview.support.OneViewSpecification

import static com.bbh.itss.coe.oneview.support.ApiJson.pipeline
import static com.bbh.itss.coe.oneview.support.ApiJson.product
import static com.bbh.itss.coe.oneview.support.ApiJson.service

/**
 * The report of a pull request decoration of the DSOEnhanced library: after it has commented on a pull request or
 * opened one, the library sends {@code POST /api/dso/bitbucket/pull-requests/decorate} with what it posted, and the
 * pipeline page reads it with {@code GET /api/pipelines/{id}/pull-request-decorations}.
 */
class PullRequestDecorationRegressionSpec extends OneViewSpecification {

    static final String DECORATE = '/api/dso/bitbucket/pull-requests/decorate'
    static final String REPOSITORY = 'https://bitbucket.bbh.com/projects/CERT/repos/gui'
    static final List<String> READ_AS_SENT = ['job', 'build', 'branch', 'commit', 'service', 'kind', 'repositoryUrl',
                                              'pullRequestId', 'pullRequestUrl', 'title', 'sourceBranch', 'targetBranch',
                                              'created', 'lineComments', 'generalComments', 'limited', 'maxComments',
                                              'findingsTotal', 'summary']

    String code
    String job
    Map gui

    def setup() {
        code = uniqueCode()
        job = "DevSecOps/$code/gui-full".toString()
        Map created = createProduct(product(code: code, name: "Product $code", services: [service(name: 'gui')]))
        gui = pipelineFor(created.services[0].id as long, pipeline(jenkinsJob: job))
    }

    /** A decoration as the library sends it: what it posted on the pull request, and the run it posted it from. */
    private Map decoration(Map overrides = [:]) {
        [pipelineKey    : gui.activeKey.value, job: job, build: 42, branch: 'feature/login', commit: 'b815d55',
         service        : 'gui', kind: 'BRANCH', repositoryUrl: REPOSITORY, pullRequestId: '12',
         pullRequestUrl : "$REPOSITORY/pull-requests/12".toString(), title: 'Add the login page',
         sourceBranch   : 'feature/login', targetBranch: 'main', created: false, lineComments: 3,
         generalComments: 2, limited: true, maxComments: 25,
         findings       : [[tool       : 'AppScan SAST', severity: 'HIGH', file: 'src/main/java/Login.java', line: 42,
                            title      : 'SQL Injection', description: 'User input reaches a database query.',
                            remediation: 'Use a prepared statement.'],
                           [tool       : 'Nexus IQ', severity: '', file: 'build.gradle', line: null,
                            title      : 'org.apache.logging.log4j:log4j-core 2.14.1',
                            description: 'Not changed automatically: left for a developer.',
                            remediation: 'Upgrade to 2.17.1']],
         findingsTotal  : 2, summary: '## Security findings of gui'] + overrides
    }

    def "a decoration the library reports is recorded for the pipeline of its key and read back without the key"() {
        given:
        Map sent = decoration()

        when:
        def answer = api.post(DECORATE, sent)

        then:
        answer.status == 200
        answer.json == [pullRequestId: '12', commentsAdded: 2, lineCommentsAdded: 3, status: 'RECORDED',
                        message: "Recorded the decoration of pull request 12 for pipeline $gui.id".toString()]

        when:
        def read = api.get("/api/pipelines/$gui.id/pull-request-decorations")
        def stored = read.json[0] as Map

        then:
        read.status == 200
        read.json.size() == 1
        stored.pipelineId == gui.id
        stored.recordedAt != null
        stored.subMap(READ_AS_SENT) == sent.subMap(READ_AS_SENT)
        stored.findings == [sent.findings[0], sent.findings[1] + [severity: null]]
        !stored.containsKey('pipelineKey')
        !read.body.contains(gui.activeKey.value as String)
    }

    def "a request of an older client, without the fields the library adds now, stays valid and is recorded without a pipeline"() {
        when:
        def answer = api.post(DECORATE, [repositoryUrl: REPOSITORY, pullRequestId: '7', credentialsId: 'bitbucket-token',
                                         sourceBranch : 'feature/login', targetBranch: 'main',
                                         findings     : [[tool: 'SonarQube', severity: 'MAJOR', file: 'src/app.ts', line: 3,
                                                          title: 'Unused import', description: null, remediation: null]],
                                         summary      : 'Findings of the last build'])

        then:
        answer.status == 200
        answer.json == [pullRequestId: '7', commentsAdded: 0, lineCommentsAdded: 0, status: 'RECORDED',
                        message: 'Recorded the decoration of pull request 7 without a pipeline: ' +
                                'OneView finds no pipeline by the pipeline key or the job']
        api.get("/api/pipelines/$gui.id/pull-request-decorations").json == []
    }

    def "a request without the repository or the pull request records nothing and is skipped, as before"() {
        when:
        def answer = api.post(DECORATE, decoration(overrides))

        then:
        answer.status == 200
        answer.json == [pullRequestId: null, commentsAdded: 0, lineCommentsAdded: 0, status: 'SKIPPED',
                        message: 'Missing repositoryUrl or pullRequestId']
        api.get("/api/pipelines/$gui.id/pull-request-decorations").json == []

        where:
        overrides << [[repositoryUrl: null], [pullRequestId: null], [pullRequestId: ''], [pullRequestId: '\u0001']]
    }

    def "a decoration with a key that is not active is recorded for the one pipeline of its job"() {
        given:
        String stale = gui.activeKey.value
        api.post("/api/pipelines/$gui.id/keys")

        when:
        def answer = api.post(DECORATE, decoration(pipelineKey: stale, kind: 'STATIC', pullRequestId: '13'))

        then:
        answer.json.message == "Recorded the decoration of pull request 13 for pipeline $gui.id".toString()
        api.get("/api/pipelines/$gui.id/pull-request-decorations").json*.kind == ['STATIC']
    }

    def "a kind OneView does not know and an address that is not http or https are recorded as none, never refused"() {
        when:
        def answer = api.post(DECORATE, decoration(kind: 'GOLDEN_FIX', pullRequestUrl: 'javascript:alert(1)'))

        then:
        answer.status == 200
        answer.json.status == 'RECORDED'

        when:
        def stored = api.get("/api/pipelines/$gui.id/pull-request-decorations").json[0] as Map

        then:
        stored.containsKey('kind')
        stored.kind == null
        stored.containsKey('pullRequestUrl')
        stored.pullRequestUrl == null
        stored.title == 'Add the login page'
    }

    def "the newest decorations of a pipeline come first, twenty unless the page asks for another number"() {
        given:
        (1..21).each { api.post(DECORATE, decoration(build: it)) }

        expect:
        api.get("/api/pipelines/$gui.id/pull-request-decorations").json*.build == (21..2).toList()
        api.get("/api/pipelines/$gui.id/pull-request-decorations?limit=2").json*.build == [21, 20]
        api.get("/api/pipelines/$gui.id/pull-request-decorations?limit=500").json*.build == (21..1).toList()
    }

    def "the decorations of a pipeline that does not exist are answered as the pipeline itself"() {
        when:
        def decorations = api.get('/api/pipelines/999999999/pull-request-decorations')
        def pipeline = api.get('/api/pipelines/999999999')

        then:
        decorations.status == 404
        decorations.json.detail == 'Pipeline 999999999 does not exist'
        decorations.json.subMap(['status', 'title', 'detail']) == pipeline.json.subMap(['status', 'title', 'detail'])
    }
}
