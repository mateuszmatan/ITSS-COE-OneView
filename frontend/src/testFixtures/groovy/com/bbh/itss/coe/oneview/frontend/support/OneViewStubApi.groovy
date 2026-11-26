package com.bbh.itss.coe.oneview.frontend.support

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList

import static com.bbh.itss.coe.oneview.frontend.support.StubResponse.html
import static com.bbh.itss.coe.oneview.frontend.support.StubResponse.json
import static com.bbh.itss.coe.oneview.frontend.support.StubResponse.problem
import static com.bbh.itss.coe.oneview.frontend.support.StubResponse.yaml
import static java.math.RoundingMode.HALF_UP

class OneViewStubApi extends StubApi {

    static final String REPORT_HTML = '<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"><title>Pipeline report</title></head><body><p>No report has been uploaded for this pipeline yet.</p></body></html>'

    @Override
    void install() {
        installDepartments(serviceCount: 0, pipelineCount: 0, activePipelineCount: 0)
        get('/api/products') { RecordedRequest request ->
            def search = request.params().search?.toLowerCase()
            def products = fixture('products.json') as List<Map>
            json(search ? products.findAll {
                [it.code, it.name, it.ownerTeam, it.departmentName].any { value -> value?.toString()?.toLowerCase()?.contains(search) }
            } : products)
        }
        get('/api/products/code-suggestion') { RecordedRequest request ->
            json([code: request.params().name.toUpperCase().replaceAll(/[^A-Z0-9]/, '')])
        }
        get('/api/products/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("product-${ids[0]}.json", "Product ${ids[0]} does not exist") }
        get('/api/products/(\\d+)/pipelines') { RecordedRequest request, List<String> ids -> fixtureOr404("product-${ids[0]}-pipelines.json", "Product ${ids[0]} does not exist") }
        get('/api/products/(\\d+)/config') { RecordedRequest request, List<String> ids -> fixtureOr404("product-${ids[0]}-config.yaml", "Product ${ids[0]} does not exist") }
        get('/api/pipelines/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("pipeline-${ids[0]}.json", "Pipeline ${ids[0]} does not exist") }
        get('/api/pipelines/(\\d+)/config') { RecordedRequest request, List<String> ids -> fixtureOr404("pipeline-${ids[0]}-config.yaml", "Pipeline ${ids[0]} does not exist") }
        get('/api/pipelines/(\\d+)/pull-request-decorations') { RecordedRequest request, List<String> ids -> pullRequestDecorations(ids[0], request.params()) }
        get('/api/pipelines/(\\d+)/report/html') { html(REPORT_HTML) }
        get('/api/pipelines/(\\d+)/runs/(\\d+)/report/html') { html(REPORT_HTML) }
        get('/api/pipelines') { RecordedRequest request ->
            def departmentId = request.params().departmentId as Integer
            if (departmentId != null && !departments.any { it.id == departmentId }) {
                return problem(404, 'Not found', "Business service $departmentId does not exist")
            }
            def products = (fixture('products.json') as List<Map>).findAll { departmentId == null || it.departmentId == departmentId }
            def pipelines = products.collectMany { product ->
                hasFixture("monitoring-product-${product.id}.json") ? fixture("monitoring-product-${product.id}.json").pipelines as List : []
            }
            json([pipelines: pipelines, metricsError: null])
        }
        def template = fixture('service-template.json') as Map
        get('/api/service-template') { json(template) }
        on('PUT', '/api/service-template') { RecordedRequest request ->
            def sent = request.json() as Map
            if (sent.version != template.version) {
                return problem(409, 'Conflict', 'The service template was changed by someone else')
            }
            template.putAll(sent)
            template.putAll(version: (template.version as int) + 1, updatedAt: '2026-10-08T13:00:00Z')
            json(template)
        }
        get('/api/settings') { json(fixture('settings.json')) }
        get('/api/settings/config') { yaml(fixtureText('settings-config.yaml')) }
        get('/api/monitoring/status') { json(fixture('monitoring-status.json')) }
        get('/api/monitoring/products') { json(fixture('monitoring-products.json')) }
        get('/api/monitoring/activity') { json(fixture('monitoring-activity.json')) }
        get('/api/monitoring/products/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("monitoring-product-${ids[0]}.json", "Product ${ids[0]} does not exist") }
        get('/api/monitoring/pipelines/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("monitoring-pipeline-${ids[0]}.json", "Pipeline ${ids[0]} does not exist") }
        get('/api/action-history') { json(fixture('action-history.json')) }
        get('/api/user/me') {
            json([username: 'mateusz.matan', displayName: 'Mateusz Matan', email: 'mateusz.matan@bbh.com', admin: true])
        }
        get('/api/samm/overview') { RecordedRequest request -> sammOverview(request.params()) }
        get('/api/samm/pipelines/(\\d+)') { RecordedRequest request, List<String> ids ->
            def scorecard = (fixture('samm-overview.json').applications as List<Map>).find { it.pipelineId == ids[0] as int }
            scorecard ? json(scorecard) : problem(404, 'Not found', "Pipeline ${ids[0]} does not exist")
        }
        installQuality()
        installAgile()
    }

    /** The QM Metric Tool: the BBH targets and the applications, saved as the portal saves them. */
    void installQuality() {
        def defaults = fixture('qa-defaults.json') as Map
        def applications = new CopyOnWriteArrayList<Map>(fixture('qa-applications.json') as List<Map>)
        get('/api/qa/defaults') { json(defaults) }
        on('PUT', '/api/qa/defaults') { RecordedRequest request ->
            def sent = request.json() as Map
            if (sent.version != defaults.version) {
                return problem(409, 'Conflict', 'The BBH quality targets were changed by someone else')
            }
            defaults.putAll(targets: sent.targets, version: nextVersion(defaults.version), updatedAt: now())
            applications.each { it.effectiveTargets = effectiveTargets(it.targets as Map, defaults.targets as Map) }
            json(defaults)
        }
        get('/api/qa/applications') { json(applications) }
        get('/api/qa/applications/(\\d+)') { RecordedRequest request, List<String> ids ->
            def application = applications.find { it.productId == ids[0] as int }
            application ? json(application) : problem(404, 'Not found', "Product ${ids[0]} does not exist")
        }
        on('PUT', '/api/qa/applications/(\\d+)') { RecordedRequest request, List<String> ids ->
            def application = applications.find { it.productId == ids[0] as int }
            if (!application) {
                return problem(404, 'Not found', "Product ${ids[0]} does not exist")
            }
            def sent = request.json() as Map
            if (sent.version != application.version) {
                return problem(409, 'Conflict', "${application.name} was changed by someone else")
            }
            application.putAll(sent.subMap(['enabled', 'qaLead', 'sonarProjectKey', 'defectProjectKey', 'dashboardUrl', 'targets']))
            application.putAll(effectiveTargets: effectiveTargets(application.targets as Map, defaults.targets as Map),
                    version: nextVersion(application.version), updatedAt: now())
            json(application)
        }
    }

    /** Agile Assessment & Adoption: the model, the teams and their assessments, saved as the portal saves them. */
    void installAgile() {
        def applications = new CopyOnWriteArrayList<Map>(fixture('agile-applications.json') as List<Map>)
        long nextAssessmentId = 100
        def application = { String id -> applications.find { it.productId == id as int } }
        get('/api/agile/model') { json(fixture('agile-model.json')) }
        get('/api/agile/applications') { json(applications) }
        get('/api/agile/applications/(\\d+)') { RecordedRequest request, List<String> ids ->
            application(ids[0]) ? json(application(ids[0])) : problem(404, 'Not found', "Product ${ids[0]} does not exist")
        }
        on('PUT', '/api/agile/applications/(\\d+)') { RecordedRequest request, List<String> ids ->
            def team = application(ids[0])
            if (!team) {
                return problem(404, 'Not found', "Product ${ids[0]} does not exist")
            }
            def sent = request.json() as Map
            if (sent.version != team.version) {
                return problem(409, 'Conflict', "The team of ${team.name} was changed by someone else")
            }
            team.putAll(sent.subMap(['enabled', 'teamName', 'framework', 'coach']))
            team.putAll(version: nextVersion(team.version), updatedAt: now())
            json(team)
        }
        on('POST', '/api/agile/applications/(\\d+)/assessments') { RecordedRequest request, List<String> ids ->
            def team = application(ids[0])
            if (!team) {
                return problem(404, 'Not found', "Product ${ids[0]} does not exist")
            }
            if (!team.enabled) {
                return problem(409, 'Conflict', (team.version == null ? "Set up the team of ${team.name} first"
                        : "Edit the team of ${team.name} and tick Takes part first") +
                        ': only a team that takes part is assessed.')
            }
            def sent = request.json() as Map
            // As the portal: any day up to today in the earliest time zone, UTC+14.
            if (sent.assessedOn && LocalDate.parse(sent.assessedOn as String).isAfter(LocalDate.now(ZoneOffset.ofHours(14)))) {
                return problem(400, 'Validation failed', 'The request has invalid fields',
                        [errors: [[field: 'assessedOn', message: 'must not be in the future']]])
            }
            def levels = sent.levels as Map<String, Integer>
            def assessment = [id          : nextAssessmentId++, productId: team.productId, assessedOn: sent.assessedOn,
                              assessor    : sent.assessor, levels: levels,
                              overallLevel: (levels.values().sum() as BigDecimal).divide(levels.size() as BigDecimal, 1, HALF_UP),
                              notes       : sent.notes]
            team.assessments = (([assessment] + (team.assessments as List<Map>)) as List<Map>)
                    .sort { Map a, Map b -> (b.assessedOn as String) <=> (a.assessedOn as String) ?: (b.id as long) <=> (a.id as long) }
            json(assessment, 201)
        }
        get('/api/agile/assessments/(\\d+)') { RecordedRequest request, List<String> ids ->
            def assessment = applications.collectMany { it.assessments as List<Map> }.find { it.id == ids[0] as long }
            assessment ? json(assessment) : problem(404, 'Not found', "Assessment ${ids[0]} does not exist")
        }
        on('DELETE', '/api/agile/assessments/(\\d+)') { RecordedRequest request, List<String> ids ->
            def team = applications.find { (it.assessments as List<Map>).any { it.id == ids[0] as long } }
            if (!team) {
                return problem(404, 'Not found', "Assessment ${ids[0]} does not exist")
            }
            team.assessments = (team.assessments as List<Map>).findAll { it.id != ids[0] as long }
            StubResponse.empty()
        }
    }

    /**
     * The decorations of pull requests the pipeline library reported for a pipeline, the newest first and as many as
     * asked for, as the portal answers them: none for a pipeline without a fixture, 404 for a pipeline that does not exist.
     */
    static StubResponse pullRequestDecorations(String id, Map<String, String> params) {
        if (!hasFixture("pipeline-${id}.json")) {
            return problem(404, 'Not found', "Pipeline $id does not exist")
        }
        def name = "pipeline-${id}-pull-request-decorations.json"
        def decorations = hasFixture(name) ? fixture(name) as List<Map> : []
        def limit = params.limit?.isInteger() ? Math.min(Math.max(params.limit as int, 1), 100) : 20
        json(decorations.take(limit))
    }

    /** The OWASP SAMM maturity of the applications in the business service or the application asked for. */
    static Map sammOverview(Map<String, String> params) {
        def overview = fixture('samm-overview.json') as Map
        def departmentId = params.departmentId as Integer
        def productId = params.productId as Integer
        def applications = (overview.applications as List<Map>).findAll {
            productId != null ? it.productId == productId : (departmentId == null || it.departmentId == departmentId)
        }
        def departments = productId != null ? [] : (overview.departments as List<Map>).findAll { department ->
            departmentId == null ? applications.any { it.departmentId == department.departmentId }
                    : department.departmentId == departmentId
        }
        overview + [applications: applications, departments: departments, totalApplications: applications.size(),
                    totalDepartments: departments.size()]
    }

    static Map effectiveTargets(Map own, Map bbh) {
        bbh.collectEntries { key, value -> [(key): own?.get(key) != null ? own.get(key) : value] }
    }

    static long nextVersion(Object version) {
        version == null ? 0 : (version as long) + 1
    }

    static String now() {
        Instant.now().toString()
    }
}
