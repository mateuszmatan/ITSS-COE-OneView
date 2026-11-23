package com.bbh.itss.coe.oneview.frontend.regression

import com.bbh.itss.coe.oneview.frontend.support.OvSpecification
import com.microsoft.playwright.Locator

import java.time.LocalDate
import java.time.ZoneOffset

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class AgileSpec extends OvSpecification {

    static final Map<String, String> LEVELS = ['Team and roles'       : '3', 'Planning and delivery': '3', 'Engineering practices': '4',
                                               'Product ownership'    : '2', 'Continuous improvement': '3']

    def "Agile Assessment & Adoption sums up the teams of BBH and shows one team with how it changed"() {
        when:
        open('/agile')

        then:
        assertThat(page.locator('h1')).hasText('Agile Assessment & Adoption')
        assertThat(page.locator('.page-header .page-description')).hasText('How mature the agile way of working of every application team and business service is, dimension by dimension, and how it changes from one assessment to the next.')
        assertThat(page.locator('.summary .card-header .muted')).hasText('BBH')
        assertThat(stat('Applications')).hasText('2')
        assertThat(stat('Teams taking part')).hasText('1')
        assertThat(stat('Assessed')).hasText('1')
        assertThat(stat('Average level, of 5')).hasText('3.2')
        assertThat(gridHeaders()).hasText(['Application', 'Business service', 'Agile', 'Team', 'Way of working', 'Agile coach',
                                           'Last assessed', 'Level'] as String[])
        assertThat(column('name')).hasText(['CertScanner CERTSCANNER', 'Payments Hub PAYHUB'] as String[])
        assertThat(column('framework')).hasText(['Scrum', 'Not set'] as String[])
        assertThat(column('assessedOn')).hasText(['2 Nov 2026', '–'] as String[])
        assertThat(column('overall')).hasText(['3.2', '–'] as String[])
        levels('.dimensions') == ['Team and roles 4.0', 'Planning and delivery 3.0', 'Engineering practices 3.0',
                                  'Product ownership 2.0', 'Continuous improvement 4.0']
        levels('.departments') == ['Corporate Technology 3.2']

        when:
        button('Show CertScanner alone', true).click()

        then:
        assertThat(page.locator('.profile h2')).hasText('CertScanner CERTSCANNER')
        assertThat(page.locator('.profile .participation')).hasText('Takes part')
        assertThat(page.locator('.latest .card-header .muted')).hasText('Assessed 2 Nov 2026 by Joe Coach')
        assertThat(page.locator('.overall')).hasText('3.2 overall level, of 5')
        levels('.latest') == ['Team and roles 4', 'Planning and delivery 3', 'Engineering practices 3', 'Product ownership 2',
                              'Continuous improvement 4']
        assertThat(page.locator('.latest .change')).hasText(['up 1 from 3', 'up 1 from 2', 'up 1 from 2', 'up 1 from 3'] as String[])
        assertThat(page.locator('.notes')).hasText('Notes: Retrospectives every sprint.')
        assertThat(gridCells(page.locator('.history'), 'overall')).hasText(['3.2', '2.4'] as String[])
        assertThat(page.locator('.summary')).hasCount(0)
        ownErrors().isEmpty()
    }

    def "an administrator sets up the team of an application, records its first assessment and deletes it"() {
        given:
        def today = LocalDate.now(ZoneOffset.UTC)

        when:
        open('/admin/agile')

        then:
        assertThat(teams().locator('.summary')).hasText('BBH · 1 of 2 teams take part')
        assertThat(button('Record assessment of Payments Hub', true)).isDisabled()
        assertThat(page.locator('#assess-reason-2')).hasText('Set up the team of Payments Hub first: only a team that takes part is assessed.')
        assertThat(assessments().locator('.summary')).hasText('2 recorded, the latest first')

        when:
        button('Set up the team of Payments Hub', true).click()

        then:
        assertThat(dialog().locator('h2')).hasText('Set up the team of Payments Hub')
        assertThat(checkbox(dialog(), 'Takes part in Agile Assessment & Adoption')).isChecked()
        optionsOf(dialog(), 'Way of working') == ['Not set', 'Scrum', 'Kanban', 'SAFe', 'Other']

        when:
        fillIn(dialog(), [Team: 'Pay Squad', 'Agile coach': 'Ann Coach'])
        choose(dialog(), 'Way of working', 'Kanban')
        dialogButton('Set up').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(snackBar()).containsText('The team of Payments Hub saved. It takes part in Agile Assessment & Adoption.')
        awaitRequest('PUT', '/api/agile/applications/2').json() ==
                [enabled: true, teamName: 'Pay Squad', framework: 'KANBAN', coach: 'Ann Coach', version: null]
        assertThat(teams().locator('.summary')).hasText('BBH · 2 of 2 teams take part')
        assertThat(button('Edit the team of Payments Hub', true)).isVisible()
        assertThat(button('Record assessment of Payments Hub', true)).isEnabled()

        when:
        button('Record assessment of Payments Hub', true).click()

        then:
        assertThat(dialog().locator('h2')).hasText('Record an assessment of Payments Hub')
        hasValues(dialog(), ['Day of the assessment': today.toString(), 'Assessed by': 'Ann Coach'])

        when: 'no level is chosen and the day is in the future'
        input(dialog(), 'Day of the assessment').fill(today.plusDays(1).toString())
        dialogButton('Record assessment').click()

        then:
        assertThat(errorOf(dialog(), 'Day of the assessment')).containsText('Not in the future')
        LEVELS.keySet().every { label ->
            assertThat(errorOf(dialog(), label)).hasText('Required')
            true
        }
        api.requests('POST', '/api/agile/applications/2/assessments').isEmpty()

        when:
        input(dialog(), 'Day of the assessment').fill('2026-11-20')
        LEVELS.each { label, level -> choose(dialog(), label, level) }
        input(dialog(), 'Notes').fill('First assessment of the new team.')
        dialogButton('Record assessment').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(snackBar()).containsText('Assessment of Payments Hub of 20 Nov 2026 recorded: level 3.0.')
        awaitRequest('POST', '/api/agile/applications/2/assessments').json() ==
                [assessedOn: '2026-11-20', assessor: 'Ann Coach', notes: 'First assessment of the new team.',
                 levels    : [TEAM: 3, PLANNING: 3, ENGINEERING: 4, PRODUCT: 2, IMPROVEMENT: 3]]
        assertThat(assessments().locator('.summary')).hasText('3 recorded, the latest first')
        assertThat(gridCells(assessments(), 'application')).hasText(['Payments Hub', 'CertScanner', 'CertScanner'] as String[])
        assertThat(gridCells(assessments(), 'overall')).hasText(['3.0', '3.2', '2.4'] as String[])
        assertThat(gridCell(gridRow(teams(), 'Payments Hub'), 'assessedOn')).hasText('20 Nov 2026')

        when:
        button('Delete the assessment of Payments Hub of 20 Nov 2026', true).click()

        then:
        assertThat(dialog()).containsText('Delete the assessment of Payments Hub of 20 Nov 2026?')
        assertThat(dialog()).containsText('The levels and notes Ann Coach recorded on 20 Nov 2026 are deleted.')

        when:
        dialogButton('Delete assessment').click()

        then:
        assertThat(snackBar()).containsText('Assessment of Payments Hub of 20 Nov 2026 deleted')
        awaitRequest('DELETE', '/api/agile/assessments/\\d+').path == '/api/agile/assessments/100'
        assertThat(assessments().locator('.summary')).hasText('2 recorded, the latest first')
        assertThat(gridCell(gridRow(teams(), 'Payments Hub'), 'assessedOn')).hasText('–')

        when:
        open('/agile')

        then:
        assertThat(stat('Teams taking part')).hasText('2')
        assertThat(stat('Assessed')).hasText('1')
        assertThat(column('team')).hasText(['Cert Squad', 'Pay Squad'] as String[])
        assertThat(column('framework')).hasText(['Scrum', 'Kanban'] as String[])
        ownErrors().isEmpty()
    }

    def "the teams of one business service are administered without the others"() {
        given:
        page.addInitScript("localStorage.setItem('oneview.department', '3')")

        when:
        open('/admin/agile')

        then:
        assertThat(teams().locator('.summary')).hasText('Corporate Technology · 1 of 1 team takes part')
        assertThat(gridHeaders(teams())).hasText(['Application', 'Agile', 'Team', 'Way of working', 'Agile coach', 'Last assessed', ''] as String[])
        assertThat(gridCells(teams(), 'name')).hasText(['CertScanner CERTSCANNER'] as String[])
        assertThat(gridCells(assessments(), 'application')).hasText(['CertScanner', 'CertScanner'] as String[])

        when:
        chooseBusinessService('Custody')

        then:
        assertThat(teams().locator('.empty-state h3')).hasText('No applications in Custody yet')
        assertThat(assessments()).hasCount(0)
        ownErrors().isEmpty()
    }

    Locator column(String name) {
        gridCells(page.locator('body'), name)
    }

    Locator teams() {
        page.locator('section.teams')
    }

    Locator assessments() {
        page.locator('section.assessments')
    }

    List<String> levels(String section) {
        page.locator("${section} .dimension").first().waitFor()
        page.locator("${section} .dimension").all().collect { entry ->
            "${entry.locator('.dimension-label strong').textContent().trim()} ${entry.locator('.value').textContent().trim()}".toString()
        }
    }
}
