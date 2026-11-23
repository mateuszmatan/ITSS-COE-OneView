package com.bbh.itss.coe.oneview.domain.qa

import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException
import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException.FieldProblem
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.coe.oneview.domain.qa.QualityProfile.DASHBOARD_URL_MESSAGE
import static com.bbh.itss.coe.oneview.domain.shared.Failures.STALE_VERSION

class QualityProfileSpec extends Specification {

    static final Instant SAVED = Instant.parse('2026-11-20T10:00:00Z')

    def "an application never set up takes no part and sets no target of its own"() {
        expect:
        QualityProfile.none(7L) == new QualityProfile(7L, false, null, null, null, null, QualityTargets.NONE, null, null)
        QualityProfile.none(7L).effectiveTargets(QualityDefaults.INITIAL) == QualityTargets.BBH
    }

    def "a profile keeps its texts trimmed, blank ones as none, and missing targets as none"() {
        expect:
        new QualityProfile(7L, true, ' Anna QA ', ' bbh:ledger ', '  ', null, null, null, null) ==
                new QualityProfile(7L, true, 'Anna QA', 'bbh:ledger', null, null, QualityTargets.NONE, null, null)
    }

    def "a change keeps the application, the version and the save time and takes everything else"() {
        given:
        def stored = new QualityProfile(7L, false, 'Anna', null, null, null, QualityTargets.NONE, 2L, SAVED)
        def changed = new QualityProfile(99L, true, 'Piotr', 'bbh:ledger', 'LEDGER',
                'https://grafana.bbh.com/d/qa?var-app=ledger', new QualityTargets(70, null, 1), 99L, Instant.EPOCH)

        when:
        def next = stored.change(2L, changed)

        then:
        next == new QualityProfile(7L, true, 'Piotr', 'bbh:ledger', 'LEDGER',
                'https://grafana.bbh.com/d/qa?var-app=ledger', new QualityTargets(70, null, 1), 2L, SAVED)
        next.effectiveTargets(new QualityDefaults(new QualityTargets(85, 90, 0), 4L, SAVED)) ==
                new QualityTargets(70, 90, 1)
    }

    def "a profile that is #problem is refused against its field"() {
        when:
        QualityProfile.none(7L).change(null, changed)

        then:
        def e = thrown(InvalidRequestException)
        e.problems() == problems

        where:
        problem                    | changed                                                           || problems
        'linked to no web address' | profile(dashboardUrl: 'grafana.bbh.com/d/qa')                     || [new FieldProblem('dashboardUrl', DASHBOARD_URL_MESSAGE)]
        'linked with a space'      | profile(dashboardUrl: 'https://grafana.bbh.com/d/my qa')          || [new FieldProblem('dashboardUrl', DASHBOARD_URL_MESSAGE)]
        'linked with a hard space' | profile(dashboardUrl: 'https://grafana.bbh.com/d/my\u00A0qa')     || [new FieldProblem('dashboardUrl', DASHBOARD_URL_MESSAGE)]
        'linked with a $'          | profile(dashboardUrl: 'https://grafana.bbh.com/d/qa?var=${env}')  || [new FieldProblem('dashboardUrl', DASHBOARD_URL_MESSAGE)]
        'linked with a quote'      | profile(dashboardUrl: 'https://grafana.bbh.com/d/"qa"')           || [new FieldProblem('dashboardUrl', DASHBOARD_URL_MESSAGE)]
        'linked with a backslash'  | profile(dashboardUrl: 'https://grafana.bbh.com\\d\\qa')          || [new FieldProblem('dashboardUrl', DASHBOARD_URL_MESSAGE)]
        'linked with a backtick'   | profile(dashboardUrl: 'https://grafana.bbh.com/d/`qa`')           || [new FieldProblem('dashboardUrl', DASHBOARD_URL_MESSAGE)]
        'led by a too long name'   | profile(qaLead: 'Q' * 201)                                        || [new FieldProblem('qaLead', 'is too long: it may take at most 200 bytes')]
        'keyed too long'           | profile(sonarProjectKey: 'k' * 401, defectProjectKey: 'D' * 101)  || [new FieldProblem('sonarProjectKey', 'is too long: it may take at most 400 bytes'),
                                                                                                           new FieldProblem('defectProjectKey', 'is too long: it may take at most 100 bytes')]
        'aiming outside the range' | profile(targets: new QualityTargets(-1, 101, QualityTargets.MAX_DEFECTS + 1)) || [new FieldProblem('targets.coveragePercent', 'must be between 0 and 100'),
                                                                                                           new FieldProblem('targets.passRatePercent', 'must be between 0 and 100'),
                                                                                                           new FieldProblem('targets.maxCriticalDefects', 'must be between 0 and 999999')]
    }

    def "a profile read at #based is not changed"() {
        when:
        new QualityProfile(7L, true, null, null, null, null, null, stored, SAVED).change(expected, profile())

        then:
        def e = thrown(IllegalStateException)
        e.message == STALE_VERSION

        where:
        based                          | stored | expected
        'an older version'             | 3L     | 2L
        'no version once it is saved'  | 3L     | null
        'a version before it is saved' | null   | 0L
    }

    private static QualityProfile profile(Map changes = [:]) {
        Map values = [enabled: true, qaLead: 'Anna', sonarProjectKey: null, defectProjectKey: null, dashboardUrl: null,
                      targets: QualityTargets.NONE] + changes
        new QualityProfile(7L, values.enabled as boolean, values.qaLead as String, values.sonarProjectKey as String,
                values.defectProjectKey as String, values.dashboardUrl as String, values.targets as QualityTargets,
                null, null)
    }
}
