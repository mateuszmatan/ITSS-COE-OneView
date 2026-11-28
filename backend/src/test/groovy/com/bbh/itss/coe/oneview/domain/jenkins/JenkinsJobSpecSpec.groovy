package com.bbh.itss.coe.oneview.domain.jenkins

import com.bbh.itss.coe.oneview.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.coe.oneview.domain.jenkins.JobKind.MULTIBRANCH
import static com.bbh.itss.coe.oneview.domain.jenkins.JobKind.OTHER
import static com.bbh.itss.coe.oneview.domain.jenkins.JobKind.PIPELINE

class JenkinsJobSpecSpec extends Specification {

    static JenkinsJobSpec.JenkinsJobSpecBuilder job() {
        JenkinsJobSpec.builder().kind(PIPELINE).name('payments-api')
                .repositoryUrl('https://bitbucket.bbh.com/scm/pay/payments-api.git')
    }

    static List<String> invalidFields(JenkinsJobSpec job) {
        def problems = new ValidationProblems()
        job.validate(problems)
        try {
            problems.throwIfAny()
            return []
        } catch (e) {
            return e.problems*.field()
        }
    }

    def "what is left open takes the defaults: main, Jenkinsfile and a poll every five minutes"() {
        when:
        def job = job().name(' payments-api ').branch(' ').scriptPath(null).pollSchedule('').build()

        then:
        job.name() == 'payments-api'
        job.branch() == 'main'
        job.scriptPath() == 'Jenkinsfile'
        job.pollSchedule() == 'H/5 * * * *'
        job.timerSchedule() == null
        job.parameters() == []
        invalidFields(job) == []
    }

    def "a branch read from a Jenkins job loses its wildcard remote"() {
        expect:
        job().branch('*/develop').build().branch() == 'develop'
    }

    def "a job that Jenkins would refuse or that could harm is refused on its field"() {
        expect:
        invalidFields(job) == fields

        where:
        job                                                      | fields
        job().kind(null).build()                                 | ['kind']
        job().kind(OTHER).build()                                | ['kind']
        job().name(null).build()                                 | ['name']
        job().name('a/b').build()                                | ['name']
        job().name('..').build()                                 | ['name']
        job().name('x' * 201).build()                            | ['name']
        job().repositoryUrl(null).build()                        | ['repositoryUrl']
        job().repositoryUrl('payments-api').build()              | ['repositoryUrl']
        job().scriptPath('/etc/Jenkinsfile').build()             | ['scriptPath']
        job().scriptPath('../Jenkinsfile').build()               | ['scriptPath']
        job().pollSchedule('H/5 * * * *\n<script>').build()      | ['pollSchedule']
        job().timerSchedule('@midnight').build()                 | []
        job().timerSchedule('H 2 * * * ; rm').build()            | ['timerSchedule']
    }

    def "an ssh address of the repository and a Multibranch job are accepted"() {
        expect:
        invalidFields(job().kind(MULTIBRANCH).repositoryUrl('ssh://git@bitbucket.bbh.com:7999/pay/payments-api.git')
                .scriptPath('ci/Jenkinsfile').build()) == []
    }

    def "the branch scan of a Multibranch job runs at the minutes of the poll schedule"() {
        expect:
        job().pollSchedule(schedule).build().scanIntervalMinutes() == minutes

        where:
        schedule        | minutes
        'H/5 * * * *'   | 5
        '*/15 * * * *'  | 15
        'H/0 * * * *'   | 1
        'H * * * *'     | 5
        '@hourly'       | 5
    }
}
