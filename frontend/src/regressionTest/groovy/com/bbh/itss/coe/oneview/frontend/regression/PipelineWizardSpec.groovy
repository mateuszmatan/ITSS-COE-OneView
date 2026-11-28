package com.bbh.itss.coe.oneview.frontend.regression

import com.bbh.itss.coe.oneview.frontend.support.OvSpecification
import com.bbh.itss.coe.oneview.frontend.support.ProductStore
import com.microsoft.playwright.Locator

import static com.bbh.itss.coe.oneview.frontend.support.StubApi.fixture
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class PipelineWizardSpec extends OvSpecification {

    static final String APPLICATION = '7f3c1e52-9a41-4c0b-b1de-2f6a8d3e9c10'
    static final String REPOSITORY = 'https://bitbucket.bbh.com/projects/CERT/repos/certscanner-api'

    def "a pipeline is set up step by step: sources and the existing build are read and OneView creates the Jenkins job"() {
        given:
        ProductStore.created(api, 3)

        when:
        open('/pipelines')
        link('Create a pipeline step by step').click()
        page.waitForURL('**/pipelines/new')

        then:
        assertThat(page.locator('.step-bar .step-label')).hasText(['Basics', 'Sources', 'Existing build', 'Jenkins job', 'Review'] as String[])
        assertThat(currentStep()).hasText('Basics')

        when:
        fillBasics('Custody', 'Trade Archive', 'trade-archive-api', 'Security')
        next()
        input(step(), 'Bitbucket repository').fill(REPOSITORY)
        button('Read the sources', true).click()

        then:
        assertThat(currentStep()).hasText('Sources')
        assertThat(findings('source-findings').locator('h3')).containsText('What OneView found in branch develop')
        assertThat(findings('source-findings')).containsText('pom.xml')
        assertThat(input(step(), 'Jenkins agents')).hasValue('maven-jdk21')
        api.requests('POST', '/api/pipeline-setup/sources').last().json() == [repositoryUrl: REPOSITORY, branch: 'main']

        when:
        fillIn(step(), ['AppScan application ID': APPLICATION, 'OpenShift project': 'trade-archive'])
        next()
        input(step(), 'Existing Jenkins job or build').fill('https://jenkins-old.bbh.com/job/CERT/job/certscanner-api/42/')
        button('Read the build', true).click()

        then:
        assertThat(currentStep()).hasText('Existing build')
        assertThat(findings('job-findings').locator('h3')).containsText('What OneView read from CERT/certscanner-api')
        assertThat(findings('job-findings')).containsText('Read only, nothing was changed')

        when:
        next()

        then:
        assertThat(currentStep()).hasText('Jenkins job')
        assertThat(input(step(), 'Branch')).hasValue('release')
        assertThat(input(step(), 'Jenkinsfile')).hasValue('ci/Jenkinsfile')
        assertThat(input(step(), 'Jenkins credential for the repository')).hasValue('bb-reader')

        when:
        input(step(), 'Jenkins address').fill('https://jenkins.bbh.com')
        next()

        then:
        assertThat(currentStep()).hasText('Review')
        assertThat(step().locator('h2')).hasText('Is everything right?')

        when:
        button('Create the pipeline', true).click()

        then:
        assertThat(step().locator('h2')).hasText('The pipeline is set up')
        assertThat(page.getByTestId('job-created')).containsText('OneView created the Jenkins job')
        assertThat(step().locator('.next-steps')).containsText('Commit this Jenkinsfile to the repository as ci/Jenkinsfile')
        with(api.requests('POST', '/api/pipelines/(\\d+)/jenkins-job').last().json() as Map) {
            jenkinsUrl == 'https://jenkins.bbh.com'
            kind == 'PIPELINE'
            repositoryUrl == PipelineWizardSpec.REPOSITORY
            branch == 'release'
            scriptPath == 'ci/Jenkinsfile'
            credentialsId == 'bb-reader'
            parameters*.name == ['TARGET', 'TOKEN']
        }
        ownErrors().isEmpty()
    }

    def "without the generic user nothing is read for the user and the Jenkins job is left to them"() {
        given:
        ProductStore.created(api, 3)
        api.respond('GET', '/api/jenkins-automation', (fixture('jenkins-automation.json') as Map) +
                [genericUser: null, oisHost: null, oisApiKeySet: false, ready: false,
                 missing    : ['the generic user', 'the OIS host', 'the OIS API key']])

        when:
        open('/pipelines/new')
        fillBasics('Custody', 'Trade Archive', 'trade-archive-api', 'SAST (Static Application Security Tests) - HCL AppScan')
        next()
        input(step(), 'AppScan application ID').fill(APPLICATION)
        next()
        next()

        then:
        assertThat(currentStep()).hasText('Jenkins job')
        assertThat(step().locator('.banner')).containsText('OneView cannot create the job yet')
        assertThat(input(step(), 'Jenkins address')).hasCount(0)

        when:
        next()
        button('Create the pipeline', true).click()

        then:
        assertThat(step().locator('h2')).hasText('The pipeline is set up')
        assertThat(step().locator('.next-steps')).containsText('Create the Jenkins job')
        api.requests('POST', '/api/pipelines/(\\d+)/jenkins-job').isEmpty()
        ownErrors().isEmpty()
    }

    def "the administrator sets the generic user of the Jenkins automation and checks the connection"() {
        when:
        open('/admin/jenkins')

        then:
        assertThat(page.getByTestId('automation-state')).hasText('Ready')
        assertThat(input(page.locator('main'), 'Generic user')).hasValue('svc-oneview')

        when:
        input(page.locator('main'), 'Looks for changes').fill('H/10 * * * *')
        button('Save', true).click()

        then:
        assertThat(snackBar()).containsText('saved')
        api.requests('PUT', '/api/jenkins-automation').last().json().pollSchedule == 'H/10 * * * *'

        when:
        input(page.locator('main'), 'Jenkins address').fill('https://jenkins.bbh.com')
        button('Check the connection', true).click()

        then:
        assertThat(page.getByTestId('check-result')).containsText('Jenkins at https://jenkins.bbh.com accepted svc-oneview')
        ownErrors().isEmpty()
    }

    private void fillBasics(String department, String product, String service, String pipeline) {
        choose(step(), 'Business Service', department)
        fillIn(step(), ['Product name': product, 'Team': 'Custody Technology', 'Service name': service])
        radio(step(), pipeline).click()
    }

    private void next() {
        page.locator('.step-actions .btn-primary').click()
    }

    private Locator findings(String name) {
        page.getByTestId(name)
    }

    private Locator step() {
        page.locator('section.step')
    }

    private Locator currentStep() {
        page.locator('.step-bar .current .step-label')
    }
}
