package com.bbh.itss.coe.oneview.domain.jenkins

import spock.lang.Specification

import static com.bbh.itss.coe.oneview.domain.jenkins.JobTrigger.Kind.SCM_POLLING
import static com.bbh.itss.coe.oneview.domain.jenkins.JobTrigger.Kind.TIMER
import static com.bbh.itss.coe.oneview.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.coe.oneview.domain.pipeline.PipelineType.SECURITY

class JenkinsfileFactsSpec extends Specification {

    def "a missing or empty Jenkinsfile has no facts"() {
        expect:
        JenkinsfileFacts.read(text) == null

        where:
        text << [null, '', '  \n']
    }

    def "the Jenkinsfile of the DevSecOps library tells its library, entry point, agents and services"() {
        when:
        def facts = JenkinsfileFacts.read('''\
            @Library('DevSecOpsJenkinsLibrary@release/2.x') _

            devSecOpsPipeline(
                agentNames: ['linux-agent', "linux-agent-2"],
                projectNames: 'payments-api, payments-batch',
            )
            '''.stripIndent())

        then:
        facts.libraries() == ['DevSecOpsJenkinsLibrary@release/2.x']
        facts.loads('DevSecOpsJenkinsLibrary')
        !facts.loads('DevSecOps')
        facts.entryPoint() == 'devSecOpsPipeline'
        facts.pipelineType() == FULL
        !facts.hasPipelineKey()
        facts.agentLabels() == ['linux-agent', 'linux-agent-2']
        facts.serviceNames() == ['payments-api', 'payments-batch']
        facts.triggers() == []
        facts.parameters() == []
    }

    def "a pipeline key is noticed and never kept"() {
        when:
        def facts = JenkinsfileFacts.read("@Library('DevSecOpsJenkinsLibrary') _\n" +
                "devSecOpsSecurityPipeline(pipelineKey: '0f8fad5b-d9cb-469f-a165-70867728950e')\n")

        then:
        facts.hasPipelineKey()
        facts.pipelineType() == SECURITY
        !facts.toString().contains('0f8fad5b')
    }

    def "a declarative Jenkinsfile tells its libraries, agent, triggers and parameters"() {
        when:
        def facts = JenkinsfileFacts.read('''\
            @Library(['common-steps', 'deploy@1.4']) _
            library 'reports'
            library identifier: 'notify@main'
            pipeline {
                agent { label 'maven-jdk21' }
                triggers {
                    pollSCM('H/10 * * * *')
                    cron 'H 2 * * 1-5'
                }
                parameters {
                    string(name: 'TARGET', defaultValue: 'rd')
                    booleanParam(name: 'DRY_RUN', defaultValue: true)
                    choice(name: "REGION", choices: ['RD', 'QC'])
                }
                stages { stage('Build') { steps { sh './gradlew build' } } }
            }
            '''.stripIndent())

        then:
        facts.libraries() == ['common-steps', 'deploy@1.4', 'reports', 'notify@main']
        facts.loads('deploy')
        facts.entryPoint() == null
        facts.pipelineType() == null
        facts.agentLabels() == ['maven-jdk21']
        facts.triggers() == [new JobTrigger(SCM_POLLING, 'pollSCM', 'H/10 * * * *'),
                             new JobTrigger(TIMER, 'cron', 'H 2 * * 1-5')]
        facts.parameters() == ['TARGET', 'DRY_RUN', 'REGION']
    }

    def "what is commented out is not read"() {
        when:
        def facts = JenkinsfileFacts.read('''\
            // @Library('old-library') _
            /* devSecOpsPipeline(pipelineKey: 'x')
               pollSCM('* * * * *') */
            node('windows') { bat 'build.cmd' }
            '''.stripIndent())

        then:
        facts.libraries() == []
        facts.entryPoint() == null
        !facts.hasPipelineKey()
        facts.triggers() == []
        facts.agentLabels() == ['windows']
    }

    def "the name of a library is what stands before its version"() {
        expect:
        JenkinsfileFacts.libraryName(library) == name

        where:
        library                           | name
        'DevSecOpsJenkinsLibrary'         | 'DevSecOpsJenkinsLibrary'
        'DevSecOpsJenkinsLibrary@main'    | 'DevSecOpsJenkinsLibrary'
    }
}
