package com.bbh.itss.coe.oneview.adapter.out.persistence

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.SimpleJdbcInsert
import org.springframework.transaction.annotation.Transactional

import java.sql.Timestamp

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:pull-request-decorations;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
class PullRequestDecorationMigrationSpec extends MigrationSpecification {

    static final List<String> TABLES = ['DSO_PR_DECORATION', 'DSO_PR_DECORATION_FINDING']

    @Override
    protected String changelogFile() {
        'db/changelog/db.changelog-master.yaml'
    }

    def "052 adds the decorations with their findings, which go with their decoration, and its rollback drops them"() {
        when:
        liquibase.update('')
        rollBackSince('052-')

        then:
        tables().intersect(TABLES) == []

        when:
        liquibase.update('')
        long decoration = decoration(PIPELINE_ID: 7, PULL_REQUEST_ID: '12', KIND: 'BRANCH')
        finding(decoration, 0, 'AppScan SAST')
        finding(decoration, 1, 'SonarQube')

        then:
        tables().containsAll(TABLES)
        objects('INDEXES', 'INDEX_NAME', 'IX_DSO_PR_DECORATION_PIPELINE') > 0
        jdbc.queryForMap('''SELECT PULL_REQUEST_CREATED, LINE_COMMENTS, GENERAL_COMMENTS, COMMENTS_LIMITED,
                MAX_COMMENTS, FINDINGS_TOTAL, SUMMARY FROM DSO_PR_DECORATION WHERE ID = ?''', decoration) ==
                [PULL_REQUEST_CREATED: 0, LINE_COMMENTS: 0, GENERAL_COMMENTS: 0, COMMENTS_LIMITED: 0, MAX_COMMENTS: null,
                 FINDINGS_TOTAL: 0, SUMMARY: null]
        jdbc.queryForList('SELECT TOOL FROM DSO_PR_DECORATION_FINDING WHERE DECORATION_ID = ? ORDER BY POSITION',
                String, decoration) == ['AppScan SAST', 'SonarQube']

        when: 'the decoration is deleted'
        jdbc.update('DELETE FROM DSO_PR_DECORATION WHERE ID = ?', decoration)

        then: 'its findings go with it'
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PR_DECORATION_FINDING', Integer) == 0

        when:
        rollBackSince('052-')

        then:
        tables().intersect(TABLES) == []

        cleanup:
        liquibase.update('')
    }

    def "052 refuses a decoration with #wrong"() {
        given:
        liquibase.update('')

        when:
        decoration([PULL_REQUEST_ID: '12'] + values)

        then:
        thrown(DataIntegrityViolationException)

        cleanup:
        jdbc.update('DELETE FROM DSO_PR_DECORATION')

        where:
        wrong                       | values
        'a kind OneView never maps' | [KIND: 'OTHER']
        'a flag that is not 0 or 1' | [PULL_REQUEST_CREATED: 2]
        'a flag of the limit'       | [COMMENTS_LIMITED: -1]
        'a negative count'          | [LINE_COMMENTS: -1]
        'a negative limit'          | [MAX_COMMENTS: -5]
        'no pull request'           | [PULL_REQUEST_ID: null]
    }

    def "052 refuses a finding #wrong"() {
        given:
        liquibase.update('')
        long decoration = decoration(PULL_REQUEST_ID: '12')
        finding(decoration, 0, 'Nexus IQ')

        when:
        finding(ofTheDecoration ? decoration : 999_999L, 0, 'AppScan DAST')

        then:
        thrown(DataIntegrityViolationException)

        cleanup:
        jdbc.update('DELETE FROM DSO_PR_DECORATION')

        where:
        wrong                                  | ofTheDecoration
        'of a decoration that does not exist'  | false
        'at the position of another finding'   | true
    }

    def "052 runs after every earlier changeset, as three changesets in their order"() {
        when:
        liquibase.update('')

        then: 'one statement each, so that a statement that fails leaves nothing of its changeset applied'
        jdbc.queryForList('SELECT ID FROM DATABASECHANGELOG ORDER BY ORDEREXECUTED', String)
                .dropWhile { !it.startsWith('052') }.take(3) ==
                ['052-pr-decoration', '052-pr-decoration-pipeline-index', '052-pr-decoration-finding']
    }

    private long decoration(Map<String, ?> values) {
        Map<String, ?> row = [RECORDED_AT: Timestamp.valueOf('2026-11-26 09:30:00')] + values
        new SimpleJdbcInsert(jdbc).withTableName('DSO_PR_DECORATION').usingColumns(row.keySet() as String[])
                .usingGeneratedKeyColumns('ID').executeAndReturnKey(row).longValue()
    }

    private void finding(long decoration, int position, String tool) {
        jdbc.update('INSERT INTO DSO_PR_DECORATION_FINDING (DECORATION_ID, POSITION, TOOL) VALUES (?, ?, ?)',
                decoration, position, tool)
    }

    private int objects(String view, String column, String name) {
        jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.$view WHERE $column = ?", Integer, name)
    }
}
