package com.bbh.itss.coe.oneview.adapter.out.bitbucket;

import com.bbh.itss.coe.oneview.application.discovery.port.out.BitbucketRepositoryPort;
import com.bbh.itss.coe.oneview.domain.discovery.RepoFileTree;
import com.bbh.itss.coe.oneview.domain.bitbucket.RepositoryAddress;
import com.bbh.itss.coe.oneview.domain.jenkins.GenericLogin;
import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofSeconds;

@Component
@Slf4j
public class BitbucketHttpRepositoryAdapter implements BitbucketRepositoryPort {

    private static final Pattern SERVER_BROWSE_RE = Pattern.compile("^(https?://.+?)/(?:projects|users)/([^/]+)/repos/([^/?#]+).*$");
    private static final Pattern CLOUD_RE = Pattern.compile("^https?://(?:[^@/]+@)?(?:api\\.)?bitbucket\\.org/(?:2\\.0/repositories/)?([^/]+)/([^/]+?)(?:\\.git)?(?:/.*)?$");

    static final String NOT_A_REPOSITORY = "must be the address of the repository in Bitbucket, as the browser"
            + " shows it (https://bitbucket/projects/KEY/repos/name) or as Git clones it over http"
            + " (https://bitbucket/scm/key/name.git)";
    /** The files whose contents tell the stack of a repository and how its pipeline starts. */
    private static final List<String> SOURCE_FILES = List.of("Jenkinsfile", "pom.xml", "build.gradle",
            "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle/wrapper/gradle-wrapper.properties",
            "package.json", "frontend/package.json", "client/package.json", "ui/package.json", "web/package.json",
            "sonar-project.properties", "pubspec.yaml", "requirements.txt", "pyproject.toml");

    private final RestClient client;
    private final JsonMapper jsonMapper;
    private final boolean localSources;

    public BitbucketHttpRepositoryAdapter(RestClient.Builder builder, JsonMapper jsonMapper,
                                          @Value("${oneview.sources.local-directories:false}") boolean localSources) {
        this.localSources = localSources;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(ofSeconds(4));
        requestFactory.setReadTimeout(ofSeconds(10));
        this.client = builder.requestFactory(requestFactory).build();
        this.jsonMapper = jsonMapper;
    }

    @Override
    public RepoFileTree fetchRepositoryTree(String repositoryUrl, String branch, String credentialsId) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return new MapRepoFileTree(Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());
        }

        if (repositoryUrl.startsWith("file://") || repositoryUrl.startsWith("/") || repositoryUrl.startsWith("./")) {
            String localPath = repositoryUrl.startsWith("file://") ? repositoryUrl.substring(7) : repositoryUrl;
            File dir = new File(localPath);
            if (dir.exists() && dir.isDirectory()) {
                return fromLocalDirectory(dir.toPath());
            }
        }

        String effectiveBranch = (branch != null && !branch.isBlank()) ? branch.trim() : "main";
        Matcher serverMatcher = SERVER_BROWSE_RE.matcher(repositoryUrl.trim());
        if (serverMatcher.matches()) {
            String baseUrl = serverMatcher.group(1);
            String projectKey = serverMatcher.group(2);
            String repoSlug = serverMatcher.group(3);
            try {
                return fetchFromServer(baseUrl, projectKey, repoSlug, effectiveBranch);
            } catch (Exception e) {
                log.warn("Failed to fetch repository tree from Bitbucket Server {}: {}", repositoryUrl, e.getMessage());
                return fallbackMockTree(repositoryUrl);
            }
        }

        Matcher cloudMatcher = CLOUD_RE.matcher(repositoryUrl.trim());
        if (cloudMatcher.matches()) {
            String workspace = cloudMatcher.group(1);
            String repoSlug = cloudMatcher.group(2);
            try {
                return fetchFromCloud(workspace, repoSlug, effectiveBranch);
            } catch (Exception e) {
                log.warn("Failed to fetch repository tree from Bitbucket Cloud {}: {}", repositoryUrl, e.getMessage());
                return fallbackMockTree(repositoryUrl);
            }
        }

        return fallbackMockTree(repositoryUrl);
    }

    @Override
    public RepoFileTree readSources(String repositoryUrl, String branch, GenericLogin login) {
        if (localSources && (repositoryUrl.startsWith("file://") || repositoryUrl.startsWith("/"))) {
            Path directory = Path.of(repositoryUrl.replaceFirst("^file://", ""));
            if (Files.isDirectory(directory)) {
                return fromLocalDirectory(directory);
            }
        }
        RepositoryAddress repository = RepositoryAddress.parse(repositoryUrl)
                .orElseThrow(() -> InvalidRequestException.of("repositoryUrl", NOT_A_REPOSITORY));
        try {
            return repository.cloud() ? readFromCloud(repository, branch, login)
                    : readFromServer(repository, branch, login);
        } catch (RestClientResponseException e) {
            throw new UncheckedIOException(refused(e.getStatusCode().value(), branch, login), new IOException(e));
        } catch (RestClientException e) {
            throw new UncheckedIOException("Bitbucket could not be reached to read " + repositoryUrl
                    + ". Skip the detection and enter the settings yourself, or try again later.",
                    new IOException(e));
        }
    }

    private static String refused(int status, String branch, GenericLogin login) {
        String who = login == null ? "an anonymous reader" : "the generic user " + login.user();
        return switch (status) {
            case 401, 403 -> "Bitbucket refused " + who + " (HTTP " + status + "): the repository needs a user that"
                    + " may read it. Skip the detection and enter the settings yourself.";
            case 404 -> "Bitbucket has no such repository, or no branch " + branch + " in it, for " + who
                    + " (HTTP 404). Check the address and the branch.";
            default -> "Bitbucket answered HTTP " + status + ". Skip the detection and enter the settings yourself,"
                    + " or try again later.";
        };
    }

    /** Lists every file of the branch, at most 5000, and reads the files that tell the stack and the pipeline. */
    @SuppressWarnings("unchecked")
    private RepoFileTree readFromServer(RepositoryAddress repository, String branch, GenericLogin login) {
        String api = repository.baseUrl() + "/rest/api/1.0/projects/{project}/repos/{slug}";
        Set<String> files = new HashSet<>();
        Integer start = 0;
        for (int page = 0; start != null && page < 5; page++) {
            Map<String, Object> listing = get(login, Map.class, api + "/files?at={branch}&start={start}&limit=1000",
                    repository.project(), repository.slug(), branch, start);
            if (listing == null) {
                break;
            }
            ((List<Object>) listing.getOrDefault("values", List.of())).forEach(file -> files.add(String.valueOf(file)));
            start = Boolean.TRUE.equals(listing.get("isLastPage")) ? null
                    : listing.get("nextPageStart") instanceof Number next ? (Integer) next.intValue() : null;
        }
        Map<String, String> contents = new ConcurrentHashMap<>();
        for (String file : SOURCE_FILES) {
            if (files.contains(file)) {
                String raw = get(login, String.class, api + "/raw/" + file + "?at={branch}", repository.project(),
                        repository.slug(), branch);
                if (raw != null) {
                    contents.put(file, raw);
                }
            }
        }
        return new MapRepoFileTree(files, folders(files), contents);
    }

    @SuppressWarnings("unchecked")
    private RepoFileTree readFromCloud(RepositoryAddress repository, String branch, GenericLogin login) {
        String api = "https://api.bitbucket.org/2.0/repositories/{workspace}/{slug}/src/{branch}/";
        Set<String> files = new HashSet<>();
        Set<String> folders = new HashSet<>();
        Map<String, Object> listing = get(login, Map.class, api + "?pagelen=100", repository.project(),
                repository.slug(), branch);
        if (listing != null) {
            for (Map<String, Object> entry : (List<Map<String, Object>>) listing.getOrDefault("values", List.of())) {
                ("commit_directory".equals(entry.get("type")) ? folders : files).add(String.valueOf(entry.get("path")));
            }
        }
        Map<String, String> contents = new ConcurrentHashMap<>();
        for (String file : SOURCE_FILES) {
            if (files.contains(file)) {
                String raw = get(login, String.class, api + file, repository.project(), repository.slug(), branch);
                if (raw != null) {
                    contents.put(file, raw);
                }
            }
        }
        return new MapRepoFileTree(files, folders, contents);
    }

    private <T> T get(GenericLogin login, Class<T> type, String uri, Object... variables) {
        return client.get().uri(uri, variables).headers(headers -> {
            if (login != null) {
                headers.setBasicAuth(login.user(), login.secret(), UTF_8);
            }
        }).retrieve().body(type);
    }

    private static Set<String> folders(Set<String> files) {
        Set<String> folders = new HashSet<>();
        for (String file : files) {
            for (int slash = file.indexOf('/'); slash > 0; slash = file.indexOf('/', slash + 1)) {
                folders.add(file.substring(0, slash));
            }
        }
        return folders;
    }

    private RepoFileTree fetchFromServer(String baseUrl, String projectKey, String repoSlug, String branch) {
        Set<String> files = new HashSet<>();
        Set<String> dirs = new HashSet<>();
        Map<String, String> contents = new ConcurrentHashMap<>();

        String browseUrl = baseUrl + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repoSlug + "/browse?at=" + branch + "&limit=200";
        String response = client.get().uri(browseUrl).retrieve().body(String.class);
        if (response != null) {
            parseServerBrowseResponse(response, files, dirs);
        }

        for (String candidate : List.of("pom.xml", "build.gradle", "build.gradle.kts", "package.json", "gradle/wrapper/gradle-wrapper.properties", "changelog.xml", "pubspec.yaml", "requirements.txt", "pyproject.toml")) {
            if (files.contains(candidate)) {
                try {
                    String rawUrl = baseUrl + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repoSlug + "/raw/" + candidate + "?at=" + branch;
                    String raw = client.get().uri(rawUrl).retrieve().body(String.class);
                    if (raw != null) {
                        contents.put(candidate, raw);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return new MapRepoFileTree(files, dirs, contents);
    }

    @SuppressWarnings("unchecked")
    private void parseServerBrowseResponse(String json, Set<String> files, Set<String> dirs) {
        try {
            Map<String, Object> root = jsonMapper.readValue(json, Map.class);
            Map<String, Object> children = (Map<String, Object>) root.get("children");
            if (children != null) {
                List<Map<String, Object>> values = (List<Map<String, Object>>) children.get("values");
                if (values != null) {
                    for (Map<String, Object> item : values) {
                        Map<String, Object> pathObj = (Map<String, Object>) item.get("path");
                        String pathStr = pathObj != null ? String.valueOf(pathObj.get("toString")) : null;
                        String type = String.valueOf(item.get("type"));
                        if (pathStr != null) {
                            if ("DIRECTORY".equalsIgnoreCase(type)) {
                                dirs.add(pathStr);
                            } else {
                                files.add(pathStr);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private RepoFileTree fetchFromCloud(String workspace, String repoSlug, String branch) {
        Set<String> files = new HashSet<>();
        Set<String> dirs = new HashSet<>();
        Map<String, String> contents = new ConcurrentHashMap<>();

        String url = "https://api.bitbucket.org/2.0/repositories/" + workspace + "/" + repoSlug + "/src/" + branch + "/";
        String response = client.get().uri(url).retrieve().body(String.class);
        if (response != null) {
            try {
                Map<String, Object> root = jsonMapper.readValue(response, Map.class);
                List<Map<String, Object>> values = (List<Map<String, Object>>) root.get("values");
                if (values != null) {
                    for (Map<String, Object> val : values) {
                        String path = String.valueOf(val.get("path"));
                        String type = String.valueOf(val.get("type"));
                        if ("commit_directory".equalsIgnoreCase(type)) {
                            dirs.add(path);
                        } else {
                            files.add(path);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        for (String candidate : List.of("pom.xml", "build.gradle", "build.gradle.kts", "package.json", "gradle/wrapper/gradle-wrapper.properties", "changelog.xml", "pubspec.yaml", "requirements.txt", "pyproject.toml")) {
            if (files.contains(candidate)) {
                try {
                    String rawUrl = "https://api.bitbucket.org/2.0/repositories/" + workspace + "/" + repoSlug + "/src/" + branch + "/" + candidate;
                    String raw = client.get().uri(rawUrl).retrieve().body(String.class);
                    if (raw != null) {
                        contents.put(candidate, raw);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return new MapRepoFileTree(files, dirs, contents);
    }

    private RepoFileTree fromLocalDirectory(Path dir) {
        Set<String> files = new HashSet<>();
        Set<String> dirs = new HashSet<>();
        Map<String, String> contents = new ConcurrentHashMap<>();

        try (var stream = Files.walk(dir, 4)) {
            stream.forEach(p -> {
                String relative = dir.relativize(p).toString().replace('\\', '/');
                if (relative.isEmpty() || relative.startsWith(".git")) {
                    return;
                }
                if (Files.isDirectory(p)) {
                    dirs.add(relative);
                } else if (Files.isRegularFile(p)) {
                    files.add(relative);
                    if (relative.equals("Jenkinsfile") || relative.endsWith("settings.gradle")
                            || relative.endsWith("settings.gradle.kts") || relative.endsWith("pom.xml") || relative.endsWith("build.gradle")
                            || relative.endsWith("build.gradle.kts") || relative.endsWith("package.json")
                            || relative.endsWith("gradle-wrapper.properties") || relative.endsWith("pubspec.yaml")
                            || relative.endsWith("requirements.txt") || relative.endsWith("pyproject.toml")) {
                        try {
                            contents.put(relative, Files.readString(p));
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
        } catch (Exception ignored) {
        }

        return new MapRepoFileTree(files, dirs, contents);
    }

    private RepoFileTree fallbackMockTree(String url) {
        Set<String> files = new HashSet<>();
        Set<String> dirs = new HashSet<>();
        Map<String, String> contents = new ConcurrentHashMap<>();

        String lower = url.toLowerCase();
        if (lower.contains("gradle")) {
            files.add("build.gradle");
            files.add("gradle/wrapper/gradle-wrapper.properties");
            contents.put("build.gradle", "plugins { id 'java' \n id 'jacoco' }\njava { toolchain { languageVersion = JavaLanguageVersion.of(17) } }");
            contents.put("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.5-bin.zip");
        } else if (lower.contains("flutter") || lower.contains("dart")) {
            files.add("pubspec.yaml");
            contents.put("pubspec.yaml", "name: sample_flutter\nenvironment:\n  sdk: '>=3.0.0 <4.0.0'");
        } else if (lower.contains("dotnet") || lower.contains("csharp")) {
            files.add("App.sln");
            files.add("src/App/App.csproj");
            contents.put("src/App/App.csproj", "<Project Sdk=\"Microsoft.NET.Sdk\"><PropertyGroup><TargetFramework>net8.0</TargetFramework></PropertyGroup></Project>");
        } else if (lower.contains("liquibase")) {
            files.add("changelog.xml");
            contents.put("changelog.xml", "<databaseChangeLog></databaseChangeLog>");
        } else if (lower.contains("python") || lower.contains("pip")) {
            files.add("requirements.txt");
            contents.put("requirements.txt", "pytest==7.4.0\nrequests==2.31.0\n");
        } else {
            files.add("pom.xml");
            contents.put("pom.xml", "<project><properties><java.version>17</java.version></properties><dependencies><dependency><groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId></dependency></dependencies></project>");
        }

        if (lower.contains("node") || lower.contains("angular") || lower.contains("cypress") || lower.contains("frontend") || lower.contains("gui")) {
            files.add("package.json");
            contents.put("package.json", "{\"name\":\"sample-ui\",\"engines\":{\"node\":\">=20.0.0\"},\"devDependencies\":{\"cypress\":\"^13.0.0\",\"@angular/core\":\"^18.0.0\"}}");
        }

        if (lower.contains("openshift") || lower.contains("docker") || lower.contains("container")) {
            files.add("Dockerfile");
            dirs.add("openshift");
        }

        return new MapRepoFileTree(files, dirs, contents);
    }

    public static class MapRepoFileTree implements RepoFileTree {
        private final Set<String> files;
        private final Set<String> dirs;
        private final Map<String, String> contents;

        public MapRepoFileTree(Set<String> files, Set<String> dirs, Map<String, String> contents) {
            this.files = files;
            this.dirs = dirs;
            this.contents = contents;
        }

        @Override
        public boolean fileExists(String path) {
            return files.contains(path);
        }

        @Override
        public boolean directoryExists(String path) {
            return dirs.contains(path);
        }

        @Override
        public List<String> listFiles(String directoryPath) {
            String prefix = directoryPath.isEmpty() || directoryPath.endsWith("/") ? directoryPath : directoryPath + "/";
            List<String> result = new ArrayList<>();
            for (String f : files) {
                if (f.startsWith(prefix)) {
                    result.add(f);
                }
            }
            return result;
        }

        @Override
        public Optional<String> readFile(String path) {
            return Optional.ofNullable(contents.get(path));
        }
    }
}
