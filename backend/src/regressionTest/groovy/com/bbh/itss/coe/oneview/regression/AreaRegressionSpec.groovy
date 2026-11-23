package com.bbh.itss.coe.oneview.regression

import com.bbh.itss.coe.oneview.support.OneViewSpecification

import java.time.LocalDate

import static com.bbh.itss.coe.oneview.support.ApiJson.pipeline
import static com.bbh.itss.coe.oneview.support.ApiJson.product
import static com.bbh.itss.coe.oneview.support.ApiJson.service

/**
 * The areas QM Metric Tool and Agile Assessment &amp; Adoption are kept per application (a product of the catalog)
 * and every change to them is in the action history under its business service and application.
 */
class AreaRegressionSpec extends OneViewSpecification {

    static final List<String> DIMENSIONS = ['TEAM', 'PLANNING', 'ENGINEERING', 'PRODUCT', 'IMPROVEMENT']

    Map department
    Map ledger

    def setup() {
        department = api.post('/api/departments', [name: "Areas ${uniqueCode()}".toString()]).json as Map
        def code = uniqueCode('AREA')
        ledger = createProduct(product(code: code, name: "Ledger $code", departmentId: department.id,
                services: [service(name: 'api')]))
    }

    def "every application takes the BBH targets of the QM Metric Tool until it sets its own"() {
        given:
        def defaults = api.get('/api/qa/defaults').json

        when:
        def listed = api.get('/api/qa/applications').json.find { it.productId == ledger.id }

        then:
        defaults.targets.keySet() as List == ['coveragePercent', 'passRatePercent', 'maxCriticalDefects']
        listed == [productId    : ledger.id, code: ledger.code, name: ledger.name, departmentId: department.id,
                   departmentName: department.name, enabled: false, qaLead: null, sonarProjectKey: null,
                   defectProjectKey: null, dashboardUrl: null,
                   targets: [coveragePercent: null, passRatePercent: null, maxCriticalDefects: null],
                   effectiveTargets: defaults.targets, version: null, updatedAt: null]
        api.get("/api/qa/applications/$ledger.id").json == listed
    }

    def "an application is set up for the QM Metric Tool at the version it was read at"() {
        when:
        def saved = api.put("/api/qa/applications/$ledger.id", [enabled: true, qaLead: ' Anna QA ',
                sonarProjectKey: "bbh:$ledger.code", dashboardUrl: 'https://grafana.bbh.com/d/qa',
                targets: [coveragePercent: 70], version: null])
        def bbh = api.get('/api/qa/defaults').json.targets

        then:
        saved.status == 200
        saved.json.enabled
        saved.json.qaLead == 'Anna QA'
        saved.json.version == 0
        saved.json.targets == [coveragePercent: 70, passRatePercent: null, maxCriticalDefects: null]
        saved.json.effectiveTargets == [coveragePercent: 70, passRatePercent: bbh.passRatePercent,
                                        maxCriticalDefects: bbh.maxCriticalDefects]

        when:
        def stale = api.put("/api/qa/applications/$ledger.id", [enabled: false, version: null])
        def invalid = api.put("/api/qa/applications/$ledger.id", [enabled: true, dashboardUrl: 'grafana',
                targets: [coveragePercent: 120], version: 0])
        def unknown = api.put('/api/qa/applications/999999', [enabled: true])

        then:
        stale.status == 409
        invalid.status == 400
        invalid.json.errors*.field == ['dashboardUrl', 'targets.coveragePercent']
        unknown.status == 404
        api.get('/api/qa/applications/999999').status == 404

        and: 'the action history has the change under the business service and the application'
        api.get('/api/action-history').json.findAll { it.productId == ledger.id }
                .collect { [it.actionType, it.resourceType, it.resourceName, it.departmentId, it.departmentName, it.status] } == [
                ['UPDATE_QA_APPLICATION', 'QA_APPLICATION', ledger.name, department.id, department.name, 'FAILURE'],
                ['UPDATE_QA_APPLICATION', 'QA_APPLICATION', ledger.name, department.id, department.name, 'FAILURE'],
                ['UPDATE_QA_APPLICATION', 'QA_APPLICATION', ledger.name, department.id, department.name, 'SUCCESS'],
                ['CREATE_PRODUCT', 'PRODUCT', ledger.name, department.id, department.name, 'SUCCESS']]
    }

    def "the BBH targets are changed at the version they were read at, and every one of them must be set"() {
        given:
        def current = api.get('/api/qa/defaults').json

        when:
        def saved = api.put('/api/qa/defaults', [targets: [coveragePercent: 82, passRatePercent: 97, maxCriticalDefects: 1],
                                                 version: current.version])

        then:
        saved.status == 200
        saved.json.targets == [coveragePercent: 82, passRatePercent: 97, maxCriticalDefects: 1]
        saved.json.version == (current.version == null ? 0 : current.version + 1)
        api.get("/api/qa/applications/$ledger.id").json.effectiveTargets == saved.json.targets

        when:
        def stale = api.put('/api/qa/defaults', [targets: current.targets, version: current.version])
        def incomplete = api.put('/api/qa/defaults', [targets: [coveragePercent: 80], version: saved.json.version])
        def restored = api.put('/api/qa/defaults', [targets: current.targets, version: saved.json.version])

        then:
        stale.status == 409
        incomplete.status == 400
        incomplete.json.errors*.field == ['passRatePercent', 'maxCriticalDefects']
        restored.status == 200
        api.get('/api/action-history').json.find { it.actionType == 'UPDATE_QA_DEFAULTS' && it.status == 'SUCCESS' }
                .details.startsWith('Saved the BBH quality targets: code coverage at least')
    }

    def "the agile model lists the dimensions rated from 1 to 5 and the ways of working"() {
        when:
        def model = api.get('/api/agile/model').json

        then:
        model.dimensions*.code == DIMENSIONS
        model.dimensions.every { it.label && it.description }
        model.frameworks*.code == ['SCRUM', 'KANBAN', 'SAFE', 'OTHER']
        [model.minLevel, model.maxLevel] == [1, 5]
    }

    def "an application's team is set up for Agile Assessment & Adoption and assessed, latest first"() {
        when:
        def profile = api.put("/api/agile/applications/$ledger.id", [enabled: true, teamName: 'Ledger Squad',
                framework: 'SCRUM', coach: 'Ewa', version: null])
        def first = api.post("/api/agile/applications/$ledger.id/assessments", assessment(
                assessedOn: LocalDate.now().minusDays(90).toString(), notes: 'Kick-off'))
        def latest = api.post("/api/agile/applications/$ledger.id/assessments", assessment(
                levels: [TEAM: 4, PLANNING: 4, ENGINEERING: 3, PRODUCT: 3, IMPROVEMENT: 2]))

        then:
        profile.status == 200
        profile.json.subMap(['enabled', 'teamName', 'framework', 'coach', 'version']) ==
                [enabled: true, teamName: 'Ledger Squad', framework: 'SCRUM', coach: 'Ewa', version: 0]
        [first, latest]*.status == [201, 201]
        latest.header('Location') == "$api.baseUrl/api/agile/assessments/${latest.json.id}"
        latest.json.overallLevel == 3.2
        latest.json.levels == [TEAM: 4, PLANNING: 4, ENGINEERING: 3, PRODUCT: 3, IMPROVEMENT: 2]
        api.get("/api/agile/assessments/${latest.json.id}").json == latest.json

        when:
        def application = api.get("/api/agile/applications/$ledger.id").json
        def listed = api.get('/api/agile/applications').json.find { it.productId == ledger.id }

        then:
        application.assessments*.id == [latest.json.id, first.json.id]
        application.departmentId == department.id
        listed == application

        when:
        def deleted = api.delete("/api/agile/assessments/${first.json.id}")

        then:
        deleted.status == 204
        api.get("/api/agile/assessments/${first.json.id}").status == 404
        api.delete("/api/agile/assessments/${first.json.id}").status == 404
        api.get("/api/agile/applications/$ledger.id").json.assessments*.id == [latest.json.id]
        api.get('/api/action-history').json.findAll { it.productId == ledger.id && it.resourceType.startsWith('AGILE') }
                .collect { [it.actionType, it.departmentId, it.status] } == [
                ['DELETE_AGILE_ASSESSMENT', department.id, 'SUCCESS'],
                ['RECORD_AGILE_ASSESSMENT', department.id, 'SUCCESS'],
                ['RECORD_AGILE_ASSESSMENT', department.id, 'SUCCESS'],
                ['UPDATE_AGILE_APPLICATION', department.id, 'SUCCESS']]
    }

    def "an assessment that is #problem is refused against its field"() {
        given:
        api.put("/api/agile/applications/$ledger.id", [enabled: true, version: null])

        when:
        def response = api.post("/api/agile/applications/$ledger.id/assessments", assessment(changes))

        then:
        response.status == 400
        response.json.errors*.field == fields
        api.get("/api/agile/applications/$ledger.id").json.assessments == []

        where:
        problem           | changes                                                     || fields
        'in the future'   | [assessedOn: LocalDate.now().plusDays(3).toString()]        || ['assessedOn']
        'by nobody'       | [assessor: ' ']                                             || ['assessor']
        'missing a level' | [levels: [TEAM: 3, PLANNING: 3, ENGINEERING: 3, PRODUCT: 3]] || ['levels.IMPROVEMENT']
        'rated above 5'   | [levels: DIMENSIONS.collectEntries { [(it): 6] }]           || DIMENSIONS.collect { "levels.$it".toString() }
    }

    def "the team of an application is assessed only while it takes part"() {
        when:
        def neverSetUp = api.post("/api/agile/applications/$ledger.id/assessments", assessment())
        def profile = api.put("/api/agile/applications/$ledger.id", [enabled: true, version: null]).json
        def taking = api.post("/api/agile/applications/$ledger.id/assessments", assessment())
        api.put("/api/agile/applications/$ledger.id", [enabled: false, version: profile.version])
        def takenOut = api.post("/api/agile/applications/$ledger.id/assessments", assessment())

        then:
        neverSetUp.status == 409
        neverSetUp.json.detail == "Set up the team of $ledger.name first: only a team that takes part is assessed."
        taking.status == 201
        takenOut.status == 409
        takenOut.json.detail ==
                "Edit the team of $ledger.name and tick Takes part first: only a team that takes part is assessed."
        api.get("/api/agile/applications/$ledger.id").json.assessments*.id == [taking.json.id]
        api.get('/api/action-history').json.findAll { it.productId == ledger.id && it.actionType == 'RECORD_AGILE_ASSESSMENT' }
                *.status == ['FAILURE', 'SUCCESS', 'FAILURE']
    }

    def "an unknown application or assessment is not found"() {
        expect:
        api.get('/api/agile/applications/999999').status == 404
        api.put('/api/agile/applications/999999', [enabled: true]).status == 404
        api.post('/api/agile/applications/999999/assessments', assessment()).status == 404
        api.get('/api/agile/assessments/999999').status == 404
    }

    def "a deleted application takes its QM Metric Tool and agile data with it"() {
        given:
        api.put("/api/qa/applications/$ledger.id", [enabled: true, version: null])
        api.put("/api/agile/applications/$ledger.id", [enabled: true, version: null])
        def assessed = api.post("/api/agile/applications/$ledger.id/assessments", assessment()).json

        when:
        def deleted = api.delete("/api/products/$ledger.id")

        then:
        deleted.status == 204
        api.get("/api/qa/applications/$ledger.id").status == 404
        api.get("/api/agile/assessments/$assessed.id").status == 404
        api.get('/api/agile/applications').json.every { it.productId != ledger.id }
        jdbc.queryForObject('SELECT COUNT(*) FROM QA_APPLICATION WHERE PRODUCT_ID = ?', Integer, ledger.id) == 0
    }

    def "every change of an application, its pipelines and their keys is in the action history under its business service"() {
        given:
        def sast = createPipeline(ledger.services[0].id as long, pipeline(type: 'SAST'))

        when:
        def updated = api.put("/api/pipelines/$sast.id", pipeline(type: 'SAST', agentLabels: ['docker'], version: sast.version))
        def rotated = api.post("/api/pipelines/$sast.id/keys")
        def revoked = api.post("/api/pipelines/$sast.id/keys/revoke", [reason: 'Retired with the ledger'])
        def removed = api.delete("/api/pipelines/$sast.id")
        def current = api.get("/api/products/$ledger.id").json
        def renamed = api.put("/api/products/$ledger.id", product(code: ledger.code, name: "$ledger.name Two".toString(),
                departmentId: department.id, version: current.version, services: current.services))
        def deleted = api.delete("/api/products/$ledger.id")

        then:
        [updated, rotated, revoked, renamed]*.status == [200, 200, 200, 200]
        [removed, deleted]*.status == [204, 204]
        api.get('/api/action-history').json.findAll { it.productId == ledger.id }
                .collect { [it.actionType, it.departmentId, it.departmentName, it.status] } == [
                ['DELETE_PRODUCT', department.id, department.name, 'SUCCESS'],
                ['UPDATE_PRODUCT', department.id, department.name, 'SUCCESS'],
                ['DELETE_PIPELINE', department.id, department.name, 'SUCCESS'],
                ['REVOKE_KEY', department.id, department.name, 'SUCCESS'],
                ['ROTATE_KEY', department.id, department.name, 'SUCCESS'],
                ['UPDATE_PIPELINE', department.id, department.name, 'SUCCESS'],
                ['CREATE_PIPELINE', department.id, department.name, 'SUCCESS'],
                ['CREATE_PRODUCT', department.id, department.name, 'SUCCESS']]
    }

    def "the DevSecOps pages show the whole of BBH, one business service or one application"() {
        given:
        def other = createProduct(product(code: uniqueCode('AREA'), name: "Other ${uniqueCode()}",
                departmentId: department.id, services: [service(name: 'gui')]))

        when:
        def everything = api.get('/api/pipelines').json
        def ofDepartment = api.get("/api/pipelines?departmentId=$department.id").json
        def samm = api.get("/api/samm/overview?departmentId=$department.id").json
        def sammOfLedger = api.get("/api/samm/overview?departmentId=$department.id&productId=$ledger.id").json
        def activity = api.get("/api/monitoring/activity?range=7d&departmentId=$department.id").json
        def activityOfLedger = api.get("/api/monitoring/activity?range=7d&productId=$ledger.id").json

        then:
        everything.pipelines*.pipeline*.productId.containsAll([ledger.id, other.id])
        ofDepartment.pipelines*.pipeline*.productId as Set == [ledger.id, other.id] as Set
        samm.applications*.productId as Set == [ledger.id, other.id] as Set
        samm.departments*.departmentId == [department.id]
        sammOfLedger.applications*.productId == [ledger.id]
        sammOfLedger.departments == []
        activity.pipelines == 2
        activityOfLedger.pipelines == 1
        api.get('/api/monitoring/activity?productId=999999').status == 404
        api.get('/api/samm/overview?departmentId=999999').status == 404
        api.get('/api/samm/overview?productId=999999').status == 404
        api.get('/api/samm/overview').json.applications*.productId.containsAll([ledger.id, other.id])
    }

    private static Map assessment(Map changes = [:]) {
        [assessedOn: LocalDate.now().toString(), assessor: 'Ewa Coach',
         levels    : DIMENSIONS.collectEntries { [(it): 3] }, notes: null] + changes
    }
}
