package com.bbh.itss.coe.oneview.adapter.out.jenkins

import com.bbh.itss.coe.oneview.domain.jenkins.BuildRetention
import com.bbh.itss.coe.oneview.domain.jenkins.FolderLibrary
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSpec
import com.bbh.itss.coe.oneview.domain.jenkins.JobParameter
import com.bbh.itss.coe.oneview.domain.jenkins.JobTrigger
import spock.lang.Specification

import static com.bbh.itss.coe.oneview.domain.jenkins.JobKind.MULTIBRANCH
import static com.bbh.itss.coe.oneview.domain.jenkins.JobKind.OTHER
import static com.bbh.itss.coe.oneview.domain.jenkins.JobKind.PIPELINE
import static com.bbh.itss.coe.oneview.domain.jenkins.JobParameter.Type.BOOLEAN
import static com.bbh.itss.coe.oneview.domain.jenkins.JobParameter.Type.CHOICE
import static com.bbh.itss.coe.oneview.domain.jenkins.JobParameter.Type.PASSWORD
import static com.bbh.itss.coe.oneview.domain.jenkins.JobParameter.Type.STRING
import static com.bbh.itss.coe.oneview.domain.jenkins.JobParameter.Type.TEXT
import static com.bbh.itss.coe.oneview.domain.jenkins.JobTrigger.Kind.BRANCH_SCAN
import static com.bbh.itss.coe.oneview.domain.jenkins.JobTrigger.Kind.SCM_POLLING
import static com.bbh.itss.coe.oneview.domain.jenkins.JobTrigger.Kind.TIMER
import static com.bbh.itss.coe.oneview.domain.pipeline.PipelineType.FULL

class JenkinsConfigXmlSpec extends Specification {

    static final String REPOSITORY = 'https://bitbucket.bbh.com/scm/pay/payments-api.git'

    static JenkinsJobSpec.JenkinsJobSpecBuilder job() {
        JenkinsJobSpec.builder().kind(PIPELINE).name('payments-api').repositoryUrl(REPOSITORY)
    }

    def "a Pipeline job OneView writes reads back as it was asked for"() {
        given:
        def parameters = [new JobParameter(STRING, 'TARGET', 'rd', 'Where to deploy', null),
                          new JobParameter(TEXT, 'NOTES', 'line 1\nline 2', null, null),
                          new JobParameter(BOOLEAN, 'DRY_RUN', 'true', null, null),
                          new JobParameter(CHOICE, 'REGION', null, 'The region', ['RD', 'QC']),
                          new JobParameter(PASSWORD, 'TOKEN', 'never-written', null, null)]

        when:
        def xml = JenkinsConfigXml.write(job().description('Payments <API> & "batch"').credentialsId('bb-reader')
                .branch('develop').scriptPath('ci/Jenkinsfile').parameters(parameters)
                .retention(new BuildRetention(30, null)).concurrentBuildsDisabled(true).pollSchedule('H/10 * * * *')
                .timerSchedule('H 2 * * *').build())
        def read = JenkinsConfigXml.read(xml).build()

        then:
        !xml.contains('never-written')
        xml.contains('<daysToKeep>30</daysToKeep>\n<numToKeep>-1</numToKeep>')
        with(read) {
            kind() == PIPELINE
            description() == 'Payments <API> & "batch"'
            repositoryUrl() == REPOSITORY
            credentialsId() == 'bb-reader'
            branch() == 'develop'
            scriptPath() == 'ci/Jenkinsfile'
            it.parameters() == parameters
            retention() == new BuildRetention(30, null)
            concurrentBuildsDisabled()
            triggers() == [new JobTrigger(SCM_POLLING, 'Poll SCM', 'H/10 * * * *'),
                           new JobTrigger(TIMER, 'Build periodically', 'H 2 * * *')]
            notes() == []
        }
    }

    def "the plainest Pipeline job polls its repository and keeps every build"() {
        when:
        def xml = JenkinsConfigXml.write(job().build())
        def read = JenkinsConfigXml.read(xml).build()

        then:
        !xml.contains('BuildDiscarderProperty')
        !xml.contains('ParametersDefinitionProperty')
        !xml.contains('DisableConcurrentBuildsJobProperty')
        !xml.contains('credentialsId')
        xml.contains('<name>*/main</name>')
        read.triggers() == [new JobTrigger(SCM_POLLING, 'Poll SCM', 'H/5 * * * *')]
        read.retention() == null
        !read.concurrentBuildsDisabled()
        read.scriptPath() == 'Jenkinsfile'
    }

    def "a Multibranch job scans its branches and carries the library when it is given one"() {
        when:
        def xml = JenkinsConfigXml.write(job().kind(MULTIBRANCH).credentialsId('bb-reader').pollSchedule('H/15 * * * *')
                .library(new FolderLibrary('DevSecOpsJenkinsLibrary', 'https://bitbucket.bbh.com/scm/dso/lib.git', 'main',
                        'lib-reader')).build())
        def read = JenkinsConfigXml.read(xml).build()

        then:
        xml.contains('<interval>900000</interval>')
        xml.contains('<defaultVersion>main</defaultVersion>')
        with(read) {
            kind() == MULTIBRANCH
            repositoryUrl() == REPOSITORY
            credentialsId() == 'bb-reader'
            scriptPath() == 'Jenkinsfile'
            libraries() == ['DevSecOpsJenkinsLibrary']
            triggers() == [new JobTrigger(BRANCH_SCAN, 'Scan the branches periodically', 'every 15 minutes')]
        }

        and: 'without a library the job leaves it to Jenkins'
        !JenkinsConfigXml.write(job().kind(MULTIBRANCH).build()).contains('FolderLibraries')
    }

    def "a Pipeline job with its script in Jenkins tells what the script says"() {
        when:
        def read = JenkinsConfigXml.read('''\
            <flow-definition plugin="workflow-job@1400">
              <description></description>
              <properties>
                <jenkins.model.BuildDiscarderProperty><strategy class="hudson.tasks.LogRotator">
                  <daysToKeep>-1</daysToKeep><numToKeep>15</numToKeep></strategy></jenkins.model.BuildDiscarderProperty>
                <hudson.model.ParametersDefinitionProperty><parameterDefinitions>
                  <hudson.model.StringParameterDefinition><name>TARGET</name><defaultValue>rd</defaultValue>
                  </hudson.model.StringParameterDefinition>
                  <com.cwctravel.hudson.plugins.extended__choice__parameter.ExtendedChoiceParameterDefinition>
                    <name>MODULES</name></com.cwctravel.hudson.plugins.extended__choice__parameter.ExtendedChoiceParameterDefinition>
                </parameterDefinitions></hudson.model.ParametersDefinitionProperty>
              </properties>
              <definition class="org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition">
                <script>@Library('DevSecOpsJenkinsLibrary') _
            devSecOpsPipeline(agentNames: ['linux-agent'])</script>
                <sandbox>true</sandbox>
              </definition>
            </flow-definition>'''.stripIndent()).build()

        then:
        read.kind() == PIPELINE
        read.description() == null
        read.repositoryUrl() == null
        read.inlineScript().pipelineType() == FULL
        read.inlineScript().agentLabels() == ['linux-agent']
        read.retention() == new BuildRetention(null, 15)
        read.parameters()*.name() == ['TARGET']
        read.notes() == ['Parameter MODULES is of a kind OneView does not create (ExtendedChoiceParameterDefinition);' +
                                 ' add it to the new job yourself.']
    }

    def "a freestyle job is read for what it has in common with a Pipeline job"() {
        when:
        def read = JenkinsConfigXml.read('''\
            <project>
              <description>Legacy build</description>
              <logRotator class="hudson.tasks.LogRotator"><daysToKeep>14</daysToKeep><numToKeep>-1</numToKeep></logRotator>
              <scm class="hudson.plugins.git.GitSCM">
                <userRemoteConfigs><hudson.plugins.git.UserRemoteConfig>
                  <url>https://bitbucket.bbh.com/scm/pay/legacy.git</url><credentialsId>bb</credentialsId>
                </hudson.plugins.git.UserRemoteConfig></userRemoteConfigs>
                <branches><hudson.plugins.git.BranchSpec><name>refs/heads/release</name></hudson.plugins.git.BranchSpec></branches>
              </scm>
              <assignedNode>maven-jdk17</assignedNode>
              <concurrentBuild>false</concurrentBuild>
              <triggers>
                <hudson.triggers.SCMTrigger><spec>H/15 * * * *</spec></hudson.triggers.SCMTrigger>
                <com.cloudbees.jenkins.plugins.BitBucketTrigger/>
              </triggers>
              <builders><hudson.tasks.Shell><command>mvn verify</command></hudson.tasks.Shell></builders>
            </project>'''.stripIndent()).build()

        then:
        with(read) {
            kind() == OTHER
            description() == 'Legacy build'
            repositoryUrl() == 'https://bitbucket.bbh.com/scm/pay/legacy.git'
            credentialsId() == 'bb'
            branch() == 'release'
            agentLabel() == 'maven-jdk17'
            retention() == new BuildRetention(14, null)
            concurrentBuildsDisabled()
            triggers() == [new JobTrigger(SCM_POLLING, 'Poll SCM', 'H/15 * * * *'),
                           new JobTrigger(JobTrigger.Kind.OTHER, 'BitBucketTrigger', null)]
            notes() == [JenkinsConfigXml.NOT_A_PIPELINE]
        }
    }

    def "a Multibranch job on the Bitbucket branch source tells its repository"() {
        when:
        def read = JenkinsConfigXml.read('''\
            <org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject>
              <sources><data><jenkins.branch.BranchSource>
                <source class="com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource">
                  <serverUrl>https://bitbucket.bbh.com/</serverUrl><credentialsId>bb</credentialsId>
                  <repoOwner>PAY</repoOwner><repository>payments-api</repository>
                </source></jenkins.branch.BranchSource></data></sources>
              <factory><scriptPath>Jenkinsfile</scriptPath></factory>
            </org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject>'''.stripIndent()).build()

        then:
        read.kind() == MULTIBRANCH
        read.repositoryUrl() == 'https://bitbucket.bbh.com/scm/pay/payments-api.git'
        read.credentialsId() == 'bb'
    }

    def "a configuration that is not XML, or that names a document type, is refused without resolving anything"() {
        when:
        JenkinsConfigXml.read(xml)

        then:
        def refused = thrown(IllegalStateException)
        refused.message.startsWith('Jenkins answered with a job configuration OneView cannot read')

        where:
        xml << ['', '<html><body>Sign in',
                '<!DOCTYPE project [<!ENTITY secret SYSTEM "file:///etc/passwd">]><project><description>&secret;</description></project>']
    }

    def "text is escaped for XML and loses the control characters XML cannot carry"() {
        expect:
        JenkinsConfigXml.escape(text) == escaped

        where:
        text                         | escaped
        null                         | ''
        'a<b>&"c\''                  | 'a&lt;b&gt;&amp;&quot;c&apos;'
        'line 1\n\tline 2\u0000\u0007' | 'line 1\n\tline 2'
    }
}
