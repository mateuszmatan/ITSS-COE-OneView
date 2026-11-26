package com.bbh.itss.coe.oneview.application.bitbucket

import com.bbh.itss.coe.oneview.application.bitbucket.port.out.PullRequestDecorationPort
import com.bbh.itss.coe.oneview.application.pipeline.port.out.PipelineRepositoryPort
import com.bbh.itss.coe.oneview.application.report.port.out.PipelineLookupPort
import com.bbh.itss.coe.oneview.domain.bitbucket.PrDecorationRequest
import com.bbh.itss.coe.oneview.domain.bitbucket.PrDecorationResult
import com.bbh.itss.coe.oneview.domain.bitbucket.PrFinding
import com.bbh.itss.coe.oneview.domain.bitbucket.PullRequestDecoration
import com.bbh.itss.coe.oneview.support.Fixtures
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static com.bbh.itss.coe.oneview.domain.bitbucket.DecorationKind.BRANCH
import static com.bbh.itss.coe.oneview.domain.bitbucket.DecorationKind.DAST
import static com.bbh.itss.coe.oneview.domain.bitbucket.DecorationKind.STATIC
import static com.bbh.itss.coe.oneview.domain.shared.Text.bytes
import static java.time.ZoneOffset.UTC

class PullRequestDecorationServiceSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-11-26T09:30:00.123456789Z')
    static final String REPOSITORY = 'https://bitbucket.bbh.com/projects/CERT/repos/gui'
    static final String PULL_REQUEST = REPOSITORY + '/pull-requests/12'
    static final String NOT_FOUND = 'Recorded the decoration of pull request 12 without a pipeline: ' +
            'OneView finds no pipeline by the pipeline key or the job'

    def lookup = new KnownPipelines(keys: ['key-7': 7L], jobs: ['CERT/gui': 9L])
    PipelineRepositoryPort pipelines = Mock()
    PullRequestDecorationPort decorations = Mock()
    def service = new PullRequestDecorationService(lookup, pipelines, decorations, Clock.fixed(NOW, UTC))
    PullRequestDecoration saved

    def setup() {
        decorations.save(_) >> { PullRequestDecoration decoration -> saved = decoration }
    }

    def "a request without the repository or the pull request records nothing and is skipped, as before"() {
        when:
        def result = service.record(request)

        then:
        result == new PrDecorationResult(null, 0, 0, 'SKIPPED', 'Missing repositoryUrl or pullRequestId')
        0 * decorations._
        lookup.lookups == []

        where:
        request << [null, request(repositoryUrl: null), request(pullRequestId: null), request(repositoryUrl: '  '),
                    request(pullRequestId: ''), request(pullRequestId: '\u0001'), request(repositoryUrl: '\u0001')]
    }

    def "the decoration is recorded for the pipeline whose active key the run sent, before the pipeline of its job"() {
        when:
        def result = service.record(request(pipelineKey: 'key-7', job: 'CERT/gui', lineComments: 3, generalComments: 2))

        then:
        result == new PrDecorationResult('12', 2, 3, 'RECORDED', 'Recorded the decoration of pull request 12 for pipeline 7')
        saved.pipelineId() == 7L
        lookup.lookups == ['key key-7']
    }

    def "without an active key the decoration is recorded for the one pipeline of its job: #reason"() {
        when:
        def result = service.record(request(pipelineKey: key, job: ' CERT/gui '))

        then:
        result.message() == 'Recorded the decoration of pull request 12 for pipeline 9'
        saved.pipelineId() == 9L
        lookup.lookups == lookups

        where:
        reason                       | key         || lookups
        'the key is not active'      | 'revoked'   || ['key revoked', 'job CERT/gui']
        'the run sent no key'        | null        || ['job CERT/gui']
        'the run sent an empty key'  | ''          || ['job CERT/gui']
    }

    def "a decoration whose pipeline OneView does not find is recorded without a pipeline: #reason"() {
        when:
        def result = service.record(request(pipelineKey: key, job: job))

        then:
        result == new PrDecorationResult('12', 1, 3, 'RECORDED', NOT_FOUND)
        saved.pipelineId() == null
        lookup.lookups == lookups

        where:
        reason                               | key       | job              || lookups
        'neither the key nor the job is known' | 'revoked' | 'CERT/other'   || ['key revoked', 'job CERT/other']
        'the job runs no pipeline or several'  | null      | 'CERT/shared'    || ['job CERT/shared']
        'the run sent neither'                 | ''        | null             || []
    }

    def "the decoration is recorded with the comments the library posted and the time OneView received it"() {
        when:
        def result = service.record(request(pipelineKey: 'key-7', job: 'CERT/gui', created: true, limited: true,
                maxComments: 25, findings: [finding(tool: 'AppScan SAST', file: 'src/Login.java', line: 42)]))

        then:
        result.status() == 'RECORDED'
        result.commentsAdded() == 1
        result.lineCommentsAdded() == 3
        saved == new PullRequestDecoration(null, 7L, Instant.parse('2026-11-26T09:30:00.123456Z'), 'CERT/gui', 42L,
                'feature/login', 'abc123', 'gui', BRANCH, REPOSITORY, '12', PULL_REQUEST, 'Login page',
                'feature/login', 'main', true, 3, 1, true, 25, 1, 'Security summary',
                [new PrFinding('AppScan SAST', 'HIGH', 'src/Login.java', 42, 'SQL injection', 'User input reaches a query',
                        'Use a prepared statement')])
    }

    def "a request of an older client, without the fields the library adds now, stays valid"() {
        given:
        def old = new PrDecorationRequest(REPOSITORY, '12', 'bitbucket-token', 'feature/login', 'main',
                [finding(), finding()], 'Security summary', null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null)

        when:
        def result = service.record(old)

        then:
        result == new PrDecorationResult('12', 0, 0, 'RECORDED', NOT_FOUND)
        saved.kind() == null
        !saved.created()
        !saved.limited()
        saved.maxComments() == null
        saved.findingsTotal() == 2
        saved.findings().size() == 2
        saved.sourceBranch() == 'feature/login'
        saved.summary() == 'Security summary'
    }

    def "the first #kept findings are kept and the total counts every finding: #reason"() {
        given:
        def sent = (1..count).collect { finding(title: "Finding $it".toString()) }

        when:
        service.record(request(findings: sent + [null], findingsTotal: total))

        then:
        saved.findings().size() == kept
        saved.findings()*.title() == (1..kept).collect { "Finding $it".toString() }
        saved.findingsTotal() == recordedTotal

        where:
        reason                                       | count | total || kept | recordedTotal
        'at most 500, the first ones'                | 600   | null  || 500  | 600
        'the library sent the first of more'         | 200   | 340   || 200  | 340
        'a total below the findings sent'            | 5     | 3     || 5    | 5
        'a negative total'                           | 2     | -1    || 2    | 2
    }

    def "texts are cut to the size of their column in bytes, never refused"() {
        given:
        def finding = finding(tool: 'T' * 150, severity: 'S' * 80, file: 'src/' + 'f' * 1200,
                title: 'ü' * 1500, description: 'd' * 5000, remediation: '€' * 2000)

        when:
        service.record(request(job: 'J' * 1500, branch: 'b' * 600, commit: 'c' * 200, service: 's' * 300,
                pullRequestId: '1' * 150, title: 'Ä' * 1500, sourceBranch: 'x' * 501, targetBranch: 'y' * 501,
                repositoryUrl: REPOSITORY + '/' + 'r' * 1000, findings: [finding]))

        then: 'a character is never split, so a text of two or three bytes a character may end a byte or two earlier'
        sizes(saved.job(), saved.branch(), saved.commit(), saved.service(), saved.pullRequestId(), saved.title(),
                saved.sourceBranch(), saved.targetBranch(), saved.repositoryUrl()) ==
                [1000, 500, 100, 200, 100, 1999, 500, 500, 1000]
        def kept = saved.findings()[0]
        sizes(kept.tool(), kept.severity(), kept.file(), kept.title(), kept.description(), kept.remediation()) ==
                [100, 50, 1000, 1999, 4000, 3999]
        [kept.tool(), kept.file(), kept.title(), kept.description(), kept.remediation()].every { it.endsWith('...') }
        kept.title().startsWith('üüü')
        saved.title().startsWith('ÄÄÄ')
        saved.pullRequestId().startsWith('111')
    }

    def "texts are trimmed and an empty text is recorded as none, as the library sends it for a value it does not know"() {
        when:
        service.record(request(service: '', branch: '  ', commit: '', title: ' Login page ', pullRequestId: ' 12 ',
                findings: [finding(severity: '', file: '', line: 0)]))

        then:
        saved.service() == null
        saved.branch() == null
        saved.commit() == null
        saved.title() == 'Login page'
        saved.pullRequestId() == '12'
        with(saved.findings()[0]) {
            severity() == null
            file() == null
            line() == null
        }
    }

    def "a pull request address is recorded only when it is an http or https address: #address"() {
        when:
        service.record(request(pullRequestUrl: address))

        then:
        saved.pullRequestUrl() == recorded

        where:
        address                                                  || recorded
        PULL_REQUEST                                             || PULL_REQUEST
        'HTTP://bitbucket.bbh.com/pr/12'                         || 'HTTP://bitbucket.bbh.com/pr/12'
        ' https://bitbucket.org/bbh/gui/pull-requests/12 '       || 'https://bitbucket.org/bbh/gui/pull-requests/12'
        'https://user:token@bitbucket.bbh.com/pr/12'             || 'https://bitbucket.bbh.com/pr/12'
        'javascript:alert(document.cookie)'                      || null
        'ftp://bitbucket.bbh.com/pr/12'                          || null
        'bitbucket.bbh.com/pr/12'                                || null
        'https://'                                               || null
        'https:///pr/12'                                         || null
        'https://bitbucket.bbh.com/pr/12 and more'               || null
        'https://bitbucket.bbh.com/' + 'p' * 2000                || null
        ''                                                       || null
        null                                                     || null
    }

    def "a user name and a password are taken out of the repository address"() {
        when:
        service.record(request(repositoryUrl: 'https://jenkins:s3cr@t@bitbucket.bbh.com/scm/cert/gui.git'))

        then:
        saved.repositoryUrl() == 'https://bitbucket.bbh.com/scm/cert/gui.git'
    }

    def "the kind #kind is recorded as #recorded, so that a kind OneView does not know is never refused"() {
        when:
        def result = service.record(request(kind: kind))

        then:
        result.status() == 'RECORDED'
        saved.kind() == recorded

        where:
        kind         || recorded
        'BRANCH'     || BRANCH
        'STATIC'     || STATIC
        'dast'       || DAST
        ' Static '   || STATIC
        'GOLDEN_FIX' || null
        ''           || null
        null         || null
    }

    def "a count is never negative and a limit below zero is no limit"() {
        when:
        def result = service.record(request(lineComments: -2, generalComments: null, maxComments: maxComments))

        then:
        result.commentsAdded() == 0
        result.lineCommentsAdded() == 0
        saved.lineComments() == 0
        saved.generalComments() == 0
        saved.maxComments() == recorded

        where:
        maxComments || recorded
        -1          || null
        0           || 0
        null        || null
    }

    def "the pipeline key of the run is not recorded"() {
        when:
        service.record(request(pipelineKey: 'key-7'))

        then:
        !('pipelineKey' in PullRequestDecoration.recordComponents*.name)
        !saved.toString().contains('key-7')
    }

    def "the newest decorations of a pipeline are read, #limit asked and #read read"() {
        given:
        def decoration = PullRequestDecoration.recorded(request(), 7L, NOW)

        when:
        def recent = service.recent(7L, limit)

        then:
        recent == [decoration]
        1 * pipelines.load(7L) >> Optional.of(Fixtures.pipeline(id: 7L))
        1 * decorations.findRecentByPipelineId(7L, read) >> [decoration]

        where:
        limit || read
        20    || 20
        100   || 100
        500   || 100
        0     || 1
        -3    || 1
    }

    def "the decorations of a pipeline that does not exist are not read"() {
        when:
        service.recent(99L, 20)

        then:
        def failure = thrown(NoSuchElementException)
        failure.message == 'Pipeline 99 does not exist'
        1 * pipelines.load(99L) >> Optional.empty()
        0 * decorations._
    }

    private static PrDecorationRequest request(Map<String, ?> values = [:]) {
        Map<String, ?> all = [repositoryUrl : REPOSITORY, pullRequestId: '12', credentialsId: null,
                              sourceBranch  : 'feature/login', targetBranch: 'main', findings: [],
                              summary       : 'Security summary', pipelineKey: null, job: 'CERT/gui', build: 42L,
                              branch        : 'feature/login', commit: 'abc123', service: 'gui', kind: 'BRANCH',
                              pullRequestUrl: PULL_REQUEST, title: 'Login page', created: false, lineComments: 3,
                              generalComments: 1, limited: false, maxComments: 25, findingsTotal: null] + values
        new PrDecorationRequest(*PrDecorationRequest.recordComponents.collect { all[it.name] })
    }

    private static PrFinding finding(Map<String, ?> values = [:]) {
        Map<String, ?> all = [tool : 'AppScan SAST', severity: 'HIGH', file: 'src/Login.java', line: 42,
                              title: 'SQL injection', description: 'User input reaches a query',
                              remediation: 'Use a prepared statement'] + values
        new PrFinding(*PrFinding.recordComponents.collect { all[it.name] })
    }

    private static List<Integer> sizes(String... texts) {
        texts.collect { bytes(it) }
    }

    /** Finds the pipeline by the keys and jobs it knows, and remembers what it was asked. */
    static class KnownPipelines implements PipelineLookupPort {

        Map<String, Long> keys = [:]
        Map<String, Long> jobs = [:]
        List<String> lookups = []

        @Override
        Optional<Long> findPipelineIdByKey(String key) {
            lookups << "key $key".toString()
            Optional.ofNullable(keys[key])
        }

        @Override
        Optional<Long> findPipelineIdByJobName(String jobName) {
            lookups << "job $jobName".toString()
            Optional.ofNullable(jobs[jobName])
        }
    }
}
