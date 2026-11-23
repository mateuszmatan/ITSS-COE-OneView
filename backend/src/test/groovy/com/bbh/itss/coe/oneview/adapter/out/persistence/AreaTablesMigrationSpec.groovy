package com.bbh.itss.coe.oneview.adapter.out.persistence

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:area-tables;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
class AreaTablesMigrationSpec extends MigrationSpecification {

    static final List<String> AREA_TABLES = ['QA_SETTINGS', 'QA_APPLICATION', 'AGL_APPLICATION', 'AGL_ASSESSMENT',
                                             'AGL_ASSESSMENT_LEVEL']

    @Override
    protected String changelogFile() {
        'db/changelog/db.changelog-master.yaml'
    }

    def "047 adds the QM Metric Tool and agile tables, 048 the business service and application of each action, and both roll back"() {
        given:
        liquibase.update('')
        rollBackSince('047-')
        jdbc.update('''INSERT INTO DSO_ACTION_LOG (CREATED_AT, USERNAME, USER_DISPLAY_NAME, ACTION_TYPE, RESOURCE_TYPE, RESOURCE_NAME, STATUS)
                VALUES (SYSDATE, 'anna', 'Anna', 'CREATE_PRODUCT', 'PRODUCT', 'Ledger', 'SUCCESS')''')
        long action = jdbc.queryForObject('SELECT MAX(ID) FROM DSO_ACTION_LOG', Long)

        expect:
        tables().disjoint(AREA_TABLES)
        !columns('DSO_ACTION_LOG').contains('PRODUCT_ID')

        when:
        liquibase.update('')

        then:
        tables().containsAll(AREA_TABLES)
        columns('DSO_ACTION_LOG').containsAll(['DEPARTMENT_ID', 'PRODUCT_ID'])
        jdbc.queryForMap('SELECT DEPARTMENT_ID, PRODUCT_ID FROM DSO_ACTION_LOG WHERE ID = ?', action) ==
                [DEPARTMENT_ID: null, PRODUCT_ID: null]

        when:
        rollBackSince('047-')

        then:
        tables().disjoint(AREA_TABLES)
        !columns('DSO_ACTION_LOG').contains('DEPARTMENT_ID')
        jdbc.queryForObject('SELECT RESOURCE_NAME FROM DSO_ACTION_LOG WHERE ID = ?', String, action) == 'Ledger'
    }

    def "the area tables keep one row of BBH targets, flags of 0 or 1, known frameworks, levels from 1 to 5 and rows of existing products"() {
        given:
        liquibase.update('')
        long product = insert('DSO_PRODUCT', [CODE: 'LEDGER', NAME: 'Ledger'])
        long assessment = insert('AGL_ASSESSMENT', [PRODUCT_ID: product, ASSESSED_ON: new java.sql.Date(0L),
                                                    ASSESSOR: 'Ewa'])

        when:
        jdbc.update("""INSERT INTO QA_SETTINGS (ID, COVERAGE_TARGET, PASS_RATE_TARGET, MAX_CRITICAL_DEFECTS, CREATED_AT,
                UPDATED_AT) VALUES (2, 80, 95, 0, SYSDATE, SYSDATE)""")

        then:
        thrown(DataIntegrityViolationException)

        when:
        jdbc.update('INSERT INTO QA_APPLICATION (PRODUCT_ID, ENABLED, CREATED_AT, UPDATED_AT) VALUES (?, 2, SYSDATE, SYSDATE)',
                product)

        then:
        thrown(DataIntegrityViolationException)

        when:
        jdbc.update('INSERT INTO AGL_APPLICATION (PRODUCT_ID, ENABLED, CREATED_AT, UPDATED_AT) VALUES (?, 2, SYSDATE, SYSDATE)',
                product)

        then:
        thrown(DataIntegrityViolationException)

        when:
        jdbc.update("""INSERT INTO AGL_APPLICATION (PRODUCT_ID, FRAMEWORK, CREATED_AT, UPDATED_AT)
                VALUES (?, 'XP', SYSDATE, SYSDATE)""", product)

        then:
        thrown(DataIntegrityViolationException)

        when:
        jdbc.update("INSERT INTO AGL_ASSESSMENT_LEVEL (ASSESSMENT_ID, DIMENSION, LEVEL_REACHED) VALUES (?, 'TEAM', 6)",
                assessment)

        then:
        thrown(DataIntegrityViolationException)

        when:
        jdbc.update('INSERT INTO QA_APPLICATION (PRODUCT_ID, CREATED_AT, UPDATED_AT) VALUES (999999, SYSDATE, SYSDATE)')

        then:
        thrown(DataIntegrityViolationException)

        when:
        jdbc.update("INSERT INTO AGL_ASSESSMENT_LEVEL (ASSESSMENT_ID, DIMENSION, LEVEL_REACHED) VALUES (?, 'TEAM', 5)",
                assessment)
        jdbc.update('DELETE FROM DSO_PRODUCT WHERE ID = ?', product)

        then:
        jdbc.queryForObject('SELECT COUNT(*) FROM AGL_ASSESSMENT_LEVEL', Integer) == 0
        jdbc.queryForObject('SELECT COUNT(*) FROM AGL_ASSESSMENT', Integer) == 0
    }
}
