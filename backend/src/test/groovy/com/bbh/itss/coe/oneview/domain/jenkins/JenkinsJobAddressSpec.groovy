package com.bbh.itss.coe.oneview.domain.jenkins

import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException
import spock.lang.Specification

class JenkinsJobAddressSpec extends Specification {

    def "the address of a job or of one of its builds is read as copied from the browser"() {
        when:
        def address = JenkinsJobAddress.ofJob('jobUrl', url)

        then:
        address.jenkinsUrl() == jenkins
        address.names() == names
        address.buildNumber() == build

        where:
        url                                                                   | jenkins                       | names                    | build
        'https://jenkins.bbh.com/job/payments/'                               | 'https://jenkins.bbh.com'     | ['payments']             | null
        'https://jenkins.bbh.com/job/DevSecOps/job/payments'                  | 'https://jenkins.bbh.com'     | ['DevSecOps', 'payments'] | null
        ' https://jenkins.bbh.com/job/DevSecOps/job/payments/42/ '            | 'https://jenkins.bbh.com'     | ['DevSecOps', 'payments'] | 42
        'https://jenkins.bbh.com/job/payments/42/console?start=1#end'         | 'https://jenkins.bbh.com'     | ['payments']             | 42
        'http://tools.bbh.com:8080/jenkins/job/My%20Team/job/a/lastBuild/'    | 'http://tools.bbh.com:8080/jenkins' | ['My Team', 'a']   | null
        'https://jenkins.bbh.com/job/payments/configure'                      | 'https://jenkins.bbh.com'     | ['payments']             | null
    }

    def "what is not the address of a job is refused on its field"() {
        when:
        JenkinsJobAddress.ofJob('jobUrl', url)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems*.field() == ['jobUrl']
        refused.message == JenkinsJobAddress.NOT_A_JOB

        where:
        url << [null, '', 'payments', 'ftp://jenkins.bbh.com/job/a/', 'https://jenkins.bbh.com', 'https://jenkins.bbh.com/view/all/',
                'https://jenkins bbh.com/job/a/']
    }

    def "the folder of a new job comes from the address of Jenkins, from the path, or from both"() {
        when:
        def folder = JenkinsJobAddress.ofFolder('jenkinsUrl', url, path)

        then:
        folder.jenkinsUrl() == 'https://jenkins.bbh.com'
        folder.names() == names
        folder.buildNumber() == null
        folder.url() == address

        where:
        url                                       | path                  | names                | address
        'https://jenkins.bbh.com'                 | null                  | []                   | 'https://jenkins.bbh.com/'
        'https://jenkins.bbh.com/'                | ' '                   | []                   | 'https://jenkins.bbh.com/'
        'https://jenkins.bbh.com'                 | 'DevSecOps/PAY'       | ['DevSecOps', 'PAY'] | 'https://jenkins.bbh.com/job/DevSecOps/job/PAY/'
        'https://jenkins.bbh.com'                 | '/DevSecOps// PAY /'  | ['DevSecOps', 'PAY'] | 'https://jenkins.bbh.com/job/DevSecOps/job/PAY/'
        'https://jenkins.bbh.com/job/DevSecOps/'  | 'PAY'                 | ['DevSecOps', 'PAY'] | 'https://jenkins.bbh.com/job/DevSecOps/job/PAY/'
    }

    def "an address that is not one of a Jenkins is refused"() {
        when:
        JenkinsJobAddress.ofFolder('jenkinsUrl', 'jenkins.bbh.com', null)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems*.field() == ['jenkinsUrl']
        refused.message == JenkinsJobAddress.NOT_A_JENKINS
    }

    def "a job in a folder has an address that escapes its names and a path that shows them"() {
        when:
        def job = JenkinsJobAddress.ofFolder('jenkinsUrl', 'https://jenkins.bbh.com', 'My Team').child('payments api')

        then:
        job.url() == 'https://jenkins.bbh.com/job/My%20Team/job/payments%20api/'
        job.path() == 'My Team/payments api'
        JenkinsJobAddress.ofJob('jobUrl', job.url()).names() == job.names()
    }
}
