package com.bbh.itss.coe.oneview.domain.agile

import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException
import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException.FieldProblem
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.coe.oneview.domain.agile.AgileFramework.KANBAN
import static com.bbh.itss.coe.oneview.domain.agile.AgileFramework.SCRUM
import static com.bbh.itss.coe.oneview.domain.shared.Failures.STALE_VERSION

class AgileProfileSpec extends Specification {

    static final Instant SAVED = Instant.parse('2026-11-20T10:00:00Z')

    def "an application never set up takes no part, and a profile keeps its names trimmed"() {
        expect:
        AgileProfile.none(7L) == new AgileProfile(7L, false, null, null, null, null, null)
        new AgileProfile(7L, true, ' Ledger Squad ', SCRUM, '  ', null, null).teamName() == 'Ledger Squad'
        new AgileProfile(7L, true, ' Ledger Squad ', SCRUM, '  ', null, null).coach() == null
    }

    def "a change keeps the application, the version and the save time and takes everything else"() {
        given:
        def stored = new AgileProfile(7L, false, 'Ledger', SCRUM, null, 1L, SAVED)

        expect:
        stored.change(1L, new AgileProfile(99L, true, 'Ledger Squad', KANBAN, 'Ewa', 99L, Instant.EPOCH)) ==
                new AgileProfile(7L, true, 'Ledger Squad', KANBAN, 'Ewa', 1L, SAVED)
        AgileProfile.none(7L).change(null, new AgileProfile(7L, true, null, null, null, null, null)) ==
                new AgileProfile(7L, true, null, null, null, null, null)
    }

    def "names longer than 200 bytes are refused against their field"() {
        when:
        AgileProfile.none(7L).change(null, new AgileProfile(7L, true, 'T' * 201, SCRUM, 'Ł' * 101, null, null))

        then:
        def e = thrown(InvalidRequestException)
        e.problems() == [new FieldProblem('teamName', 'is too long: it may take at most 200 bytes'),
                         new FieldProblem('coach', 'is too long: it may take at most 200 bytes')]
    }

    def "a profile read at #based is not changed"() {
        when:
        new AgileProfile(7L, true, null, null, null, stored, SAVED).change(expected, AgileProfile.none(7L))

        then:
        def e = thrown(IllegalStateException)
        e.message == STALE_VERSION

        where:
        based                          | stored | expected
        'an older version'             | 3L     | 2L
        'no version once it is saved'  | 3L     | null
        'a version before it is saved' | null   | 0L
    }
}
