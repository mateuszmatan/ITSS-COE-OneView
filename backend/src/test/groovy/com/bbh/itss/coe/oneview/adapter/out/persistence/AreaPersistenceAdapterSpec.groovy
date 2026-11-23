package com.bbh.itss.coe.oneview.adapter.out.persistence

import com.bbh.itss.coe.oneview.domain.agile.AgileAssessment
import com.bbh.itss.coe.oneview.domain.agile.AgileDimension
import com.bbh.itss.coe.oneview.domain.agile.AgileProfile
import com.bbh.itss.coe.oneview.domain.catalog.Product
import com.bbh.itss.coe.oneview.domain.catalog.ServiceDraft
import com.bbh.itss.coe.oneview.domain.qa.QualityDefaults
import com.bbh.itss.coe.oneview.domain.qa.QualityProfile
import com.bbh.itss.coe.oneview.domain.qa.QualityTargets
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification

import java.time.LocalDate

import static com.bbh.itss.coe.oneview.domain.agile.AgileDimension.TEAM
import static com.bbh.itss.coe.oneview.domain.agile.AgileFramework.SAFE
import static com.bbh.itss.coe.oneview.domain.shared.Failures.STALE_VERSION
import static com.bbh.itss.coe.oneview.support.CatalogFixtures.details
import static com.bbh.itss.coe.oneview.support.Fixtures.settings
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE

/** The tables of the areas QM Metric Tool (QA_) and Agile Assessment &amp; Adoption (AGL_). */
@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:area-adapter;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = NONE)
@Import([DepartmentPersistenceAdapter, ProductPersistenceAdapter, QualityPersistenceAdapter, AgilePersistenceAdapter])
class AreaPersistenceAdapterSpec extends Specification {

    static final LocalDate TODAY = LocalDate.parse('2026-11-23')
    static final Map<AgileDimension, Integer> LEVELS = AgileDimension.values().collectEntries { [(it): 2] }

    @Autowired
    ProductPersistenceAdapter products

    @Autowired
    QualityPersistenceAdapter quality

    @Autowired
    AgilePersistenceAdapter agile

    @Autowired
    TestEntityManager entities

    @Autowired
    JdbcTemplate jdbc

    long productId

    def setup() {
        productId = products.save(Product.create(details(code: 'LEDGER', name: 'Ledger'),
                [new ServiceDraft(null, 'api', null, settings())], products)).id()
    }

    def "the BBH targets are saved in the one row of their table and changed at the version they were read at"() {
        expect:
        quality.loadDefaults() == Optional.empty()

        when:
        def saved = quality.saveDefaults(QualityDefaults.INITIAL.change(null, new QualityTargets(75, 90, 2)))
        entities.clear()

        then:
        saved.version() == 0L
        saved.updatedAt() != null
        quality.loadDefaults().get() == saved
        jdbc.queryForMap('SELECT ID, COVERAGE_TARGET, PASS_RATE_TARGET, MAX_CRITICAL_DEFECTS FROM QA_SETTINGS') ==
                [ID: 1, COVERAGE_TARGET: 75, PASS_RATE_TARGET: 90, MAX_CRITICAL_DEFECTS: 2]

        when:
        def changed = quality.saveDefaults(saved.change(0L, QualityTargets.BBH))
        entities.clear()

        then:
        changed.version() == 1L
        quality.loadDefaults().get().targets() == QualityTargets.BBH

        when:
        quality.saveDefaults(new QualityDefaults(QualityTargets.BBH, 0L, saved.updatedAt()))

        then:
        def e = thrown(IllegalStateException)
        e.message == STALE_VERSION
    }

    def "an application's QM profile is stored under its product, read back unchanged and changed as the next version"() {
        given:
        def profile = QualityProfile.none(productId).change(null, new QualityProfile(productId, true, 'Anna',
                'bbh:ledger', 'LEDGER', 'https://grafana.bbh.com/d/qa', new QualityTargets(70, null, 1), null, null))

        expect:
        quality.loadProfile(productId) == Optional.empty()

        when:
        def saved = quality.saveProfile(profile)
        entities.clear()

        then:
        saved.version() == 0L
        quality.loadProfile(productId).get() == saved
        quality.findProfiles() == [saved]
        saved.targets() == new QualityTargets(70, null, 1)
        jdbc.queryForMap('SELECT ENABLED, QA_LEAD, PASS_RATE_TARGET FROM QA_APPLICATION WHERE PRODUCT_ID = ?', productId) ==
                [ENABLED: 1, QA_LEAD: 'Anna', PASS_RATE_TARGET: null]

        when:
        def changed = quality.saveProfile(saved.change(0L, QualityProfile.none(productId)))
        entities.clear()

        then:
        changed.version() == 1L
        quality.loadProfile(productId).get() == changed
        !changed.enabled()
        changed.targets() == QualityTargets.NONE

        when:
        quality.saveProfile(saved)

        then:
        def e = thrown(IllegalStateException)
        e.message == STALE_VERSION
    }

    def "an application's agile profile is stored under its product and a stale one is not written"() {
        given:
        def profile = AgileProfile.none(productId).change(null,
                new AgileProfile(productId, true, 'Ledger Squad', SAFE, 'Ewa', null, null))

        when:
        def saved = agile.saveProfile(profile)
        entities.clear()

        then:
        saved.version() == 0L
        agile.loadProfile(productId).get() == saved
        agile.findProfiles() == [saved]
        jdbc.queryForObject('SELECT FRAMEWORK FROM AGL_APPLICATION WHERE PRODUCT_ID = ?', String, productId) == 'SAFE'

        when:
        agile.saveProfile(AgileProfile.none(productId))

        then:
        def e = thrown(IllegalStateException)
        e.message == STALE_VERSION
    }

    def "assessments are stored with a level per dimension, listed latest first and deleted with their levels"() {
        given:
        def first = agile.saveAssessment(AgileAssessment.record(productId, TODAY.minusDays(90), 'Ewa', LEVELS, 'Kick-off', TODAY))
        def sameDay = agile.saveAssessment(AgileAssessment.record(productId, TODAY, 'Ewa', LEVELS, null, TODAY))
        def latest = agile.saveAssessment(AgileAssessment.record(productId, TODAY, 'Jan', LEVELS + [(TEAM): 5], null, TODAY))
        entities.clear()

        expect:
        agile.findAssessments(productId)*.id() == [latest.id(), sameDay.id(), first.id()]
        agile.findAssessments()*.id() == [latest.id(), sameDay.id(), first.id()]
        agile.loadAssessment(latest.id()).get() == latest
        latest.levels()[TEAM] == 5
        latest.overallLevel() == 2.6
        jdbc.queryForObject('SELECT COUNT(*) FROM AGL_ASSESSMENT_LEVEL WHERE ASSESSMENT_ID = ?', Integer, first.id()) == 5

        when:
        agile.deleteAssessment(first.id())
        agile.deleteAssessment(999_999L)
        entities.flush()
        entities.clear()

        then:
        agile.findAssessments(productId)*.id() == [latest.id(), sameDay.id()]
        jdbc.queryForObject('SELECT COUNT(*) FROM AGL_ASSESSMENT_LEVEL WHERE ASSESSMENT_ID = ?', Integer, first.id()) == 0
    }

    def "a deleted product takes its QM and agile data with it"() {
        given:
        quality.saveProfile(QualityProfile.none(productId).change(null, QualityProfile.none(productId)))
        agile.saveProfile(AgileProfile.none(productId).change(null, AgileProfile.none(productId)))
        agile.saveAssessment(AgileAssessment.record(productId, TODAY, 'Ewa', LEVELS, null, TODAY))
        entities.flush()

        when:
        jdbc.update('DELETE FROM DSO_SERVICE WHERE PRODUCT_ID = ?', productId)
        jdbc.update('DELETE FROM DSO_PRODUCT WHERE ID = ?', productId)

        then:
        ['QA_APPLICATION', 'AGL_APPLICATION', 'AGL_ASSESSMENT'].every {
            jdbc.queryForObject("SELECT COUNT(*) FROM $it WHERE PRODUCT_ID = ?".toString(), Integer, productId) == 0
        }
        jdbc.queryForObject('SELECT COUNT(*) FROM AGL_ASSESSMENT_LEVEL', Integer) == 0
    }
}
