package com.bbh.itss.coe.oneview.adapter.out.persistence

import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomation
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomationSettings
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification
import spock.lang.Subject

import static com.bbh.itss.coe.oneview.domain.shared.Failures.STALE_VERSION
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:jenkins-automation;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = NONE)
@Import(JenkinsAutomationPersistenceAdapter)
class JenkinsAutomationPersistenceAdapterSpec extends Specification {

    @Subject
    @Autowired
    JenkinsAutomationPersistenceAdapter adapter

    @Autowired
    JdbcTemplate jdbc

    def settings = JenkinsAutomationSettings.builder().genericUser('svc-oneview').oisHost('ois-qc.bbh.com')
            .oisApiKey('a2a-key').pollSchedule('H/10 * * * *')
            .libraryRepositoryUrl('https://bitbucket.bbh.com/scm/dso/dsoenhanced.git').libraryCredentialsId('bb-reader')
            .build()

    def "the settings are stored in the one row and read back unchanged"() {
        expect:
        adapter.load() == Optional.empty()

        when:
        def saved = adapter.save(new JenkinsAutomation(settings, 0, null))

        then:
        saved.settings() == settings
        saved.version() == 0
        saved.updatedAt() != null
        adapter.load().get() == saved
        jdbc.queryForMap('SELECT ID, GENERIC_USER, OIS_HOST, POLL_SCHEDULE, LIBRARY_VERSION FROM DSO_JENKINS_AUTOMATION') ==
                [ID: 1, GENERIC_USER: 'svc-oneview', OIS_HOST: 'ois-qc.bbh.com', POLL_SCHEDULE: 'H/10 * * * *',
                 LIBRARY_VERSION: 'main']
    }

    def "a change is written over the stored settings as the next version, and an older one is refused"() {
        given:
        def first = adapter.save(new JenkinsAutomation(settings, 0, null))

        when:
        def second = adapter.save(first.change(0, JenkinsAutomationSettings.NONE))

        then:
        second.version() == 1
        !second.settings().ready()
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_JENKINS_AUTOMATION', Integer) == 1
        jdbc.queryForObject('SELECT OIS_API_KEY FROM DSO_JENKINS_AUTOMATION', String) == null

        when:
        adapter.save(new JenkinsAutomation(settings, 0, null))

        then:
        def stale = thrown(IllegalStateException)
        stale.message == STALE_VERSION
    }
}
