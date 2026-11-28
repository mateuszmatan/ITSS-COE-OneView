package com.bbh.itss.coe.oneview.adapter.out.ois;

import com.bbh.itss.coe.oneview.application.jenkins.port.out.GenericUserSecretPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static java.time.Duration.ofSeconds;

/**
 * Fetches the secret of the generic user from OIS the way the DevSecOps library fetches its proxy password: over
 * the application-to-application (A2A) interface, signing in with the client certificate of OneView and naming the
 * account with its API key. The certificate is the SSL bundle named by {@code oneview.ois.ssl-bundle}; the secret
 * is returned to the caller and neither stored nor logged.
 */
@Component
@Slf4j
class OisA2aSecretAdapter implements GenericUserSecretPort {

    static final String NO_CERTIFICATE = "OneView has no OIS certificate: its deployment has to configure the SSL"
            + " bundle '%s' (spring.ssl.bundle) with the certificate and key registered in OIS.";

    private final SslBundles bundles;
    private final JsonMapper json;
    private final String bundle;
    private final String apiVersion;

    OisA2aSecretAdapter(SslBundles bundles, JsonMapper json,
                        @Value("${oneview.ois.ssl-bundle:ois}") String bundle,
                        @Value("${oneview.ois.api-version:v4}") String apiVersion) {
        this.bundles = bundles;
        this.json = json;
        this.bundle = bundle;
        this.apiVersion = apiVersion;
    }

    @Override
    public String fetch(String oisHost, String apiKey) {
        HttpRequest request = HttpRequest.newBuilder(credentials(oisHost)).timeout(ofSeconds(20))
                .header("Authorization", "A2A " + apiKey).header("Accept", "application/json").GET().build();
        HttpResponse<String> response;
        try {
            response = client().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("OIS could not be reached at " + oisHost + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException("Waiting for OIS at " + oisHost + " was interrupted", new IOException(e));
        }
        if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
            log.warn("OIS at {} answered HTTP {} to the request for the secret of the generic user", oisHost,
                    response.statusCode());
            throw new UncheckedIOException("OIS at " + oisHost + " did not give the secret of the generic user (HTTP "
                    + response.statusCode() + "). Check the OIS API key and that the certificate of OneView is"
                    + " registered for it.", new IOException("HTTP " + response.statusCode()));
        }
        String body = response.body().trim();
        return body.startsWith("\"") ? json.readValue(body, String.class) : body;
    }

    URI credentials(String oisHost) {
        return URI.create("https://" + oisHost + "/service/a2a/" + apiVersion + "/Credentials?type=Password");
    }

    HttpClient client() {
        try {
            return HttpClient.newBuilder().connectTimeout(ofSeconds(5))
                    .sslContext(bundles.getBundle(bundle).createSslContext()).build();
        } catch (NoSuchSslBundleException e) {
            throw new UncheckedIOException(NO_CERTIFICATE.formatted(bundle), new IOException(e));
        }
    }
}
