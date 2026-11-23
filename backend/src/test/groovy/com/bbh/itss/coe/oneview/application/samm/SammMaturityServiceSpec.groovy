package com.bbh.itss.coe.oneview.application.samm;

import com.bbh.itss.coe.oneview.application.catalog.port.out.DepartmentRepositoryPort
import com.bbh.itss.coe.oneview.application.monitoring.port.in.MonitoringTargets
import com.bbh.itss.coe.oneview.application.monitoring.port.in.ReadMonitoringTargetsUseCase
import com.bbh.itss.coe.oneview.application.monitoring.port.out.PipelineRunsPort
import com.bbh.itss.coe.oneview.application.pipeline.port.in.PipelineView
import com.bbh.itss.coe.oneview.application.report.port.out.PipelineReportPort
import com.bbh.itss.coe.oneview.domain.catalog.Department
import com.bbh.itss.coe.oneview.domain.monitoring.LatestRuns
import com.bbh.itss.coe.oneview.domain.settings.PlatformSettings
import com.bbh.itss.coe.oneview.support.Fixtures
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SammMaturityServiceSpec extends Specification {

    ReadMonitoringTargetsUseCase monitoringTargets = Mock()
    DepartmentRepositoryPort departments = Mock()
    PipelineReportPort reports = Mock()
    PipelineRunsPort runs = Mock()
    Clock clock = Clock.fixed(Instant.parse('2026-11-04T12:00:00Z'), ZoneOffset.UTC)

    SammMaturityService service = new SammMaturityService(monitoringTargets, departments, reports, runs, clock)

    def "overview calculates SAMM scores across all applications and departments"() {
        given:
        def dept1 = new Department(1L, 'Corporate Advisory', 1)
        def dept2 = new Department(2L, 'Custody & Clearing', 1)

        def product = Fixtures.product(id: 10L, name: 'Advisor Portal', departmentId: 1L, services: [[id: 20L, name: 'advisor-api']])
        def pipeline = Fixtures.pipeline(id: 100L, productId: 10L, serviceId: 20L)
        def view = new PipelineView(product, product.services()[0], pipeline, 'https://jenkins.bbh.com')

        def platform = PlatformSettings.builder().jenkinsUrl('https://jenkins.bbh.com').build()
        def targets = new MonitoringTargets([product], [view], platform, [] as Set)

        departments.findAll() >> [dept1, dept2]
        monitoringTargets.everything() >> targets
        runs.configured(platform) >> false
        reports.findLatestByPipelineId(100L) >> Optional.empty()

        when:
        def overview = service.overview()

        then:
        overview.totalApplications() == 1
        overview.totalDepartments() == 2
        overview.applications().size() == 1
        overview.applications()[0].pipelineId() == 100L
        overview.applications()[0].departmentName() == 'Corporate Advisory'
        overview.departments().size() == 2
        overview.domainScores().size() == 5
    }

    def "the overview of a business service or of one application holds only their pipelines"() {
        given:
        def advisory = new Department(1L, 'Corporate Advisory', 1)
        def custody = new Department(2L, 'Custody & Clearing', 1)
        def advisor = Fixtures.product(id: 10L, name: 'Advisor Portal', departmentId: 1L, services: [[id: 20L, name: 'advisor-api']])
        def ledger = Fixtures.product(id: 11L, name: 'Ledger', departmentId: 2L, services: [[id: 21L, name: 'ledger-api']])
        def views = [new PipelineView(advisor, advisor.services()[0], Fixtures.pipeline(id: 100L, productId: 10L, serviceId: 20L),
                                      'https://jenkins.bbh.com'),
                     new PipelineView(ledger, ledger.services()[0], Fixtures.pipeline(id: 101L, productId: 11L, serviceId: 21L),
                                      'https://jenkins.bbh.com')]
        def platform = PlatformSettings.builder().jenkinsUrl('https://jenkins.bbh.com').build()

        departments.findAll() >> [advisory, custody]
        monitoringTargets.everything() >> new MonitoringTargets([advisor, ledger], views, platform, [] as Set)
        runs.configured(platform) >> false
        reports.findLatestByPipelineId(_) >> Optional.empty()

        expect:
        with(service.overview(2L, null)) {
            applications()*.pipelineId() == [101L]
            departments()*.departmentName() == ['Custody & Clearing']
            totalApplications() == 1
        }
        with(service.overview(null, 10L)) {
            applications()*.pipelineId() == [100L]
            departments() == []
            totalDepartments() == 0
        }
        service.overview(null, null).applications()*.pipelineId() == [100L, 101L]
        service.overview().departments().size() == 2
    }

    def "queries department and application score by id"() {
        given:
        def dept = new Department(5L, 'Treasury', 1)
        def product = Fixtures.product(id: 10L, name: 'FX Desk', departmentId: 5L, services: [[id: 30L, name: 'fx-api']])
        def pipeline = Fixtures.pipeline(id: 200L, productId: 10L, serviceId: 30L)
        def view = new PipelineView(product, product.services()[0], pipeline, 'https://jenkins.bbh.com')
        def platform = PlatformSettings.builder().jenkinsUrl('https://jenkins.bbh.com').build()
        def targets = new MonitoringTargets([product], [view], platform, [] as Set)

        departments.findAll() >> [dept]
        monitoringTargets.everything() >> targets
        runs.configured(platform) >> false
        reports.findLatestByPipelineId(200L) >> Optional.empty()

        when:
        def app = service.applicationScore(200L)
        def d = service.departmentScore(5L)
        def missing = service.applicationScore(999L)

        then:
        app.isPresent()
        app.get().pipelineId() == 200L
        d.isPresent()
        d.get().departmentId() == 5L
        missing.isEmpty()
    }
}
