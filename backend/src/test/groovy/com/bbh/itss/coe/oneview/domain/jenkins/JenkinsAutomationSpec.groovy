package com.bbh.itss.coe.oneview.domain.jenkins

import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.coe.oneview.domain.shared.Failures.STALE_VERSION

class JenkinsAutomationSpec extends Specification {

    static JenkinsAutomationSettings.JenkinsAutomationSettingsBuilder settings() {
        JenkinsAutomationSettings.builder().genericUser('svc-oneview').oisHost('ois-qc.bbh.com').oisApiKey('a2a-key')
    }

    def stored = new JenkinsAutomation(settings().build(), 3, Instant.parse('2026-11-28T10:00:00Z'))

    def "without settings nothing is ready and the three settings OneView needs are named"() {
        expect:
        with(JenkinsAutomation.NOT_SET.settings()) {
            !ready()
            missing() == ['the generic user', 'the OIS host', 'the OIS API key']
            pollSchedule() == 'H/5 * * * *'
            libraryVersion() == 'main'
            library('DevSecOpsJenkinsLibrary') == null
        }
    }

    def "the generic user, the OIS host and the OIS API key together make the automation ready"() {
        expect:
        settings().build().ready()
        settings().oisHost(' ').build().missing() == ['the OIS host']
        settings().oisApiKey(null).build().missing() == ['the OIS API key']
    }

    def "an API key without a generic user is not kept"() {
        expect:
        settings().genericUser(' ').build().oisApiKey() == null
    }

    def "a Multibranch job carries the library when its repository is set"() {
        expect:
        settings().libraryRepositoryUrl('https://bitbucket.bbh.com/scm/dso/dsoenhanced.git').libraryVersion(' ')
                .libraryCredentialsId('bb-reader').build().library('DevSecOpsJenkinsLibrary') ==
                new FolderLibrary('DevSecOpsJenkinsLibrary', 'https://bitbucket.bbh.com/scm/dso/dsoenhanced.git', 'main',
                        'bb-reader')
    }

    def "a change without an API key keeps the stored key while the generic user stays"() {
        when:
        def changed = stored.change(3, settings().oisApiKey(null).oisHost('ois-qc.bbh.com:8443').build())

        then:
        changed.settings().oisApiKey() == 'a2a-key'
        changed.settings().oisHost() == 'ois-qc.bbh.com:8443'
        changed.version() == 3
    }

    def "a new API key replaces the stored one, and clearing the generic user removes it"() {
        expect:
        stored.change(3, settings().oisApiKey('new-key').build()).settings().oisApiKey() == 'new-key'
        stored.change(3, settings().genericUser(null).oisApiKey(null).build()).settings().oisApiKey() == null
    }

    def "a change of settings read at an older version is refused"() {
        when:
        stored.change(2, settings().build())

        then:
        def refused = thrown(IllegalStateException)
        refused.message == STALE_VERSION
    }

    def "settings that cannot work are refused on their fields"() {
        when:
        stored.change(3, changed)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems*.field() == fields

        where:
        changed                                                  | fields
        settings().oisHost('https://ois-qc.bbh.com').build()     | ['oisHost']
        settings().oisHost('ois qc').build()                     | ['oisHost']
        settings().pollSchedule('often <b>').build()             | ['pollSchedule']
        settings().genericUser('u' * 201).build()                | ['genericUser']
    }
}
