package com.bbh.itss.coe.oneview.adapter.out.jenkins;

import com.bbh.itss.coe.oneview.application.jenkins.port.out.JenkinsPort;
import com.bbh.itss.coe.oneview.domain.jenkins.GenericLogin;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobAddress;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSnapshot;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSpec;
import com.bbh.itss.coe.oneview.domain.jenkins.LastBuild;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofSeconds;
import static org.springframework.http.MediaType.APPLICATION_XML;

/**
 * Jenkins over its REST API. Everything but {@link #createJob} is a GET that changes nothing. The secret of the
 * login travels only in the Authorization header of each request and is never logged.
 */
@Component
@Slf4j
class JenkinsHttpAdapter implements JenkinsPort {

    private static final String BUILD_TREE = "number,result,actions[parameters[name,value],remoteUrls,"
            + "lastBuiltRevision[branch[name]]]";

    private final RestClient client;

    JenkinsHttpAdapter(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(ofSeconds(5));
        requestFactory.setReadTimeout(ofSeconds(30));
        this.client = builder.requestFactory(requestFactory).build();
    }

    @Override
    public String whoAmI(String jenkinsUrl, GenericLogin login) {
        Map<?, ?> who = call(jenkinsUrl, login, () -> get(jenkinsUrl + "/whoAmI/api/json", login, Map.class));
        if (who == null || !Boolean.TRUE.equals(who.get("authenticated")) || Boolean.TRUE.equals(who.get("anonymous"))) {
            throw new UncheckedIOException(refused(jenkinsUrl, login, 401), new IOException("anonymous"));
        }
        return String.valueOf(who.get("name"));
    }

    @Override
    public JenkinsJobSnapshot readJob(JenkinsJobAddress job, GenericLogin login) {
        String config = call(job.url(), login, () -> get(job.url() + "config.xml", login, String.class));
        JenkinsJobSnapshot.JenkinsJobSnapshotBuilder snapshot = JenkinsConfigXml.read(config == null ? "" : config)
                .jenkinsUrl(job.jenkinsUrl()).jobPath(job.path()).jobUrl(job.url());
        String build = job.buildNumber() == null ? "lastBuild" : job.buildNumber().toString();
        try {
            Map<?, ?> json = get(job.url() + build + "/api/json?tree={tree}", login, Map.class, BUILD_TREE);
            snapshot.lastBuild(lastBuild(json));
        } catch (RestClientException e) {
            log.info("No build read for {}: {}", job.url(), e.getMessage());
        }
        return snapshot.build();
    }

    @SuppressWarnings("unchecked")
    private static LastBuild lastBuild(Map<?, ?> json) {
        if (json == null || !(json.get("number") instanceof Number number)) {
            return null;
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        String branch = null;
        String repositoryUrl = null;
        for (Object entry : json.get("actions") instanceof List<?> actions ? actions : List.of()) {
            if (!(entry instanceof Map<?, ?> action)) {
                continue;
            }
            for (Object parameter : action.get("parameters") instanceof List<?> list ? list : List.of()) {
                if (parameter instanceof Map<?, ?> value && value.get("name") != null) {
                    parameters.put(String.valueOf(value.get("name")),
                            value.get("value") == null ? "" : String.valueOf(value.get("value")));
                }
            }
            if (repositoryUrl == null && action.get("remoteUrls") instanceof List<?> urls && !urls.isEmpty()) {
                repositoryUrl = String.valueOf(urls.get(0));
            }
            if (branch == null && action.get("lastBuiltRevision") instanceof Map<?, ?> revision
                    && revision.get("branch") instanceof List<?> branches && !branches.isEmpty()
                    && branches.get(0) instanceof Map<?, ?> first && first.get("name") != null) {
                branch = String.valueOf(first.get("name")).replaceFirst("^(refs/remotes/)?origin/", "");
            }
        }
        return new LastBuild(number.intValue(), json.get("result") == null ? "RUNNING"
                : String.valueOf(json.get("result")), parameters, branch, repositoryUrl);
    }

    @Override
    public boolean exists(JenkinsJobAddress job, GenericLogin login) {
        return call(job.url(), login, () -> {
            try {
                get(job.url() + "api/json?tree=name", login, Map.class);
                return true;
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() == 404) {
                    return false;
                }
                throw e;
            }
        });
    }

    @Override
    public void createJob(JenkinsJobAddress folder, JenkinsJobSpec job, GenericLogin login) {
        call(folder.url(), login, () -> {
            Crumb crumb = crumb(folder.jenkinsUrl(), login);
            JenkinsJobAddress parent = new JenkinsJobAddress(folder.jenkinsUrl(), List.of(), null);
            for (String name : folder.names()) {
                if (!exists(parent.child(name), login)) {
                    createItem(parent, name, JenkinsConfigXml.FOLDER, login, crumb);
                    log.info("Created the Jenkins folder {} in {} as {}", name, parent.url(), login.user());
                }
                parent = parent.child(name);
            }
            createItem(folder, job.name(), JenkinsConfigXml.write(job), login, crumb);
            return null;
        });
        log.info("Created the Jenkins job {} in {} as {}", job.name(), folder.url(), login.user());
    }

    private void createItem(JenkinsJobAddress parent, String name, String configXml, GenericLogin login,
                            Crumb crumb) {
        client.post().uri(parent.url() + "createItem?name={name}", name)
                .headers(headers -> {
                    headers.setBasicAuth(login.user(), login.secret(), UTF_8);
                    crumb.apply(headers);
                })
                .contentType(APPLICATION_XML).body(configXml.getBytes(UTF_8)).retrieve().toBodilessEntity();
    }

    /** The CSRF crumb of Jenkins with the session it belongs to; none when Jenkins issues no crumbs. */
    private Crumb crumb(String jenkinsUrl, GenericLogin login) {
        try {
            ResponseEntity<Map> response = client.get().uri(jenkinsUrl + "/crumbIssuer/api/json")
                    .headers(headers -> headers.setBasicAuth(login.user(), login.secret(), UTF_8))
                    .retrieve().toEntity(Map.class);
            Map<?, ?> body = response.getBody();
            List<String> cookies = new ArrayList<>();
            for (String cookie : response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE)) {
                cookies.add(cookie.split(";", 2)[0]);
            }
            return body == null ? Crumb.NONE : new Crumb(String.valueOf(body.get("crumbRequestField")),
                    String.valueOf(body.get("crumb")), String.join("; ", cookies));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Crumb.NONE;
            }
            throw e;
        }
    }

    private <T> T get(String uri, GenericLogin login, Class<T> type, Object... variables) {
        return client.get().uri(uri, variables).headers(headers -> {
            if (login != null) {
                headers.setBasicAuth(login.user(), login.secret(), UTF_8);
            }
        }).retrieve().body(type);
    }

    private static <T> T call(String address, GenericLogin login, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String told = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("X-Error");
            throw new UncheckedIOException(told == null ? refused(address, login, status)
                    : "Jenkins refused the request for " + address + " (HTTP " + status + "): " + told,
                    new IOException(e));
        } catch (RestClientException e) {
            throw new UncheckedIOException("Jenkins could not be reached at " + address
                    + ". Check the address, or skip this step and do it yourself.", new IOException(e));
        }
    }

    private static String refused(String address, GenericLogin login, int status) {
        String who = login == null ? "an anonymous reader" : "the generic user " + login.user();
        return switch (status) {
            case 401, 403 -> "Jenkins refused " + who + " at " + address + " (HTTP " + status + "). Reading the"
                    + " configuration of a job needs the Job/ExtendedRead or Job/Configure permission, creating a job"
                    + " or a folder needs Job/Create.";
            case 404 -> "Jenkins has nothing at " + address + " for " + who + " (HTTP 404). Check the address.";
            default -> "Jenkins answered HTTP " + status + " for " + address + ".";
        };
    }

    private record Crumb(String field, String value, String cookies) {

        static final Crumb NONE = new Crumb(null, null, "");

        void apply(HttpHeaders headers) {
            if (field != null) {
                headers.set(field, value);
            }
            if (!cookies.isEmpty()) {
                headers.set(HttpHeaders.COOKIE, cookies);
            }
        }
    }
}
