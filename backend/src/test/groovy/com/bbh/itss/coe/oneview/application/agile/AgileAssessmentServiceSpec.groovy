package com.bbh.itss.coe.oneview.application.agile

import com.bbh.itss.coe.oneview.application.agile.port.in.AgileApplicationView
import com.bbh.itss.coe.oneview.application.agile.port.out.AgileRepositoryPort
import com.bbh.itss.coe.oneview.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.coe.oneview.application.catalog.port.out.ProductSummary
import com.bbh.itss.coe.oneview.domain.agile.AgileAssessment
import com.bbh.itss.coe.oneview.domain.agile.AgileDimension
import com.bbh.itss.coe.oneview.domain.agile.AgileProfile
import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException
import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException.FieldProblem
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

import static com.bbh.itss.coe.oneview.domain.agile.AgileFramework.KANBAN
import static com.bbh.itss.coe.oneview.domain.agile.AgileFramework.SCRUM
import static com.bbh.itss.coe.oneview.domain.shared.Failures.STALE_VERSION
import static java.time.ZoneOffset.UTC

class AgileAssessmentServiceSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-11-23T22:30:00Z')
    static final LocalDate TODAY = LocalDate.parse('2026-11-23')
    static final ProductSummary LEDGER = product(1L, 'LEDGER', 'Ledger', 3L, 'Custody')
    static final ProductSummary FUNDS = product(3L, 'FUND', 'Fund Admin', 5L, 'AI Lab')
    static final Map<AgileDimension, Integer> LEVELS = AgileDimension.values().collectEntries { [(it): 3] }

    AgileRepositoryPort repository = Mock()
    ProductRepositoryPort products = Stub() {
        summaries() >> [LEDGER, FUNDS]
    }
    def service = new AgileAssessmentService(repository, products, Clock.fixed(NOW, UTC))

    def "every application is listed by business service and name with its profile and its assessments"() {
        given:
        def profile = new AgileProfile(1L, true, 'Ledger Squad', SCRUM, 'Ewa', 0L, NOW)
        def latest = new AgileAssessment(12L, 1L, TODAY, 'Ewa', LEVELS, null)
        def earlier = new AgileAssessment(10L, 1L, TODAY.minusDays(90), 'Ewa', LEVELS, 'Kick-off')
        repository.findProfiles() >> [profile]
        repository.findAssessments() >> [latest, earlier]

        expect:
        service.applications() == [
                new AgileApplicationView(3L, 'FUND', 'Fund Admin', 5L, 'AI Lab', AgileProfile.none(3L), []),
                new AgileApplicationView(1L, 'LEDGER', 'Ledger', 3L, 'Custody', profile, [latest, earlier])]
    }

    def "one application is shown with its assessments, or as never set up"() {
        given:
        def assessment = new AgileAssessment(12L, 1L, TODAY, 'Ewa', LEVELS, null)
        repository.loadProfile(1L) >> Optional.empty()
        repository.findAssessments(1L) >> [assessment]

        expect:
        service.application(1L) == new AgileApplicationView(1L, 'LEDGER', 'Ledger', 3L, 'Custody',
                AgileProfile.none(1L), [assessment])
    }

    def "a profile is saved at the version it was read at, and a stale change is not saved"() {
        given:
        repository.loadProfile(1L) >> Optional.of(new AgileProfile(1L, false, null, null, null, 4L, NOW))
        repository.findAssessments(1L) >> []
        def changed = new AgileProfile(0L, true, 'Ledger Squad', KANBAN, null, null, null)

        when:
        def view = service.changeProfile(1L, 4L, changed)

        then:
        1 * repository.saveProfile(new AgileProfile(1L, true, 'Ledger Squad', KANBAN, null, 4L, NOW)) >>
                new AgileProfile(1L, true, 'Ledger Squad', KANBAN, null, 5L, NOW)
        view.profile().version() == 5L

        when:
        service.changeProfile(1L, 3L, changed)

        then:
        def e = thrown(IllegalStateException)
        e.message == STALE_VERSION
        0 * repository.saveProfile(_)
    }

    def "an assessment is recorded for today at the latest, by the clock of the portal"() {
        when:
        def recorded = service.recordAssessment(1L, TODAY, ' Ewa ', LEVELS, 'Quarterly')

        then:
        1 * repository.saveAssessment(new AgileAssessment(null, 1L, TODAY, 'Ewa', LEVELS, 'Quarterly')) >>
                new AgileAssessment(15L, 1L, TODAY, 'Ewa', LEVELS, 'Quarterly')
        recorded.id() == 15L

        when:
        service.recordAssessment(1L, TODAY.plusDays(1), 'Ewa', LEVELS, null)

        then:
        def e = thrown(InvalidRequestException)
        e.problems() == [new FieldProblem('assessedOn', 'must not be in the future')]
        0 * repository.saveAssessment(_)
    }

    def "an assessment is found by its id and deleted"() {
        given:
        def assessment = new AgileAssessment(12L, 1L, TODAY, 'Ewa', LEVELS, null)
        repository.loadAssessment(12L) >> Optional.of(assessment)

        expect:
        service.assessment(12L) == assessment

        when:
        service.deleteAssessment(12L)

        then:
        1 * repository.deleteAssessment(12L)
    }

    def "#what that does not exist is not found and nothing is saved or deleted"() {
        given:
        repository.loadAssessment(_) >> Optional.empty()

        when:
        action(service)

        then:
        def e = thrown(NoSuchElementException)
        e.message == message
        0 * repository.saveProfile(_)
        0 * repository.saveAssessment(_)
        0 * repository.deleteAssessment(_)

        where:
        what                    | action                                                             || message
        'an application'        | { it.application(99L) }                                            || 'Product 99 does not exist'
        'a profiled application' | { it.changeProfile(99L, null, AgileProfile.none(99L)) }           || 'Product 99 does not exist'
        'an assessed application' | { it.recordAssessment(99L, TODAY, 'Ewa', LEVELS, null) }        || 'Product 99 does not exist'
        'an assessment'         | { it.assessment(77L) }                                             || 'Assessment 77 does not exist'
        'a deleted assessment'  | { it.deleteAssessment(77L) }                                       || 'Assessment 77 does not exist'
    }

    private static ProductSummary product(long id, String code, String name, Long departmentId, String departmentName) {
        ProductSummary.builder().id(id).code(code).name(name).departmentId(departmentId).departmentName(departmentName)
                .build()
    }
}
