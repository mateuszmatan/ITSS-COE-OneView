package com.bbh.itss.coe.oneview.domain.jenkins

import spock.lang.Specification

import static com.bbh.itss.coe.oneview.domain.jenkins.JobParameter.Type.PASSWORD
import static com.bbh.itss.coe.oneview.domain.jenkins.JobParameter.Type.STRING
import static com.bbh.itss.coe.oneview.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.coe.oneview.domain.pipeline.PipelineType.SAST

class JenkinsValuesSpec extends Specification {

    def "a login never prints its secret"() {
        expect:
        new GenericLogin('svc-oneview', 'secret-from-ois').toString() == 'GenericLogin[user=svc-oneview]'
    }

    def "a password parameter never carries its default value"() {
        expect:
        new JobParameter(PASSWORD, 'TOKEN', 'hunter2', null, null).defaultValue() == null
        new JobParameter(STRING, 'TARGET', 'rd', null, null).defaultValue() == 'rd'
        new JobParameter(STRING, 'TARGET', 'rd', null, null).choices() == []
    }

    def "a retention of nothing or of -1 is no limit"() {
        expect:
        new BuildRetention(days, builds).limited() == limited

        where:
        days | builds | limited
        null | null   | false
        -1   | -1     | false
        0    | null   | false
        30   | -1     | true
        null | 20     | true
    }

    def "the Jenkinsfile of a pipeline loads the library and starts the pipeline with its key"() {
        expect:
        Jenkinsfiles.withKey('DevSecOpsJenkinsLibrary', FULL, 'the-key') ==
                "@Library('DevSecOpsJenkinsLibrary') _\n\ndevSecOpsPipeline(pipelineKey: 'the-key')\n"
        Jenkinsfiles.withKey(' ', SAST, 'k') ==
                "@Library('DevSecOpsJenkinsLibrary') _\n\ndevSecOpsSASTScanningPipeline(pipelineKey: 'k')\n"
    }

    def "OneView can read the Jenkinsfile it writes"() {
        when:
        def facts = JenkinsfileFacts.read(Jenkinsfiles.withKey('DevSecOpsJenkinsLibrary', FULL, 'the-key'))

        then:
        facts.loads('DevSecOpsJenkinsLibrary')
        facts.pipelineType() == FULL
        facts.hasPipelineKey()
    }
}
