package com.bbh.itss.coe.oneview.frontend.regression

import com.bbh.itss.coe.oneview.frontend.support.OvSpecification
import com.microsoft.playwright.Locator

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class AdminTabsSpec extends OvSpecification {

    static final Map<String, String> TABS = ['Business Services'       : '/admin/departments', Products: '/admin/products',
                                             'Service template'        : '/admin/template', 'Library defaults': '/admin/settings',
                                             'Applications and targets': '/admin/qa', 'Teams and assessments': '/admin/agile']

    static final List<String> GROUPS = ['Catalog', 'DevSecOps', 'QM Metric Tool', 'Agile Assessment & Adoption']

    def "the Admin page shows its heading, its description and the tabs of every area with the open one marked"() {
        when:
        open('/admin/departments')

        then:
        assertThat(page.locator('h1')).hasText('OneView Admin')
        assertThat(page.locator('.page-header .page-description'))
                .hasText('Set up OneView for everyone: business services and their applications, what a new DevSecOps service gets and the settings every pipeline shares, the quality targets of the QM Metric Tool and the agile teams and their assessments.')
        assertThat(tabs()).hasText(TABS.keySet() as String[])
        TABS.every { label, path ->
            assertThat(tab(label)).hasAttribute('href', path)
            true
        }
        assertThat(page.locator('nav.tab-bar .tab-group-label')).hasText(GROUPS as String[])
        assertThat(page.locator('nav.tab-bar [role=group]')).hasCount(GROUPS.size())
        assertThat(page.locator('nav.tab-bar')).hasAttribute('aria-label', 'OneView Admin')
        isOpen('Business Services')
        assertThat(page).hasTitle('Business Services · OneView Admin · BBH ITSS COE OneView')
        assertThat(page.locator('nav.sidebar a.active')).hasText('Admin')
        assertThat(menuLinks()).hasCount(0)
        ownErrors().isEmpty()
    }

    def "the tabs switch between the departments, the products and the Library defaults, and Back returns"() {
        given:
        open('/admin/products')

        when:
        tab('Business Services').click()
        page.waitForURL('**/admin/departments')

        then:
        isOpen('Business Services')
        assertThat(gridRows().locator('.name').first()).hasText('AI Lab')
        assertThat(page).hasTitle(~'^Business Services · OneView Admin')

        when:
        tab('Library defaults').click()
        page.waitForURL('**/admin/settings')

        then:
        isOpen('Library defaults')
        assertThat(field('Jenkins URL')).hasValue('https://jenkins.bbh.com')
        assertThat(page).hasTitle(~'^Library defaults · OneView Admin')

        when:
        tab('Products').click()
        page.waitForURL('**/admin/products')

        then:
        isOpen('Products')
        assertThat(page.locator('section.department h2').first()).hasText('AI Lab')

        when:
        page.goBack()
        page.waitForURL('**/admin/settings')

        then:
        isOpen('Library defaults')
        assertThat(page.locator('h1')).hasText('OneView Admin')
        ownErrors().isEmpty()
    }

    def "the tabs of the QM Metric Tool and of Agile Assessment & Adoption open their administration"() {
        given:
        open('/admin/template')

        when:
        tab('Applications and targets').click()
        page.waitForURL('**/admin/qa')

        then:
        isOpen('Applications and targets')
        assertThat(page).hasTitle('QM Metric Tool · OneView Admin · BBH ITSS COE OneView')
        assertThat(page.locator('.bbh h2')).hasText('BBH quality targets')
        assertThat(contextBar()).isVisible()

        when:
        tab('Teams and assessments').click()
        page.waitForURL('**/admin/agile')

        then:
        isOpen('Teams and assessments')
        assertThat(page).hasTitle('Agile Assessment & Adoption · OneView Admin · BBH ITSS COE OneView')
        assertThat(page.locator('.teams h2')).hasText('Application teams')

        when:
        tab('Service template').click()
        page.waitForURL('**/admin/template')

        then:
        isOpen('Service template')
        assertThat(contextBar()).hasCount(0)
        ownErrors().isEmpty()
    }

    Locator tabs() {
        page.locator('nav.tab-bar a')
    }

    void isOpen(String label) {
        assertThat(page.locator('nav.tab-bar a.active')).hasText(label)
        assertThat(tab(label)).hasAttribute('aria-current', 'page')
        assertThat(page.locator('nav.tab-bar a[aria-current]')).hasCount(1)
    }
}
