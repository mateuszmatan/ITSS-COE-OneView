package com.bbh.itss.coe.oneview.adapter.out.jenkins

import com.bbh.itss.coe.oneview.domain.jenkins.GenericLogin
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobAddress
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSpec
import com.bbh.itss.coe.oneview.support.FakeHttpServer
import org.springframework.web.client.RestClient
import spock.lang.AutoCleanup
import spock.lang.Specification

import static com.bbh.itss.coe.oneview.domain.jenkins.JobKind.PIPELINE

class JenkinsHttpAdapterSpec extends Specification {

    static final GenericLogin LOGIN = new GenericLogin('svc-oneview', 'secret-from-ois')
    static final String BASIC = 'Basic ' + 'svc-oneview:secret-from-ois'.bytes.encodeBase64()
    static final String CONFIG = '<flow-definition><description>Payments</description></flow-definition>'

    @AutoCleanup
    FakeHttpServer jenkins = FakeHttpServer.start()

    def adapter = new JenkinsHttpAdapter(RestClient.builder())
    def spec = JenkinsJobSpec.builder().kind(PIPELINE).name('payments api')
            .repositoryUrl('https://bitbucket.bbh.com/scm/pay/payments-api.git').build()

    JenkinsJobAddress job(String... names) {
        new JenkinsJobAddress(jenkins.url, names as List, null)
    }

    def "Jenkins tells the name of the generic user it signed in"() {
        given:
        jenkins.on('GET', '/whoAmI/api/json', [body: '{"name":"OneView service","authenticated":true,"anonymous":false}'])

        expect:
        adapter.whoAmI(jenkins.url, LOGIN) == 'OneView service'
        jenkins.requests.first().headers.authorization == BASIC
    }

    def "a Jenkins that sees only an anonymous reader refuses the generic user"() {
        given:
        jenkins.on('GET', '/whoAmI/api/json', [body: '{"name":"anonymous","authenticated":false,"anonymous":true}'])

        when:
        adapter.whoAmI(jenkins.url, LOGIN)

        then:
        def refused = thrown(UncheckedIOException)
        refused.message.startsWith("Jenkins refused the generic user svc-oneview at $jenkins.url (HTTP 401).")
        !refused.message.contains('secret-from-ois')
    }

    def "a job is read with its last build, by GET requests only"() {
        given:
        jenkins.on('GET', '/job/legacy/job/payments/config.xml', [body: CONFIG, contentType: 'application/xml'])
        jenkins.on('GET', '/job/legacy/job/payments/lastBuild/api/json\\?tree=.*', [body: '''{"number":42,"result":"SUCCESS","actions":[
            {"parameters":[{"name":"TARGET","value":"qc"},{"name":"DRY_RUN","value":false},{"name":"TOKEN"}]},
            {"remoteUrls":["https://bitbucket.bbh.com/scm/pay/payments-api.git"],
             "lastBuiltRevision":{"branch":[{"name":"refs/remotes/origin/develop"}]}}, {}, null]}'''])

        when:
        def read = adapter.readJob(job('legacy', 'payments'), LOGIN)

        then:
        read.jenkinsUrl() == jenkins.url
        read.jobPath() == 'legacy/payments'
        read.jobUrl() == "$jenkins.url/job/legacy/job/payments/"
        read.kind() == PIPELINE
        read.description() == 'Payments'
        with(read.lastBuild()) {
            number() == 42
            result() == 'SUCCESS'
            parameters() == [TARGET: 'qc', DRY_RUN: 'false', TOKEN: '']
            branch() == 'develop'
            repositoryUrl() == 'https://bitbucket.bbh.com/scm/pay/payments-api.git'
        }
        jenkins.requests*.method.unique() == ['GET']
        jenkins.requests*.headers*.authorization.unique() == [BASIC]
    }

    def "the build the address names is read, a running build has no result yet, and without a login the read is anonymous"() {
        given:
        jenkins.on('GET', '/job/payments/config.xml', [body: CONFIG])
        jenkins.on('GET', '/job/payments/7/api/json.*', [body: '{"number":7,"result":null,"actions":[]}'])

        when:
        def read = adapter.readJob(new JenkinsJobAddress(jenkins.url, ['payments'], 7), null)

        then:
        read.lastBuild().number() == 7
        read.lastBuild().result() == 'RUNNING'
        jenkins.requests*.headers*.authorization.unique() == [null]
    }

    def "a job that never ran is read without a build"() {
        given:
        jenkins.on('GET', '/job/payments/config.xml', [body: CONFIG])

        expect:
        adapter.readJob(job('payments'), LOGIN).lastBuild() == null
    }

    def "a job that cannot be read says why"() {
        given:
        jenkins.on('GET', '/job/secret/config.xml', [status: 403])

        when:
        adapter.readJob(job(name), login)

        then:
        def refused = thrown(UncheckedIOException)
        refused.message.startsWith(message)

        where:
        name      | login | message
        'secret'  | LOGIN | 'Jenkins refused the generic user svc-oneview at'
        'secret'  | null  | 'Jenkins refused an anonymous reader at'
        'missing' | LOGIN | 'Jenkins has nothing at'
    }

    def "a Jenkins that does not answer is reported as unreachable"() {
        given:
        def gone = jenkins.url
        jenkins.close()

        when:
        adapter.exists(new JenkinsJobAddress(gone, ['payments'], null), LOGIN)

        then:
        def unreachable = thrown(UncheckedIOException)
        unreachable.message.startsWith("Jenkins could not be reached at $gone/job/payments/.")
    }

    def "a job exists when Jenkins knows its name"() {
        given:
        jenkins.on('GET', '/job/payments/api/json\\?tree=name', [body: '{"name":"payments"}'])

        expect:
        adapter.exists(job('payments'), LOGIN)
        !adapter.exists(job('other'), LOGIN)
    }

    def "a job is created with the crumb and its session, after the folders that are missing"() {
        given:
        jenkins.on('GET', '/crumbIssuer/api/json', [body: '{"crumbRequestField":"Jenkins-Crumb","crumb":"c-123"}',
                                                    headers: ['Set-Cookie': 'JSESSIONID.1=abc; Path=/; HttpOnly']])
        jenkins.on('GET', '/job/DevSecOps/api/json\\?tree=name', [body: '{"name":"DevSecOps"}'])
        jenkins.on('POST', '.*/createItem\\?name=.*', [status: 200])

        when:
        adapter.createJob(job('DevSecOps', 'PAY'), spec, LOGIN)

        then:
        def posts = jenkins.requests.findAll { it.method == 'POST' }
        posts*.target == ['/job/DevSecOps/createItem?name=PAY', '/job/DevSecOps/job/PAY/createItem?name=payments%20api']
        posts.every {
            it.headers.authorization == BASIC && it.headers.'jenkins-crumb' == 'c-123' &&
                    it.headers.cookie == 'JSESSIONID.1=abc' && it.headers.'content-type'.startsWith('application/xml')
        }
        posts[0].body == JenkinsConfigXml.FOLDER
        posts[1].body == JenkinsConfigXml.write(spec)
    }

    def "a Jenkins without crumbs gets the job without one, directly below Jenkins"() {
        given:
        jenkins.on('POST', '/createItem\\?name=.*', [status: 200])

        when:
        adapter.createJob(job(), spec, LOGIN)

        then:
        def post = jenkins.requests.find { it.method == 'POST' }
        post.target == '/createItem?name=payments%20api'
        !post.headers.containsKey('jenkins-crumb')
        !post.headers.containsKey('cookie')
    }

    def "what Jenkins says against a new job reaches the user"() {
        given:
        jenkins.on('POST', '/createItem\\?name=.*', [status: 400, headers: ['X-Error': 'A job already exists with the name payments api']])

        when:
        adapter.createJob(job(), spec, LOGIN)

        then:
        def refused = thrown(UncheckedIOException)
        refused.message == "Jenkins refused the request for $jenkins.url/ (HTTP 400): A job already exists with the name payments api"
    }
}
