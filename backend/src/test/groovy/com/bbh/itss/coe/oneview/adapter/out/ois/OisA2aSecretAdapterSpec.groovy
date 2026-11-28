package com.bbh.itss.coe.oneview.adapter.out.ois

import com.bbh.itss.coe.oneview.support.FakeHttpServer
import org.springframework.boot.ssl.DefaultSslBundleRegistry
import org.springframework.boot.ssl.SslBundle
import org.springframework.boot.ssl.SslBundles
import spock.lang.AutoCleanup
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import javax.net.ssl.SSLContext
import java.net.http.HttpClient

import static java.net.http.HttpClient.Builder.NO_PROXY

class OisA2aSecretAdapterSpec extends Specification {

    @AutoCleanup
    FakeHttpServer ois = FakeHttpServer.start()

    /** The adapter with the transport of the test: plain HTTP to the stand-in instead of HTTPS with the certificate. */
    OisA2aSecretAdapter adapter(String apiVersion = 'v4') {
        new OisA2aSecretAdapter(Stub(SslBundles), JsonMapper.builder().build(), 'ois', apiVersion) {
            @Override
            URI credentials(String oisHost) {
                URI.create(super.credentials(oisHost).toString().replaceFirst('^https', 'http'))
            }

            @Override
            HttpClient client() {
                HttpClient.newBuilder().proxy(NO_PROXY).build()
            }
        }
    }

    def "the secret is asked for over the A2A interface with the API key, and never as part of the address"() {
        given:
        ois.on('GET', '/service/a2a/v4/Credentials\\?type=Password', [body: body])

        expect:
        adapter().fetch(ois.host, 'a2a-key') == 'secret-from-ois'
        with(ois.requests.first()) {
            headers.authorization == 'A2A a2a-key'
            headers.accept == 'application/json'
            !target.contains('a2a-key')
        }

        where:
        body << ['"secret-from-ois"', 'secret-from-ois\n']
    }

    def "a secret with characters JSON escapes is read as OIS meant it"() {
        given:
        ois.on('GET', '/service/a2a/v4/Credentials.*', [body: '"p\\"a\\\\ss\\u00e9"'])

        expect:
        adapter().fetch(ois.host, 'a2a-key') == 'p"a\\ssé'
    }

    def "the version of the A2A interface follows the deployment"() {
        given:
        ois.on('GET', '/service/a2a/v3/Credentials.*', [body: '"s"'])

        expect:
        adapter('v3').fetch(ois.host, 'k') == 's'
    }

    def "the address of the secret is https on the OIS host"() {
        expect:
        new OisA2aSecretAdapter(Stub(SslBundles), JsonMapper.builder().build(), 'ois', 'v4')
                .credentials('ois-qc.bbh.com').toString() == 'https://ois-qc.bbh.com/service/a2a/v4/Credentials?type=Password'
    }

    def "an OIS that refuses, or gives nothing, is reported without the API key"() {
        given:
        ois.on('GET', '/service/a2a/v4/Credentials.*', [status: status, body: body])

        when:
        adapter().fetch(ois.host, 'a2a-key')

        then:
        def refused = thrown(UncheckedIOException)
        refused.message.startsWith("OIS at $ois.host did not give the secret of the generic user (HTTP $status).")
        !refused.message.contains('a2a-key')

        where:
        status | body
        401    | '{"Message":"Authorization is required"}'
        200    | ''
    }

    def "an OIS that does not answer is reported as unreachable"() {
        given:
        def gone = ois.host
        ois.close()

        when:
        adapter().fetch(gone, 'a2a-key')

        then:
        def unreachable = thrown(UncheckedIOException)
        unreachable.message.startsWith("OIS could not be reached at $gone")
    }

    def "without its certificate OneView says what its deployment has to configure"() {
        when:
        new OisA2aSecretAdapter(new DefaultSslBundleRegistry(), JsonMapper.builder().build(), 'ois', 'v4')
                .fetch('ois-qc.bbh.com', 'a2a-key')

        then:
        def missing = thrown(UncheckedIOException)
        missing.message == "OneView has no OIS certificate: its deployment has to configure the SSL bundle 'ois'" +
                ' (spring.ssl.bundle) with the certificate and key registered in OIS.'
    }

    def "the certificate of the SSL bundle is what the client signs in with"() {
        given:
        def context = SSLContext.getDefault()
        SslBundle bundle = Stub() {
            createSslContext() >> context
        }
        def registry = new DefaultSslBundleRegistry('ois-qc', bundle)

        expect:
        new OisA2aSecretAdapter(registry, JsonMapper.builder().build(), 'ois-qc', 'v4').client().sslContext().is(context)
    }
}
