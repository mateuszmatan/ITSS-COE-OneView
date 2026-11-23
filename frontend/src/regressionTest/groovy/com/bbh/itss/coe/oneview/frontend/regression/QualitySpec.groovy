package com.bbh.itss.coe.oneview.frontend.regression

import com.bbh.itss.coe.oneview.frontend.support.OvSpecification
import com.microsoft.playwright.Locator

import static com.bbh.itss.coe.oneview.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class QualitySpec extends OvSpecification {

    static final List<String> BBH_TARGETS = ['Code coverage (%)', 'Test pass rate (%)', 'Open critical defects (at most)']

    def "the QM Metric Tool sums up the applications of BBH and shows one of them alone with its targets"() {
        when:
        open('/qa')

        then:
        assertThat(page.locator('h1')).hasText('QM Metric Tool')
        assertThat(page.locator('.page-header .page-description')).hasText('Quality assurance of every application: the quality targets it works to, who leads its quality, and where its test results, code quality and defects are, with their trends in Grafana from InfluxDB.')
        assertThat(link('Set up in OneView Admin', true)).hasAttribute('href', '/admin/qa')
        assertThat(page.locator('.summary .card-header .muted')).hasText('BBH')
        assertThat(stat('Applications')).hasText('2')
        assertThat(stat('Take part')).hasText('1')
        assertThat(stat('Do not take part yet')).hasText('1')
        assertThat(stat('With a Grafana dashboard')).hasText('1')
        assertThat(page.locator('.bbh-targets')).hasText('BBH targets: Code coverage at least 80%, test pass rate at least 95%, open critical defects at most 0. An application can set its own in OneView Admin.')
        assertThat(gridHeaders()).hasText(['Application', 'Business service', 'QM Metric Tool', 'QA lead', 'Targets', 'Grafana'] as String[])
        assertThat(gridCells(page.locator('body'), 'name')).hasText(['CertScanner CERTSCANNER', 'Payments Hub PAYHUB'] as String[])
        assertThat(gridCells(page.locator('body'), 'enabled')).hasText(['Takes part', 'Not set up'] as String[])
        assertThat(gridCells(page.locator('body'), 'targets')).hasText(['Code coverage at least 85%', 'The BBH targets'] as String[])
        assertThat(gridCell(gridRow(page.locator('body'), 'CertScanner'), 'dashboard').locator('a'))
                .hasAttribute('href', 'https://grafana.bbh.com/d/cert-quality')
        assertThat(page.locator('ov-area-plan h3')).hasText(['Test results', 'Code quality', 'Defects', 'Quality trends'] as String[])

        when:
        button('Show CertScanner alone', true).click()

        then:
        assertThat(selected(contextBar(), 'Application')).hasText('CertScanner')
        assertThat(selected(contextBar(), 'Business service')).hasText('Corporate Technology')
        assertThat(page.locator('.profile h2')).hasText('CertScanner CERTSCANNER')
        assertThat(page.locator('.profile .participation')).hasText('Takes part')
        facts() == ['Business service: Corporate Technology', 'QA lead: Ann Lee', 'SonarQube project: com.bbh:cert-scanner',
                    'Defect project: CERT', 'Grafana dashboard: Open the quality dashboard']
        assertThat(page.locator('.profile a.dashboard')).hasAttribute('href', 'https://grafana.bbh.com/d/cert-quality')
        assertThat(stat('Code coverage · own target')).hasText('at least 85%')
        assertThat(stat('Test pass rate · BBH target')).hasText('at least 95%')
        assertThat(stat('Open critical defects · BBH target')).hasText('at most 0')
        assertThat(page.locator('.summary')).hasCount(0)

        when:
        chooseApplication('All applications of Corporate Technology')

        then:
        assertThat(page.locator('.summary .card-header .muted')).hasText('Corporate Technology')
        assertThat(stat('Applications')).hasText('1')
        assertThat(gridHeaders()).hasText(['Application', 'QM Metric Tool', 'QA lead', 'Targets', 'Grafana'] as String[])
        ownErrors().isEmpty()
    }

    def "an administrator changes the BBH targets, and they apply to every application without its own"() {
        when:
        open('/admin/qa')

        then:
        assertThat(page.locator('h1')).hasText('OneView Admin')
        assertThat(bbh().locator('.summary')).hasText('Not changed yet: the targets OneView starts with')
        hasValues(bbh(), [(BBH_TARGETS[0]): '80', (BBH_TARGETS[1]): '95', (BBH_TARGETS[2]): '0'])
        assertThat(buttonIn(bbh(), 'Save BBH targets')).isDisabled()

        when:
        input(bbh(), 'Test pass rate (%)').fill('')
        buttonIn(bbh(), 'Save BBH targets').click()

        then:
        assertThat(errorOf(bbh(), 'Test pass rate (%)')).hasText('Required')
        api.requests('PUT', '/api/qa/defaults').isEmpty()

        when:
        input(bbh(), 'Test pass rate (%)').fill('90')
        input(bbh(), 'Code coverage (%)').fill('75')
        buttonIn(bbh(), 'Save BBH targets').click()

        then:
        assertThat(snackBar()).containsText('BBH quality targets saved. Every application without targets of its own works to them now.')
        assertThat(bbh().locator('.summary')).hasText('Last saved just now')
        awaitRequest('PUT', '/api/qa/defaults').json() == [targets: [coveragePercent: 75, passRatePercent: 90, maxCriticalDefects: 0], version: null]
        assertThat(buttonIn(bbh(), 'Save BBH targets')).isDisabled()

        when:
        input(bbh(), 'Open critical defects (at most)').fill('2')
        sideLink('QM Metric Tool').click()

        then: 'leaving with a change asks first'
        assertThat(dialog()).containsText('Discard your changes?')

        when:
        dialogButton('Discard').click()
        page.waitForURL('**/qa')

        then:
        assertThat(page.locator('.bbh-targets')).hasText('BBH targets: Code coverage at least 75%, test pass rate at least 90%, open critical defects at most 0. An application can set its own in OneView Admin.')
        api.requests('PUT', '/api/qa/defaults').size() == 1
        ownErrors().isEmpty()
    }

    def "an administrator sets up an application with its QA lead, its dashboard and a target of its own"() {
        when:
        open('/admin/qa')

        then:
        assertThat(applications().locator('.summary')).hasText('BBH · 1 of 2 applications take part')
        assertThat(gridHeaders(applications())).hasText(['Application', 'Business service', 'QM Metric Tool', 'QA lead', 'Targets', 'Last changed', ''] as String[])
        assertThat(gridCells(applications(), 'actions')).hasText(['Edit', 'Set up'] as String[])

        when:
        button('Set up Payments Hub', true).click()

        then:
        assertThat(dialog().locator('h2')).hasText('Set up Payments Hub in the QM Metric Tool')
        assertThat(checkbox(dialog(), 'Takes part in the QM Metric Tool')).isChecked()
        assertThat(hintOf(dialog(), 'Code coverage (%)')).containsText('BBH target: at least 80%')

        when:
        input(dialog(), 'Grafana dashboard').fill('grafana/pay')
        dialogButton('Set up').click()

        then:
        assertThat(errorOf(dialog(), 'Grafana dashboard')).isVisible()
        api.requests('PUT', '/api/qa/applications/2').isEmpty()

        when:
        fillIn(dialog(), ['QA lead'               : 'Eve Ray', 'SonarQube project key': 'com.bbh:payments-hub',
                          'Defect project key'    : 'PAY', 'Grafana dashboard': 'https://grafana.bbh.com/d/pay-quality',
                          'Code coverage (%)'     : '70'])
        dialogButton('Set up').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(snackBar()).containsText('Payments Hub saved. It takes part in the QM Metric Tool.')
        with(awaitRequest('PUT', '/api/qa/applications/2').json() as Map) {
            enabled == true
            qaLead == 'Eve Ray'
            sonarProjectKey == 'com.bbh:payments-hub'
            defectProjectKey == 'PAY'
            dashboardUrl == 'https://grafana.bbh.com/d/pay-quality'
            targets == [coveragePercent: 70, passRatePercent: null, maxCriticalDefects: null]
            version == null
        }
        assertThat(applications().locator('.summary')).hasText('BBH · 2 of 2 applications take part')
        assertThat(gridCell(gridRow(applications(), 'Payments Hub'), 'qaLead')).hasText('Eve Ray')
        assertThat(gridCell(gridRow(applications(), 'Payments Hub'), 'actions')).hasText('Edit')

        when:
        open('/qa')
        chooseApplication('Payments Hub')

        then:
        assertThat(page.locator('.profile .participation')).hasText('Takes part')
        assertThat(page.locator('.not-taking-part')).hasCount(0)
        assertThat(stat('Code coverage · own target')).hasText('at least 70%')
        assertThat(stat('Test pass rate · BBH target')).hasText('at least 95%')
        ownErrors().isEmpty()
    }

    def "someone else saving the BBH targets first is said, and Reload shows their targets"() {
        given:
        open('/admin/qa')
        def theirs = [targets: [coveragePercent: 85, passRatePercent: 97, maxCriticalDefects: 0], version: 4, updatedAt: '2026-11-22T09:00:00Z']

        when:
        api.on('PUT', '/api/qa/defaults') { problem(409, 'Conflict', 'The BBH quality targets were changed by someone else') }
        api.get('/api/qa/defaults') { theirs }
        input(bbh(), 'Code coverage (%)').fill('70')
        buttonIn(bbh(), 'Save BBH targets').click()

        then:
        assertThat(bbh().locator('.conflict .banner-text')).containsText('Someone else saved the BBH targets after you opened this page.')

        when:
        buttonIn(bbh(), 'Reload').click()

        then:
        assertThat(bbh().locator('.conflict')).hasCount(0)
        assertThat(input(bbh(), 'Code coverage (%)')).hasValue('85')
        assertThat(input(bbh(), 'Test pass rate (%)')).hasValue('97')
        ownErrors().findAll { !it.contains('409') }.isEmpty()
    }

    Locator bbh() {
        page.locator('.bbh')
    }

    Locator applications() {
        page.locator('section.applications')
    }

    List<String> facts() {
        page.locator('.profile dt').all().collect { term ->
            "${term.textContent().trim()}: ${term.locator('xpath=following-sibling::dd[1]').textContent().trim()}".toString()
        }.findAll { !it.startsWith('Last changed') }
    }
}
