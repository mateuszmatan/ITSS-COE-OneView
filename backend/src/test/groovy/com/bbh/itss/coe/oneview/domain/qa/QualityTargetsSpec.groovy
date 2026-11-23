package com.bbh.itss.coe.oneview.domain.qa

import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException
import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException.FieldProblem
import spock.lang.Specification

import static com.bbh.itss.coe.oneview.domain.shared.Failures.STALE_VERSION

class QualityTargetsSpec extends Specification {

    def "an application takes every target it leaves out from BBH"() {
        expect:
        new QualityTargets(coverage, passRate, defects).over(QualityTargets.BBH) == expected

        where:
        coverage | passRate | defects || expected
        null     | null     | null    || new QualityTargets(80, 95, 0)
        70       | null     | null    || new QualityTargets(70, 95, 0)
        null     | 100      | 3       || new QualityTargets(80, 100, 3)
        0        | 0        | 0       || new QualityTargets(0, 0, 0)
    }

    def "targets are empty only while none is set"() {
        expect:
        QualityTargets.NONE.empty()
        !new QualityTargets(null, null, 0).empty()
        !QualityTargets.BBH.empty()
    }

    def "the BBH targets start at 80% coverage, a 95% pass rate and no open critical defect"() {
        expect:
        QualityDefaults.INITIAL == new QualityDefaults(new QualityTargets(80, 95, 0), null, null)
    }

    def "the BBH targets are changed at the version they were read at, and every one of them must be set"() {
        given:
        def stored = new QualityDefaults(QualityTargets.BBH, 3L, null)

        expect:
        stored.change(3L, new QualityTargets(75, 90, 2)) == new QualityDefaults(new QualityTargets(75, 90, 2), 3L, null)
        QualityDefaults.INITIAL.change(null, new QualityTargets(100, 100, QualityTargets.MAX_DEFECTS)).targets() ==
                new QualityTargets(100, 100, 999_999)

        when:
        stored.change(3L, new QualityTargets(null, 101, -1))

        then:
        def e = thrown(InvalidRequestException)
        e.problems() == [new FieldProblem('coveragePercent', 'set a target between 0 and 100'),
                         new FieldProblem('passRatePercent', 'must be between 0 and 100'),
                         new FieldProblem('maxCriticalDefects', 'must be between 0 and 999999')]

        when:
        stored.change(3L, null)

        then:
        thrown(InvalidRequestException)
    }

    def "the BBH targets read at #based are not changed"() {
        when:
        new QualityDefaults(QualityTargets.BBH, stored, null).change(expected, QualityTargets.BBH)

        then:
        def e = thrown(IllegalStateException)
        e.message == STALE_VERSION

        where:
        based                            | stored | expected
        'an older version'               | 2L     | 1L
        'no version once they are saved' | 2L     | null
        'a version before the first save' | null  | 0L
    }
}
