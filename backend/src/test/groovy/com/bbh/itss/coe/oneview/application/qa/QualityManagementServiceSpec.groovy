package com.bbh.itss.coe.oneview.application.qa

import com.bbh.itss.coe.oneview.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.coe.oneview.application.catalog.port.out.ProductSummary
import com.bbh.itss.coe.oneview.application.qa.port.in.QualityApplicationView
import com.bbh.itss.coe.oneview.application.qa.port.out.QualityRepositoryPort
import com.bbh.itss.coe.oneview.domain.qa.QualityDefaults
import com.bbh.itss.coe.oneview.domain.qa.QualityProfile
import com.bbh.itss.coe.oneview.domain.qa.QualityTargets
import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.coe.oneview.domain.shared.Failures.STALE_VERSION

class QualityManagementServiceSpec extends Specification {

    static final Instant SAVED = Instant.parse('2026-11-20T10:00:00Z')
    static final ProductSummary LEDGER = product(1L, 'LEDGER', 'Ledger', 3L, 'Custody')
    static final ProductSummary PAYMENTS = product(2L, 'PAY', 'payments', 3L, 'Custody')
    static final ProductSummary FUNDS = product(3L, 'FUND', 'Fund Admin', 5L, 'AI Lab')
    static final ProductSummary LEGACY = product(4L, 'OLD', 'Archive', null, null)
    static final QualityDefaults STORED_DEFAULTS = new QualityDefaults(new QualityTargets(85, 90, 1), 2L, SAVED)

    QualityRepositoryPort repository = Mock()
    ProductRepositoryPort products = Stub() {
        summaries() >> [LEGACY, PAYMENTS, FUNDS, LEDGER]
    }
    def service = new QualityManagementService(repository, products)

    def "the BBH targets are the initial ones until an administrator saves them"() {
        when:
        def initial = service.defaults()

        then:
        1 * repository.loadDefaults() >> Optional.empty()
        initial == QualityDefaults.INITIAL

        when:
        def stored = service.defaults()

        then:
        1 * repository.loadDefaults() >> Optional.of(STORED_DEFAULTS)
        stored == STORED_DEFAULTS
    }

    def "the BBH targets are saved at the version they were read at, and a stale or invalid change is not saved"() {
        given:
        repository.loadDefaults() >> Optional.of(STORED_DEFAULTS)
        def changed = new QualityTargets(70, 99, 0)

        when:
        def saved = service.changeDefaults(2L, changed)

        then:
        1 * repository.saveDefaults(new QualityDefaults(changed, 2L, SAVED)) >> new QualityDefaults(changed, 3L, SAVED)
        saved.version() == 3L

        when:
        service.changeDefaults(1L, changed)

        then:
        def stale = thrown(IllegalStateException)
        stale.message == STALE_VERSION
        0 * repository.saveDefaults(_)

        when:
        service.changeDefaults(2L, new QualityTargets(null, 99, 0))

        then:
        thrown(InvalidRequestException)
        0 * repository.saveDefaults(_)
    }

    def "every application is listed by business service and name, those never set up with the BBH targets"() {
        given:
        def ledger = new QualityProfile(1L, true, 'Anna', 'bbh:ledger', null, null, new QualityTargets(70, null, null), 0L, SAVED)
        repository.loadDefaults() >> Optional.of(STORED_DEFAULTS)
        repository.findProfiles() >> [ledger]

        when:
        def applications = service.applications()

        then:
        applications*.name == ['Fund Admin', 'Ledger', 'payments', 'Archive']
        applications*.departmentName == ['AI Lab', 'Custody', 'Custody', null]
        applications[1] == new QualityApplicationView(1L, 'LEDGER', 'Ledger', 3L, 'Custody', ledger,
                new QualityTargets(70, 90, 1))
        applications[0] == new QualityApplicationView(3L, 'FUND', 'Fund Admin', 5L, 'AI Lab', QualityProfile.none(3L),
                STORED_DEFAULTS.targets())
    }

    def "one application is shown with its profile, or as never set up"() {
        given:
        repository.loadDefaults() >> Optional.empty()
        repository.loadProfile(1L) >> Optional.of(new QualityProfile(1L, true, null, null, null, null, null, 0L, SAVED))
        repository.loadProfile(2L) >> Optional.empty()

        expect:
        service.application(1L).profile().enabled()
        service.application(1L).effectiveTargets() == QualityTargets.BBH
        service.application(2L) == new QualityApplicationView(2L, 'PAY', 'payments', 3L, 'Custody',
                QualityProfile.none(2L), QualityTargets.BBH)
    }

    def "a profile is saved at the version it was read at and shown with the targets it has"() {
        given:
        repository.loadDefaults() >> Optional.empty()
        repository.loadProfile(2L) >> Optional.empty()
        def changed = new QualityProfile(0L, true, 'Piotr', null, 'PAY', null, new QualityTargets(null, null, 5), null, null)
        def expected = new QualityProfile(2L, true, 'Piotr', null, 'PAY', null, new QualityTargets(null, null, 5), null, null)

        when:
        def view = service.change(2L, null, changed)

        then:
        1 * repository.saveProfile(expected) >> new QualityProfile(2L, true, 'Piotr', null, 'PAY', null,
                new QualityTargets(null, null, 5), 0L, SAVED)
        view.profile().version() == 0L
        view.effectiveTargets() == new QualityTargets(80, 95, 5)
    }

    def "an application that is not in the catalog is not found and nothing is saved"() {
        when:
        action(service)

        then:
        def e = thrown(NoSuchElementException)
        e.message == 'Product 99 does not exist'
        0 * repository.saveProfile(_)

        where:
        action << [{ it.application(99L) }, { it.change(99L, null, QualityProfile.none(99L)) }]
    }

    private static ProductSummary product(long id, String code, String name, Long departmentId, String departmentName) {
        ProductSummary.builder().id(id).code(code).name(name).departmentId(departmentId).departmentName(departmentName)
                .build()
    }
}
