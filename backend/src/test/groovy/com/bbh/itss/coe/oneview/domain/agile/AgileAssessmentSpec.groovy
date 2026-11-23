package com.bbh.itss.coe.oneview.domain.agile

import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException
import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException.FieldProblem
import spock.lang.Specification

import java.time.LocalDate

import static com.bbh.itss.coe.oneview.domain.agile.AgileDimension.ENGINEERING
import static com.bbh.itss.coe.oneview.domain.agile.AgileDimension.IMPROVEMENT
import static com.bbh.itss.coe.oneview.domain.agile.AgileDimension.PLANNING
import static com.bbh.itss.coe.oneview.domain.agile.AgileDimension.PRODUCT
import static com.bbh.itss.coe.oneview.domain.agile.AgileDimension.TEAM

class AgileAssessmentSpec extends Specification {

    static final LocalDate TODAY = LocalDate.parse('2026-11-23')
    static final Map<AgileDimension, Integer> LEVELS = [(TEAM): 3, (PLANNING): 4, (ENGINEERING): 2, (PRODUCT): 3,
                                                        (IMPROVEMENT): 4]
    static final List<FieldProblem> NOT_RATED = AgileDimension.values().collect {
        new FieldProblem("levels.${it.name()}", "rate ${it.label} from 1 to 5")
    }

    def "an assessment rates every dimension, keeps its texts trimmed and is new until it is stored"() {
        when:
        def assessment = AgileAssessment.record(7L, TODAY, ' Ewa Coach ', LEVELS, '  ', TODAY)

        then:
        assessment == new AgileAssessment(null, 7L, TODAY, 'Ewa Coach', LEVELS, null)
        assessment.levels().keySet() as List == [TEAM, PLANNING, ENGINEERING, PRODUCT, IMPROVEMENT]
        AgileAssessment.record(7L, TODAY.minusDays(400), 'Ewa', LEVELS, 'Yearly review', TODAY).notes() == 'Yearly review'
    }

    def "the overall level is the mean of the levels to one decimal"() {
        expect:
        new AgileAssessment(1L, 7L, TODAY, 'Ewa', levels, null).overallLevel() == overall

        where:
        levels                                                                            || overall
        LEVELS                                                                            || 3.2
        [(TEAM): 1, (PLANNING): 1, (ENGINEERING): 1, (PRODUCT): 1, (IMPROVEMENT): 2]      || 1.2
        [(TEAM): 5, (PLANNING): 5, (ENGINEERING): 5, (PRODUCT): 5, (IMPROVEMENT): 5]      || 5.0
        [(TEAM): 2, (PLANNING): 3, (ENGINEERING): 3]                                      || 2.7
        [:]                                                                               || null
        null                                                                              || null
    }

    def "an assessment that is #problem is refused against its field"() {
        when:
        AgileAssessment.record(7L, assessedOn, assessor, levels, notes, TODAY)

        then:
        def e = thrown(InvalidRequestException)
        e.problems() == problems

        where:
        problem             | assessedOn | assessor  | levels                          | notes      || problems
        'undated'           | null       | 'Ewa'     | LEVELS                          | null       || [new FieldProblem('assessedOn', 'set the day of the assessment')]
        'made tomorrow'     | TODAY.plusDays(1)  | 'Ewa'     | LEVELS                          | null       || [new FieldProblem('assessedOn', 'must not be in the future')]
        'made by nobody'    | TODAY      | '  '      | LEVELS                          | null       || [new FieldProblem('assessor', 'name who made the assessment')]
        'too long'          | TODAY      | 'E' * 201 | LEVELS                          | 'n' * 4001 || [new FieldProblem('assessor', 'is too long: it may take at most 200 bytes'),
                                                                                                    new FieldProblem('notes', 'is too long: it may take at most 4000 bytes')]
        'missing a rating'  | TODAY      | 'Ewa'     | LEVELS.findAll { it.key != PRODUCT } | null || [new FieldProblem('levels.PRODUCT', 'rate Product ownership from 1 to 5')]
        'rated outside 1-5' | TODAY      | 'Ewa'     | LEVELS + [(TEAM): 0, (IMPROVEMENT): 6] | null || [new FieldProblem('levels.TEAM', 'must be between 1 and 5'),
                                                                                                    new FieldProblem('levels.IMPROVEMENT', 'must be between 1 and 5')]
        'not rated at all'  | TODAY      | 'Ewa'     | null                            | null       || NOT_RATED
    }

    def "every dimension and framework has a label, and every dimension says what it rates"() {
        expect:
        AgileDimension.values()*.label == ['Team and roles', 'Planning and delivery', 'Engineering practices',
                                           'Product ownership', 'Continuous improvement']
        AgileDimension.values().every { it.description.endsWith('.') }
        AgileFramework.values()*.label == ['Scrum', 'Kanban', 'SAFe', 'Other']
    }
}
