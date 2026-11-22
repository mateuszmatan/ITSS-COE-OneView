package com.bbh.itss.coe.oneview.regression

import com.bbh.itss.coe.oneview.support.OneViewSpecification

import static com.bbh.itss.coe.oneview.support.ApiJson.pipeline
import static com.bbh.itss.coe.oneview.support.ApiJson.product
import static com.bbh.itss.coe.oneview.support.ApiJson.service

/**
 * The report upload of the DSOEnhanced library: one {@code POST /api/dso/reports} per pipeline key of a run,
 * each with the same job, build and report.
 */
class PipelineReportRegressionSpec extends OneViewSpecification {

    String code
    String sharedJob
    Map gui
    Map backendApi

    def setup() {
        code = uniqueCode()
        sharedJob = "DevSecOps/$code/full".toString()
        Map created = createProduct(product(code: code, name: "Product $code",
                services: [service(name: 'gui'), service(name: 'backend-api')]))
        gui = pipelineFor(created.services[0].id as long, pipeline(jenkinsJob: sharedJob))
        backendApi = pipelineFor(created.services[1].id as long, pipeline(jenkinsJob: sharedJob))
    }

    private Map report(String key, Map overrides = [:]) {
        [pipelineKey: key, job: sharedJob, build: 42, result: 'UNSTABLE', branch: 'main', commit: 'b815d55',
         durationSeconds: 754, stagesTotal: 12, passed: 9, warned: 1, failed: 0, blocked: 0, skipped: 2,
         reportHtml: "<html><body>Report of $code build 42</body></html>".toString(),
         autoResolved: [[service: 'gui', field: 'buildTool', value: 'npm', reason: 'package.json found']]] + overrides
    }

    def "a run with the keys of two pipelines of one Jenkins job stores its report with each pipeline"() {
        when:
        def first = api.post('/api/dso/reports', report(gui.activeKey.value as String))
        def second = api.post('/api/dso/reports', report(backendApi.activeKey.value as String))

        then:
        first.status == 201
        second.status == 201
        first.json.pipelineId == gui.id
        second.json.pipelineId == backendApi.id
        first.json.jobName == sharedJob
        first.json.buildNumber == 42
        first.json.result == 'UNSTABLE'

        and:
        [gui, backendApi].every { Map stored ->
            def latest = api.get("/api/pipelines/$stored.id/report")
            latest.status == 200 && latest.json.buildNumber == 42 && latest.json.stagesTotal == 12 &&
                    latest.json.autoResolved == [[service: 'gui', field: 'buildTool', value: 'npm', reason: 'package.json found']]
        }
        api.get("/api/pipelines/$gui.id/report/html").body.contains("Report of $code build 42")
        api.get("/api/pipelines/$backendApi.id/runs/42/report/html").body.contains("Report of $code build 42")
    }

    def "a report with a key that is not active is stored without a pipeline when its Jenkins job belongs to several"() {
        given:
        String stale = gui.activeKey.value
        api.post("/api/pipelines/$gui.id/keys")

        when:
        def answered = api.post('/api/dso/reports', report(stale))
        def unknown = api.post('/api/dso/reports', report('00000000-0000-4000-8000-000000000000'))

        then:
        answered.status == 201
        answered.json.pipelineId == null
        unknown.status == 201
        unknown.json.pipelineId == null
        api.get("/api/pipelines/$gui.id/report").status == 404
        api.get("/api/pipelines/$backendApi.id/report").status == 404
    }

    def "a report with a key that is not active is stored with the one pipeline of its Jenkins job"() {
        given:
        String ownJob = "DevSecOps/$code/gui-sast".toString()
        Map sast = pipelineFor(gui.serviceId as long, pipeline(type: 'SAST', jenkinsJob: ownJob))

        when:
        def answered = api.post('/api/dso/reports', report('00000000-0000-4000-8000-000000000000', [job: ownJob, build: 7]))

        then:
        answered.status == 201
        answered.json.pipelineId == sast.id
        api.get("/api/pipelines/$sast.id/report").json.buildNumber == 7
    }
}
