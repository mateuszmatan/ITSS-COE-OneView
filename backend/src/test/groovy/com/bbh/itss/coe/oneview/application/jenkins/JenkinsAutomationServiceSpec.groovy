package com.bbh.itss.coe.oneview.application.jenkins

import com.bbh.itss.coe.oneview.application.jenkins.port.out.GenericUserSecretPort
import com.bbh.itss.coe.oneview.application.jenkins.port.out.JenkinsAutomationRepositoryPort
import com.bbh.itss.coe.oneview.application.jenkins.port.out.JenkinsPort
import com.bbh.itss.coe.oneview.domain.jenkins.GenericLogin
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomation
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomationSettings
import spock.lang.Specification

import java.time.Instant

class JenkinsAutomationServiceSpec extends Specification {

    static final JenkinsAutomationSettings READY = JenkinsAutomationSettings.builder().genericUser('svc-oneview')
            .oisHost('ois-qc.bbh.com').oisApiKey('a2a-key').build()

    JenkinsAutomationRepositoryPort repository = Mock()
    GenericUserSecretPort secrets = Mock()
    JenkinsPort jenkins = Mock()
    def service = new JenkinsAutomationService(repository, secrets, jenkins)

    def "before anything is saved the automation is not set"() {
        given:
        repository.load() >> Optional.empty()

        expect:
        service.current() == JenkinsAutomation.NOT_SET
    }

    def "a change is saved over what is stored, keeping the API key that was not sent again"() {
        given:
        repository.load() >> Optional.of(new JenkinsAutomation(READY, 2, Instant.EPOCH))

        when:
        service.update(2, READY.toBuilder().oisApiKey(null).pollSchedule('H/2 * * * *').build())

        then:
        1 * repository.save({ JenkinsAutomation saved ->
            saved.version() == 2 && saved.settings().oisApiKey() == 'a2a-key' &&
                    saved.settings().pollSchedule() == 'H/2 * * * *'
        }) >> { JenkinsAutomation saved -> saved }
    }

    def "the check fetches the secret from OIS and signs in to Jenkins with it"() {
        given:
        repository.load() >> Optional.of(new JenkinsAutomation(READY, 2, Instant.EPOCH))

        when:
        def check = service.check('https://jenkins.bbh.com/')

        then:
        1 * secrets.fetch('ois-qc.bbh.com', 'a2a-key') >> 'secret-from-ois'
        1 * jenkins.whoAmI('https://jenkins.bbh.com', new GenericLogin('svc-oneview', 'secret-from-ois')) >> 'OneView service'
        check.ok()
        check.message() == 'OIS gave the secret and https://jenkins.bbh.com knows the generic user as OneView service.'
        !check.message().contains('secret-from-ois')
    }

    def "a check that fails says why instead of failing itself"() {
        given:
        repository.load() >> Optional.of(new JenkinsAutomation(READY, 2, Instant.EPOCH))
        secrets.fetch(_, _) >> { throw new UncheckedIOException('OIS could not be reached', new IOException()) }

        when:
        def check = service.check('https://jenkins.bbh.com')

        then:
        !check.ok()
        check.message() == 'OIS could not be reached'
        0 * jenkins._
    }

    def "a check without settings names what is missing and asks neither OIS nor Jenkins"() {
        given:
        repository.load() >> Optional.empty()

        when:
        def check = service.check('https://jenkins.bbh.com')

        then:
        !check.ok()
        check.message().contains('the generic user, the OIS host, the OIS API key')
        0 * secrets._
        0 * jenkins._
    }
}
