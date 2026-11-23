package com.bbh.itss.coe.oneview.frontend.regression

import com.bbh.itss.coe.oneview.frontend.support.OvSpecification
import com.microsoft.playwright.Locator

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class ContextBarSpec extends OvSpecification {

    static final List<String> BUSINESS_SERVICES = ['All business services', 'AI Lab', 'Capital Partners', 'Corporate Technology',
                                                   'Custody', 'Fund Services']

    def "the landing page sums up every area for BBH, for the business service chosen and for one application"() {
        when:
        open('/')

        then:
        assertThat(page.locator('.scope-note')).hasText('The figures on the cards are for the whole of BBH. Choose a business service or an application above to see only theirs.')
        assertThat(figures('DevSecOps')).hasText(['9 pipelines', '6 passed', '2 failed or with warnings'] as String[])
        assertThat(figures('QM Metric Tool')).hasText(['1 of 2 applications take part', '1 with a Grafana dashboard'] as String[])
        assertThat(figures('Agile Assessment & Adoption')).hasText(['1 of 2 teams take part', '1 assessed', '3.2 average level, of 5'] as String[])
        optionsOf(contextBar(), 'Business service') == BUSINESS_SERVICES
        optionsOf(contextBar(), 'Application') == ['All applications', 'CertScanner', 'Payments Hub']
        contextBar().locator('optgroup').evaluateAll('groups => groups.map((group) => group.label)') == ['Corporate Technology', 'Fund Services']
        assertThat(summary()).hasText('Showing all of BBH: 2 applications in 5 business services')

        when:
        chooseBusinessService('Fund Services')

        then:
        assertThat(page.locator('.scope-note')).hasText('The figures on the cards are for Fund Services. Choose All business services above to see the whole of BBH.')
        assertThat(figures('DevSecOps')).hasText(['6 pipelines', '3 passed', '2 failed or with warnings'] as String[])
        assertThat(figures('QM Metric Tool')).hasText(['0 of 1 application takes part', '0 with a Grafana dashboard'] as String[])
        assertThat(figures('Agile Assessment & Adoption')).hasText(['0 of 1 team takes part', '0 assessed', '– average level, of 5'] as String[])
        optionsOf(contextBar(), 'Application') == ['All applications of Fund Services', 'Payments Hub']
        assertThat(summary()).hasText('Showing Fund Services: 1 application')

        when:
        chooseApplication('Payments Hub')

        then:
        assertThat(page.locator('.scope-note')).hasText('The figures on the cards are for Payments Hub. Choose All business services above to see the whole of BBH.')
        assertThat(figures('QM Metric Tool')).hasText(['Not set up in the QM Metric Tool', 'at least 80% code coverage', 'at least 95% test pass rate'] as String[])
        assertThat(figures('Agile Assessment & Adoption')).hasText(['Not set up in Agile Assessment & Adoption', 'Not assessed yet'] as String[])
        assertThat(summary()).hasText('Showing Payments Hub (PAYHUB) of Fund Services')
        ownErrors().isEmpty()
    }

    def "the business service and the application chosen once stay chosen on every page and after a reload"() {
        given:
        open('/')
        chooseApplication('Payments Hub')

        when:
        sideLink('QM Metric Tool').click()
        page.waitForURL('**/qa')

        then:
        assertThat(page.locator('.profile h2')).hasText('Payments Hub PAYHUB')
        assertThat(page.locator('.not-taking-part .banner-text')).hasText('Payments Hub does not take part in the QM Metric Tool yet. An administrator sets it up in OneView Admin with its QA lead, where its results are and its own quality targets.')
        assertThat(selected(contextBar(), 'Business service')).hasText('Fund Services')
        assertThat(selected(contextBar(), 'Application')).hasText('Payments Hub')

        when:
        sideLink('Agile Assessment & Adoption').click()
        page.waitForURL('**/agile')

        then:
        assertThat(page.locator('.not-taking-part .banner-text')).containsText('The team of Payments Hub does not take part in Agile Assessment & Adoption yet.')
        assertThat(page.locator('.latest .empty-state h3')).hasText('No assessment of Payments Hub yet')

        when:
        sideLink('DevSecOps').click()
        page.waitForURL('**/monitoring')

        then:
        assertThat(page.locator('.results .card-header .muted')).hasText('Payments Hub')
        assertThat(page.locator('.products .card-header .muted')).hasText('1 product')
        awaitRequest('GET', '/api/monitoring/activity').params().subMap(['departmentId', 'productId']) == [departmentId: '5', productId: '2']

        when:
        page.reload()

        then:
        assertThat(selected(contextBar(), 'Application')).hasText('Payments Hub')
        assertThat(page.locator('.results .card-header .muted')).hasText('Payments Hub')

        when:
        chooseBusinessService('All business services')

        then:
        assertThat(selected(contextBar(), 'Application')).hasText('All applications')
        assertThat(page.locator('.results .card-header .muted')).hasCount(0)
        assertThat(page.locator('.products .card-header .muted')).hasText('2 products')
        page.evaluate("() => [localStorage.getItem('oneview.department'), localStorage.getItem('oneview.application')]") == [null, null]
        ownErrors().isEmpty()
    }

    def "OWASP SAMM names the business service or the application chosen, and a link opens a scorecard outside the choice"() {
        when:
        open('/samm')

        then:
        assertThat(page.locator('.score-label')).hasText('BBH Organization Maturity')
        assertThat(page.locator('.section-subtitle').first()).hasText('Average score across the 1 BBH application')
        assertThat(page.locator(".stat-label:text-is('Business Services Covered')")).hasCount(1)

        when:
        chooseBusinessService('Corporate Technology')

        then:
        assertThat(page.locator('.score-label')).hasText('Corporate Technology Maturity')
        assertThat(page.locator('.section-subtitle').first()).hasText('Average score across 1 application of Corporate Technology')
        assertThat(page.locator(".stat-label:text-is('Business Services Covered')")).hasCount(0)

        when:
        chooseApplication('CertScanner')

        then:
        assertThat(page.locator('.section-subtitle').first()).hasText('Average score across 1 pipeline of CertScanner')

        when: 'the link of Pipeline Monitoring names a pipeline of another business service'
        chooseBusinessService('Fund Services')
        open('/samm?pipelineId=1')

        then:
        assertThat(page.locator('.scorecard-modal')).containsText('CertScanner / gui')
        awaitRequest('GET', '/api/samm/pipelines/1')
        ownErrors().isEmpty()
    }

    def "the bar stays away from the pages of one pipeline, Self-service and the settings"() {
        expect:
        ['/pipelines/1', '/self-service', '/admin/template', '/admin/settings', '/admin/departments'].every { String path ->
            open(path)
            assertThat(page.locator('h1').first()).isVisible()
            assertThat(contextBar()).hasCount(0)
            true
        }

        and:
        ['/', '/pipelines', '/monitoring', '/samm', '/qa', '/agile', '/history', '/admin/products', '/admin/qa', '/admin/agile'].every { String path ->
            open(path)
            assertThat(contextBar()).isVisible()
            true
        }
        ownErrors().isEmpty()
    }

    Locator figures(String area) {
        holding(page.locator('a.area-card'), "h2:text-is('${area}')").locator('.area-figures li')
    }

    Locator summary() {
        contextBar().locator('.context-summary')
    }
}
