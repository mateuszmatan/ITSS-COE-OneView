package com.bbh.itss.coe.oneview.adapter.out.persistence

import com.bbh.itss.coe.oneview.domain.bitbucket.PrFinding
import com.bbh.itss.coe.oneview.domain.bitbucket.PullRequestDecoration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.coe.oneview.domain.bitbucket.DecorationKind.BRANCH
import static com.bbh.itss.coe.oneview.domain.bitbucket.DecorationKind.STATIC
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:pull-request-decoration-adapter;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = NONE)
@Import(PullRequestDecorationPersistenceAdapter)
class PullRequestDecorationPersistenceAdapterSpec extends Specification {

    static final Instant RECORDED = Instant.parse('2026-11-26T09:30:00.123456Z')

    @Autowired
    PullRequestDecorationPersistenceAdapter adapter

    @Autowired
    TestEntityManager entities

    @Autowired
    JdbcTemplate jdbc

    def "a decoration is stored with its findings in the order they were sent and read back the same"() {
        given:
        def findings = [new PrFinding('AppScan SAST', 'HIGH', 'src/Login.java', 42, 'SQL injection',
                'User input reaches a query', "Use a prepared statement:\n\n    query(sql, id)"),
                        new PrFinding('Nexus IQ', null, 'build.gradle', null, 'log4j-core 2.14.1', null,
                                'Upgrade to 2.17.1'),
                        new PrFinding('SonarQube', 'MAJOR', null, null, 'java:S1192', 'Duplicated literal', null)]

        when:
        def saved = adapter.save(decoration(7L, RECORDED, findings))
        entities.clear()
        def read = adapter.findRecentByPipelineId(7L, 20)

        then:
        saved.id() != null
        read == [saved]
        read[0].findings() == findings
        read[0].recordedAt() == RECORDED
        jdbc.queryForList('''SELECT POSITION, TOOL, FILE_PATH, LINE_NUMBER FROM DSO_PR_DECORATION_FINDING
                WHERE DECORATION_ID = ? ORDER BY POSITION''', saved.id()) ==
                [[POSITION: 0, TOOL: 'AppScan SAST', FILE_PATH: 'src/Login.java', LINE_NUMBER: 42],
                 [POSITION: 1, TOOL: 'Nexus IQ', FILE_PATH: 'build.gradle', LINE_NUMBER: null],
                 [POSITION: 2, TOOL: 'SonarQube', FILE_PATH: null, LINE_NUMBER: null]]
        jdbc.queryForMap('''SELECT KIND, PULL_REQUEST_CREATED, COMMENTS_LIMITED, MAX_COMMENTS, FINDINGS_TOTAL
                FROM DSO_PR_DECORATION WHERE ID = ?''', saved.id()) ==
                [KIND: 'BRANCH', PULL_REQUEST_CREATED: 1, COMMENTS_LIMITED: 0, MAX_COMMENTS: 25, FINDINGS_TOTAL: 340]
    }

    def "a decoration without a kind, a pipeline or findings is stored as well"() {
        when:
        def saved = adapter.save(new PullRequestDecoration(null, null, RECORDED, null, null, null, null, null, null,
                null, '12', null, null, null, null, false, 0, 0, false, null, 0, null, []))
        entities.clear()

        then:
        jdbc.queryForMap('SELECT PIPELINE_ID, KIND, MAX_COMMENTS FROM DSO_PR_DECORATION WHERE ID = ?', saved.id()) ==
                [PIPELINE_ID: null, KIND: null, MAX_COMMENTS: null]
        saved.findings() == []
    }

    def "a finding without any text reads back as one, so that the findings keep their count and order"() {
        given:
        def empty = new PrFinding(null, null, null, null, null, null, null)
        def last = new PrFinding('SonarQube', 'MAJOR', null, null, 'java:S1192', null, null)

        when:
        def saved = adapter.save(decoration(7L, RECORDED, [empty, last]))
        entities.clear()

        then:
        adapter.findRecentByPipelineId(7L, 20)[0].findings() == [empty, last]
        saved.findings() == [empty, last]
    }

    def "the newest decorations of a pipeline come first, at most as many as asked, and none of another pipeline"() {
        given:
        def oldest = adapter.save(decoration(7L, RECORDED.minusSeconds(120), []))
        def newest = adapter.save(decoration(7L, RECORDED, []))
        def sameTime = adapter.save(decoration(7L, RECORDED, []))
        adapter.save(decoration(8L, RECORDED.plusSeconds(60), []))
        adapter.save(decoration(null, RECORDED.plusSeconds(60), []))
        entities.clear()

        expect:
        adapter.findRecentByPipelineId(7L, 20)*.id() == [sameTime.id(), newest.id(), oldest.id()]
        adapter.findRecentByPipelineId(7L, 2)*.id() == [sameTime.id(), newest.id()]
        adapter.findRecentByPipelineId(7L, 0)*.id() == [sameTime.id()]
        adapter.findRecentByPipelineId(9L, 20) == []
    }

    def "the findings of every decoration that is read are read with it"() {
        given:
        (1..25).each { adapter.save(decoration(7L, RECORDED.plusSeconds(it), [finding(it)])) }
        entities.clear()

        when:
        def read = adapter.findRecentByPipelineId(7L, 25)

        then:
        read.collect { it.findings()*.title() } == (25..1).collect { ["Finding $it".toString()] }
    }

    private static PullRequestDecoration decoration(Long pipelineId, Instant recordedAt, List<PrFinding> findings) {
        new PullRequestDecoration(null, pipelineId, recordedAt, 'CERT/gui', 42L, 'feature/login', 'abc123', 'gui',
                pipelineId == 8L ? STATIC : BRANCH, 'https://bitbucket.bbh.com/projects/CERT/repos/gui', '12',
                'https://bitbucket.bbh.com/projects/CERT/repos/gui/pull-requests/12', 'Login page', 'feature/login',
                'main', true, 3, 1, false, 25, 340, 'Security summary', findings)
    }

    private static PrFinding finding(int number) {
        new PrFinding('AppScan SAST', 'HIGH', 'src/Login.java', number, "Finding $number".toString(), null, null)
    }
}
