package com.bbh.itss.coe.oneview.adapter.out.bitbucket

import com.bbh.itss.coe.oneview.domain.jenkins.GenericLogin
import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException
import com.bbh.itss.coe.oneview.support.FakeHttpServer
import org.springframework.web.client.RestClient
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.TempDir
import tools.jackson.databind.json.JsonMapper

import java.nio.file.Path

class BitbucketSourcesSpec extends Specification {

    static final GenericLogin LOGIN = new GenericLogin('svc-oneview', 'secret-from-ois')
    static final String API = '/rest/api/1.0/projects/PAY/repos/payments-api'

    @AutoCleanup
    FakeHttpServer bitbucket = FakeHttpServer.start()

    @TempDir
    Path directory

    def adapter = new BitbucketHttpRepositoryAdapter(RestClient.builder(), JsonMapper.builder().build(), false)

    String getRepository() {
        "$bitbucket.url/projects/PAY/repos/payments-api/browse"
    }

    def "the files of the branch are listed page by page and the files that tell the stack are read"() {
        given:
        bitbucket.on('GET', "$API/files\\?at=develop&start=0&limit=1000",
                [body: '{"values":["pom.xml","Jenkinsfile","src/main/java/App.java"],"isLastPage":false,"nextPageStart":3}'])
        bitbucket.on('GET', "$API/files\\?at=develop&start=3&limit=1000",
                [body: '{"values":["openshift/deployment.yaml"],"isLastPage":true}'])
        bitbucket.on('GET', "$API/raw/pom.xml\\?at=develop", [body: '<project/>', contentType: 'text/plain'])
        bitbucket.on('GET', "$API/raw/Jenkinsfile\\?at=develop", [body: 'devSecOpsPipeline()', contentType: 'text/plain'])

        when:
        def sources = adapter.readSources(repository, 'develop', LOGIN)

        then:
        sources.fileExists('src/main/java/App.java')
        sources.fileExists('openshift/deployment.yaml')
        sources.directoryExists('src/main')
        sources.directoryExists('openshift')
        sources.readFile('pom.xml') == Optional.of('<project/>')
        sources.readFile('Jenkinsfile') == Optional.of('devSecOpsPipeline()')
        sources.readFile('src/main/java/App.java') == Optional.empty()
        bitbucket.requests.size() == 4
        bitbucket.requests*.method.unique() == ['GET']
        bitbucket.requests*.headers*.authorization.unique() == ['Basic ' + 'svc-oneview:secret-from-ois'.bytes.encodeBase64()]
    }

    def "without a login the repository is read anonymously, from its clone address as well"() {
        given:
        bitbucket.on('GET', '/rest/api/1.0/projects/pay/repos/payments-api/files.*', [body: '{"values":["build.gradle"],"isLastPage":true}'])
        bitbucket.on('GET', '/rest/api/1.0/projects/pay/repos/payments-api/raw/build.gradle.*', [body: "plugins { id 'java' }"])

        when:
        def sources = adapter.readSources("$bitbucket.url/scm/pay/payments-api.git", 'main', null)

        then:
        sources.readFile('build.gradle') == Optional.of("plugins { id 'java' }")
        bitbucket.requests*.headers*.authorization.unique() == [null]
    }

    def "a repository that cannot be read says why, and nothing is made up in its place"() {
        given:
        bitbucket.on('GET', "$API/files.*", [status: status])

        when:
        adapter.readSources(repository, 'main', login)

        then:
        def refused = thrown(UncheckedIOException)
        refused.message.startsWith(message)

        where:
        status | login | message
        401    | null  | 'Bitbucket refused an anonymous reader (HTTP 401)'
        403    | LOGIN | 'Bitbucket refused the generic user svc-oneview (HTTP 403)'
        404    | LOGIN | 'Bitbucket has no such repository, or no branch main in it, for the generic user svc-oneview'
        500    | LOGIN | 'Bitbucket answered HTTP 500.'
    }

    def "a Bitbucket that does not answer is reported as unreachable"() {
        given:
        def gone = repository
        bitbucket.close()

        when:
        adapter.readSources(gone, 'main', LOGIN)

        then:
        def unreachable = thrown(UncheckedIOException)
        unreachable.message.startsWith("Bitbucket could not be reached to read $gone.")
    }

    def "what is not the address of a Bitbucket repository is refused, a local folder as well"() {
        given:
        directory.resolve('pom.xml').toFile().text = '<project/>'

        when:
        adapter.readSources(url ?: directory.toString(), 'main', null)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems*.field() == ['repositoryUrl']
        bitbucket.requests.empty

        where:
        url << ['https://github.com/bbh/payments', 'payments-api', null]
    }

    def "a local folder is read only where the deployment allows it"() {
        given:
        directory.resolve('pom.xml').toFile().text = '<project/>'
        def local = new BitbucketHttpRepositoryAdapter(RestClient.builder(), JsonMapper.builder().build(), true)

        expect:
        local.readSources(directory.toString(), 'main', null).readFile('pom.xml') == Optional.of('<project/>')
        local.readSources("file://$directory", 'main', null).fileExists('pom.xml')
    }
}
