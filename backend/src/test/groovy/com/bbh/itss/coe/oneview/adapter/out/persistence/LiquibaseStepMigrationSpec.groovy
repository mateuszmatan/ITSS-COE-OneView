package com.bbh.itss.coe.oneview.adapter.out.persistence

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:liquibase-step;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
class LiquibaseStepMigrationSpec extends MigrationSpecification {

    /** The columns of the Liquibase steps after the build, after the unit tests and after the AppScan compile. */
    static final List<String> STEP_COLUMNS = ['BUILD_LIQUIBASE_', 'UNIT_TEST_LIQUIBASE_', 'APPSCAN_COMPILE_LIQUIBASE_']
            .collectMany { prefix ->
                ['TASKS', 'FLAGS', 'DIRECTORY', 'MAVEN_HOME', 'ENVIRONMENT', 'LABEL', 'RETURN_STDOUT'].collect { prefix + it }
            }

    @Override
    protected String changelogFile() {
        'db/changelog/db.changelog-master.yaml'
    }

    def "051 adds the three Liquibase steps, empty for the existing services, and its rollback drops them"() {
        given:
        liquibase.update('')
        rollBackSince('051-')
        long product = insert('DSO_PRODUCT', [CODE: 'LQS', NAME: 'Liquibase steps'])
        long ledger = service(product, 'ledger', 'MAVEN')

        when:
        liquibase.update('')

        then:
        columns('DSO_SERVICE').containsAll(STEP_COLUMNS)
        jdbc.queryForMap('''SELECT BUILD_LIQUIBASE_TASKS, UNIT_TEST_LIQUIBASE_FLAGS, APPSCAN_COMPILE_LIQUIBASE_LABEL,
                BUILD_LIQUIBASE_RETURN_STDOUT, UNIT_TEST_LIQUIBASE_RETURN_STDOUT, APPSCAN_COMPILE_LIQUIBASE_RETURN_STDOUT
                FROM DSO_SERVICE WHERE ID = ?''', ledger) ==
                [BUILD_LIQUIBASE_TASKS: null, UNIT_TEST_LIQUIBASE_FLAGS: null, APPSCAN_COMPILE_LIQUIBASE_LABEL: null,
                 BUILD_LIQUIBASE_RETURN_STDOUT: 0, UNIT_TEST_LIQUIBASE_RETURN_STDOUT: 0,
                 APPSCAN_COMPILE_LIQUIBASE_RETURN_STDOUT: 0]

        when:
        jdbc.update('''UPDATE DSO_SERVICE SET BUILD_LIQUIBASE_TASKS = 'validate', UNIT_TEST_LIQUIBASE_ENVIRONMENT = 'A=1',
                APPSCAN_COMPILE_LIQUIBASE_RETURN_STDOUT = 1 WHERE ID = ?''', ledger)

        then:
        jdbc.queryForMap('''SELECT BUILD_LIQUIBASE_TASKS, UNIT_TEST_LIQUIBASE_ENVIRONMENT,
                APPSCAN_COMPILE_LIQUIBASE_RETURN_STDOUT FROM DSO_SERVICE WHERE ID = ?''', ledger) ==
                [BUILD_LIQUIBASE_TASKS: 'validate', UNIT_TEST_LIQUIBASE_ENVIRONMENT: 'A=1',
                 APPSCAN_COMPILE_LIQUIBASE_RETURN_STDOUT: 1]

        when:
        jdbc.update("UPDATE DSO_SERVICE SET $column = 2 WHERE ID = ?" as String, ledger)

        then:
        thrown(DataIntegrityViolationException)

        when:
        rollBackSince('051-')

        then:
        columns('DSO_SERVICE').intersect(STEP_COLUMNS) == []
        jdbc.queryForObject('SELECT NAME FROM DSO_SERVICE WHERE ID = ?', String, ledger) == 'ledger'

        cleanup:
        jdbc.update('DELETE FROM DSO_SERVICE WHERE PRODUCT_ID = ?', product)
        jdbc.update('DELETE FROM DSO_PRODUCT WHERE ID = ?', product)
        liquibase.update('')

        where:
        column << ['BUILD_LIQUIBASE_RETURN_STDOUT', 'UNIT_TEST_LIQUIBASE_RETURN_STDOUT',
                   'APPSCAN_COMPILE_LIQUIBASE_RETURN_STDOUT']
    }

    def "051 copies the SonarScanner version of every Flutter service to the SonarQube settings and keeps the old column, and its rollback copies the version back"() {
        given:
        liquibase.update('')
        rollBackSince('051-')
        long product = insert('DSO_PRODUCT', [CODE: 'SSV', NAME: 'Scanner versions'])
        long app = service(product, 'mobile-app', 'FLUTTER', [FLUTTER_SONAR_SCANNER_VERSION: '5.0.1.3006'])
        long web = service(product, 'mobile-web', 'FLUTTER')
        long api = service(product, 'api', 'GRADLE', [FLUTTER_SONAR_SCANNER_VERSION: '4.8.0.2856'])

        when:
        liquibase.update('')

        then: 'only a Flutter service rendered the version, so only its version is copied'
        versions('SONAR_SCANNER_VERSION') == [api: null, 'mobile-app': '5.0.1.3006', 'mobile-web': null]

        and: 'the old column keeps its values for the pod of the release before 051'
        versions('FLUTTER_SONAR_SCANNER_VERSION') == [api: '4.8.0.2856', 'mobile-app': '5.0.1.3006', 'mobile-web': null]

        when: 'a Flutter service sets its version, and a Gradle service one of its own'
        jdbc.update("UPDATE DSO_SERVICE SET SONAR_SCANNER_VERSION = '6.2.1.4610' WHERE ID = ?", web)
        jdbc.update("UPDATE DSO_SERVICE SET SONAR_SCANNER_VERSION = '5.0.1.3006' WHERE ID = ?", api)
        rollBackSince('051-service-sonar-scanner-version')

        then: 'every Flutter service keeps its version in the old column, which the portal before 051 reads'
        !('SONAR_SCANNER_VERSION' in columns('DSO_SERVICE'))
        columns('DSO_SERVICE').containsAll(STEP_COLUMNS)
        versions('FLUTTER_SONAR_SCANNER_VERSION') == [api: '4.8.0.2856', 'mobile-app': '5.0.1.3006', 'mobile-web': '6.2.1.4610']

        when:
        liquibase.update('')

        then:
        versions('SONAR_SCANNER_VERSION') == [api: null, 'mobile-app': '5.0.1.3006', 'mobile-web': '6.2.1.4610']
        jdbc.queryForObject('SELECT BUILD_TOOL FROM DSO_SERVICE WHERE ID = ?', String, app) == 'FLUTTER'

        cleanup:
        jdbc.update('DELETE FROM DSO_SERVICE WHERE PRODUCT_ID = ?', product)
        jdbc.update('DELETE FROM DSO_PRODUCT WHERE ID = ?', product)
        liquibase.update('')
    }

    def "after 051 the pod of the release before it still saves and reads its services, as during a rolling update"() {
        given:
        liquibase.update('')
        long product = insert('DSO_PRODUCT', [CODE: 'SSR', NAME: 'Rolling update'])

        when: 'the old pod saves a Flutter service with the columns it maps: the old version column and none of 051'
        long app = service(product, 'mobile-app', 'FLUTTER', [FLUTTER_SONAR_SCANNER_VERSION: '5.0.1.3006'])
        jdbc.update("UPDATE DSO_SERVICE SET FLUTTER_SONAR_SCANNER_VERSION = '6.2.1.4610' WHERE ID = ?", app)

        then: 'the columns of 051 take their defaults'
        jdbc.queryForMap('''SELECT FLUTTER_SONAR_SCANNER_VERSION, SONAR_SCANNER_VERSION, BUILD_LIQUIBASE_RETURN_STDOUT,
                UNIT_TEST_LIQUIBASE_RETURN_STDOUT, APPSCAN_COMPILE_LIQUIBASE_RETURN_STDOUT
                FROM DSO_SERVICE WHERE ID = ?''', app) ==
                [FLUTTER_SONAR_SCANNER_VERSION: '6.2.1.4610', SONAR_SCANNER_VERSION: null, BUILD_LIQUIBASE_RETURN_STDOUT: 0,
                 UNIT_TEST_LIQUIBASE_RETURN_STDOUT: 0, APPSCAN_COMPILE_LIQUIBASE_RETURN_STDOUT: 0]

        cleanup:
        jdbc.update('DELETE FROM DSO_SERVICE WHERE PRODUCT_ID = ?', product)
        jdbc.update('DELETE FROM DSO_PRODUCT WHERE ID = ?', product)
    }

    def "a start that stopped after the version column was added copies the versions when it runs 051 again"() {
        given:
        liquibase.update('')
        rollBackSince('051-')
        long product = insert('DSO_PRODUCT', [CODE: 'SSC', NAME: 'Scanner copy'])
        service(product, 'mobile-app', 'FLUTTER', [FLUTTER_SONAR_SCANNER_VERSION: '5.0.1.3006'])

        when:
        updateBefore('051-service-sonar-scanner-version-copy')

        then:
        versions('SONAR_SCANNER_VERSION') == ['mobile-app': null]

        when:
        liquibase.update('')

        then:
        versions('SONAR_SCANNER_VERSION') == ['mobile-app': '5.0.1.3006']

        cleanup:
        jdbc.update('DELETE FROM DSO_SERVICE WHERE PRODUCT_ID = ?', product)
        jdbc.update('DELETE FROM DSO_PRODUCT WHERE ID = ?', product)
        liquibase.update('')
    }

    def "051 is the last changeset of the changelog, so that it runs after every earlier one"() {
        when:
        liquibase.update('')

        then: 'one statement each, so that a statement that fails leaves nothing of its changeset applied'
        jdbc.queryForList('SELECT ID FROM DATABASECHANGELOG ORDER BY ORDEREXECUTED', String).takeRight(4) ==
                ['051-service-liquibase-step', '051-service-liquibase-step-check', '051-service-sonar-scanner-version',
                 '051-service-sonar-scanner-version-copy']
    }

    private long service(long productId, String name, String buildTool, Map<String, ?> values = [:]) {
        insert('DSO_SERVICE', [PRODUCT_ID: productId, NAME: name, DISPLAY_ORDER: 0, BUILD_TOOL: buildTool, SOURCE_DIR: '.',
                               DEPLOY_TARGET: 'VM', APPSCAN_APP_ID: '109f44ac-cc06-4ca0-884e-d944904f7019',
                               INFLUX_PROJECT: "SSV-$name".toString()] + values)
    }

    private Map<String, String> versions(String column) {
        jdbc.queryForList("SELECT NAME, $column AS VERSION FROM DSO_SERVICE ORDER BY NAME" as String)
                .collectEntries { [it.NAME, it.VERSION] }
    }
}
