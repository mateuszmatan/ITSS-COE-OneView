package com.bbh.itss.coe.oneview.domain.catalog;

import com.bbh.itss.coe.oneview.domain.shared.ConfigTree;
import com.bbh.itss.coe.oneview.domain.shared.ValidationProblems;
import lombok.Builder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.GRADLE;
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.MAVEN;
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.NPM;
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.PIP;
import static com.bbh.itss.coe.oneview.domain.shared.StoredList.LINES_2000;
import static com.bbh.itss.coe.oneview.domain.shared.Text.trimmed;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * The Nexus delivery of a service on virtual machines beyond its upload command: the files of the upload
 * (delivery.push; Maven goals upload the package of an npm or pip service), the Maven coordinates and repository of a
 * Gradle upload, the download of the extended pipeline (delivery.pull), the release of the files that passed the gate
 * (delivery.release, stage 12) and the NuGet feeds of a .NET service (delivery.nuget). Each artifact is a line of a
 * glob, for the download a file name, optionally followed by its classifier. A release value left empty, and the
 * release switch left unset, come from the Library defaults.
 */
@Builder
public record NexusDeliverySettings(List<String> artifacts, String group, String artifact, String repositoryUrl,
                                    Boolean allowInsecureProtocol, ToolCommand pull, List<String> pullArtifacts,
                                    String pullOutputDirectory, Boolean releaseEnabled, String releaseRepositoryUrl,
                                    String releaseRepositoryId, String releaseCredentialsId, String nugetSource,
                                    String nugetCredentialsId, String nugetReleaseSource,
                                    String nugetReleaseCredentialsId, String nugetPackagePattern) {

    public static final NexusDeliverySettings NONE = builder().build();
    public static final String GRADLE_PUSH_TASK = "publishNexusDeliveryPublicationToNexusRepository";
    public static final String GRADLE_PULL_TASK = "pullNexusArtifacts";
    public static final String DEFAULT_OUTPUT_DIRECTORY = "downloaded-artifacts";

    private static final String COORDINATE = "[A-Za-z0-9_.-]+";
    private static final String DELIVERY_URL = "https?://[^\\s'\"`$\\\\]+";
    private static final String EXTENSION = "[A-Za-z0-9_-]+";
    private static final List<String> RESERVED_GRADLE_FLAGS = List.of("-p", "-b", "-x", "-m", "--dry-run");
    private static final List<String> RESERVED_GRADLE_PREFIXES = List.of("-p=", "--project-dir", "--build-file",
            "-PnexusManifest", "--exclude-task");

    public NexusDeliverySettings {
        artifacts = trimmed(artifacts);
        group = trimToNull(group);
        artifact = trimToNull(artifact);
        repositoryUrl = trimToNull(repositoryUrl);
        allowInsecureProtocol = isTrue(allowInsecureProtocol);
        pull = getIfNull(pull, ToolCommand.NONE);
        pullArtifacts = trimmed(pullArtifacts);
        pullOutputDirectory = trimToNull(pullOutputDirectory);
        releaseRepositoryUrl = trimToNull(releaseRepositoryUrl);
        releaseRepositoryId = trimToNull(releaseRepositoryId);
        releaseCredentialsId = trimToNull(releaseCredentialsId);
        nugetSource = trimToNull(nugetSource);
        nugetCredentialsId = trimToNull(nugetCredentialsId);
        nugetReleaseSource = trimToNull(nugetReleaseSource);
        nugetReleaseCredentialsId = trimToNull(nugetReleaseCredentialsId);
        nugetPackagePattern = trimToNull(nugetPackagePattern);
    }

    /**
     * Whether Maven goals upload the files of the service: those of a Maven service, and the package of an npm or pip
     * service, which the library publishes with Maven.
     */
    public static boolean mavenUpload(BuildTool tool) {
        return tool == MAVEN || tool == NPM || tool == PIP;
    }

    /** Whether stage 12 releases the files of the service with delivery.release: a .NET service pushes to NuGet. */
    public static boolean releases(BuildTool tool) {
        return mavenUpload(tool) || tool == GRADLE;
    }

    /** Whether the extended pipeline downloads the snapshot: the files to download are listed. */
    public boolean pulls() {
        return !pullArtifacts.isEmpty();
    }

    /**
     * Renders the delivery of a service on virtual machines. The upload command is delivery.push.maven with the
     * artifacts of a Maven service, delivery.maven without them, and delivery.push.maven for the package of an npm or
     * pip service that lists it; a Gradle service passes its flags and environment variables to the upload and the
     * download. The release names the Maven server ID of a Maven upload and the credentials of a Gradle upload. The
     * NuGet feeds of a .NET service are written by {@link #writeNugetTo}, as they apply on every deploy target.
     */
    public void writeTo(ConfigTree config, BuildTool tool, ToolCommand upload) {
        if (tool == MAVEN && artifacts.isEmpty()) {
            upload.writeTo(config, "delivery", MAVEN);
        } else if (tool == MAVEN || mavenUpload(tool) && !artifacts.isEmpty()) {
            upload.writeTo(config, "delivery.push", MAVEN);
            config.set("delivery.push.maven.artifacts", entries(artifacts));
        } else if (tool == GRADLE && !artifacts.isEmpty()) {
            config.set("delivery.push.gradle.group", group).set("delivery.push.gradle.artifact", artifact);
            writeGradle(config, "delivery.push.gradle", upload, artifacts);
        }
        if (releases(tool)) {
            config.set("delivery.release.enabled", releaseEnabled)
                    .set("delivery.release.repositoryUrl", releaseRepositoryUrl)
                    .set("delivery.release.repositoryId", tool == GRADLE ? null : releaseRepositoryId)
                    .set("delivery.release.credentialsId", tool == GRADLE ? releaseCredentialsId : null);
        }
        if (pulls() && (tool == MAVEN || tool == GRADLE)) {
            config.set("delivery.pull.outputDirectory", pullOutputDirectory)
                    .set("delivery.pull.group", group)
                    .set("delivery.pull.artifact", artifact);
            if (tool == MAVEN) {
                pull.writeTo(config, "delivery.pull", MAVEN);
                config.set("delivery.pull.maven.artifacts", entries(pullArtifacts));
            } else {
                writeGradle(config, "delivery.pull.gradle", pull, pullArtifacts);
            }
        }
    }

    /**
     * The NuGet feeds of a .NET service, on virtual machines and on OpenShift alike: stage 6 pushes the package to the
     * source on virtual machines, and stage 12 pushes it to the release feed or, on OpenShift and with the Nexus
     * delivery off, where stage 6 pushed none, to the source.
     */
    public void writeNugetTo(ConfigTree config) {
        config.set("delivery.nuget.source", nugetSource)
                .set("delivery.nuget.credentialsId", nugetCredentialsId)
                .set("delivery.nuget.releaseSource", nugetReleaseSource)
                .set("delivery.nuget.releaseCredentialsId", nugetReleaseCredentialsId)
                .set("delivery.nuget.packagePattern", nugetPackagePattern);
    }

    private void writeGradle(ConfigTree config, String block, ToolCommand command, List<String> files) {
        config.set(block + ".repositoryUrl", repositoryUrl)
                .flag(block + ".allowInsecureProtocol", allowInsecureProtocol)
                .set(block + ".flags", command.flags())
                .set(block + ".env", command.environmentMap())
                .set(block + ".artifacts", entries(files));
    }

    /**
     * The rules of the delivery of a service that publishes to Nexus from virtual machines. The upload command is
     * checked under its own path, as the Gradle upload takes only its flags and environment variables.
     */
    public void validate(ValidationProblems problems, ValidationProblems command, BuildTool tool, ToolCommand upload) {
        LINES_2000.check(problems, "artifacts", artifacts);
        LINES_2000.check(problems, "pullArtifacts", pullArtifacts);
        pull.validate(problems.at("pull"));
        checkEntries(problems, "artifacts", artifacts, false);
        checkEntries(problems, "pullArtifacts", pullArtifacts, true);
        if (group != null && !group.matches(COORDINATE)) {
            problems.add("group", "has letters, digits, '.', '_' and '-' only");
        }
        if (artifact != null && !artifact.matches(COORDINATE)) {
            problems.add("artifact", "has letters, digits, '.', '_' and '-' only");
        }
        if (repositoryUrl != null && !repositoryUrl.matches("https?://\\S+")) {
            problems.add("repositoryUrl", "must be an http or https address");
        }
        if (pullOutputDirectory != null && pullOutputDirectory.contains("'")) {
            problems.add("pullOutputDirectory", "must be a folder without quotes");
        }
        validateRelease(problems, tool, upload);
        if (tool == GRADLE) {
            validateGradle(problems, command, upload);
        } else if (tool == MAVEN && pulls()) {
            requireCoordinates(problems, "to download the Maven artifacts");
            problems.at("pull").require("tasks", pull.tasks(), "add the Maven goals that download the snapshot, for"
                    + " example org.apache.maven.plugins:maven-dependency-plugin:3.6.1:get");
        } else if ((tool == NPM || tool == PIP) && !artifacts.isEmpty()) {
            command.require("tasks", upload.tasks(), "add the Maven goals that upload the package, for example"
                    + " deploy:deploy-file");
        }
    }

    /**
     * The release repository is an address the library passes to Maven and Gradle, and the release never goes to the
     * snapshot repository of the upload: the repository of a Gradle upload, the -Durl flag of a Maven upload.
     */
    private void validateRelease(ValidationProblems problems, BuildTool tool, ToolCommand upload) {
        checkAddress(problems, "releaseRepositoryUrl", releaseRepositoryUrl);
        if (releaseRepositoryId != null && !releaseRepositoryId.matches(COORDINATE)) {
            problems.add("releaseRepositoryId", "has letters, digits, '.', '_' and '-' only");
        }
        String snapshot = tool == GRADLE ? repositoryUrl : mavenUpload(tool) ? snapshotUrl(upload.flags()) : null;
        if (releaseRepositoryUrl != null && snapshot != null && sameAddress(releaseRepositoryUrl, snapshot)) {
            problems.add("releaseRepositoryUrl", "is the snapshot repository of the upload: give the release"
                    + " repository, or leave it empty for that of the Library defaults");
        }
    }

    /**
     * The NuGet sources of a .NET service and its package pattern, which NuGet gets in quotes. A source is what
     * {@code dotnet nuget push --source} takes: a feed address, a folder or the name of a source in NuGet.Config.
     */
    public void validateNuget(ValidationProblems problems) {
        if (hasQuote(nugetSource)) {
            problems.add("nugetSource", "must be a NuGet source without quotes");
        }
        if (hasQuote(nugetReleaseSource)) {
            problems.add("nugetReleaseSource", "must be a NuGet source without quotes");
        }
        if (hasQuote(nugetPackagePattern)) {
            problems.add("nugetPackagePattern", "must be a pattern without quotes");
        }
    }

    private static boolean hasQuote(String value) {
        return value != null && (value.contains("'") || value.contains("\""));
    }

    private static void checkAddress(ValidationProblems problems, String field, String address) {
        if (address != null && !address.matches(DELIVERY_URL)) {
            problems.add(field, "must be an http or https address without spaces, quotes, backslashes, $ or backticks");
        }
    }

    private static String snapshotUrl(List<String> flags) {
        return flags.stream().filter(flag -> flag.startsWith("-Durl=")).map(flag -> flag.substring(6)).findFirst()
                .orElse(null);
    }

    private static boolean sameAddress(String one, String other) {
        return one.replaceAll("/+$", "").equalsIgnoreCase(other.replaceAll("/+$", ""));
    }

    private void validateGradle(ValidationProblems problems, ValidationProblems command, ToolCommand upload) {
        if (!artifacts.isEmpty()) {
            requireCoordinates(problems, "to upload the Gradle artifacts");
            problems.require("repositoryUrl", repositoryUrl, "is required to upload the Gradle artifacts");
        }
        if (pulls()) {
            requireCoordinates(problems, "to download the Gradle artifacts");
            problems.require("repositoryUrl", repositoryUrl, "is required to download the Gradle artifacts");
        }
        checkGradleCommand(command, upload, GRADLE_PUSH_TASK);
        checkGradleCommand(problems.at("pull"), pull, GRADLE_PULL_TASK);
    }

    private void requireCoordinates(ValidationProblems problems, String purpose) {
        problems.require("group", group, "is required " + purpose)
                .require("artifact", artifact, "is required " + purpose);
    }

    private static void checkGradleCommand(ValidationProblems problems, ToolCommand command, String task) {
        if (!command.tasks().isEmpty() && !command.tasks().equals(List.of(task))) {
            problems.add("tasks", "the Gradle step of the Nexus delivery runs only the task " + task);
        }
        reservedGradleFlag(command.flags()).ifPresent(flag -> problems.add("flags",
                "the flag " + flag + " is set by the Nexus delivery and cannot be given here"));
    }

    static Optional<String> reservedGradleFlag(List<String> flags) {
        return flags.stream().filter(flag -> RESERVED_GRADLE_FLAGS.contains(flag)
                || RESERVED_GRADLE_PREFIXES.stream().anyMatch(flag::startsWith)).findFirst();
    }

    /**
     * Checks each line: a path (a file name when downloading) with an optional classifier. A downloaded file needs an
     * extension, and two downloads may not share a file name.
     */
    private static void checkEntries(ValidationProblems problems, String field, List<String> lines, boolean names) {
        Set<String> seen = new HashSet<>();
        for (String line : lines) {
            String[] parts = line.split("\\s+");
            String path = parts[0];
            if (parts.length > 2) {
                problems.add(field, "'" + line + "': give a path and at most one classifier, separated by a space");
                return;
            }
            if (path.contains(",") || path.contains("'")) {
                problems.add(field, "'" + path + "' has a comma or a quote, which Maven cannot upload");
                return;
            }
            if (parts.length == 2 && !parts[1].matches(COORDINATE)) {
                problems.add(field, "the classifier '" + parts[1] + "' has letters, digits, '.', '_' and '-' only");
                return;
            }
            if (names) {
                String name = path.substring(path.lastIndexOf('/') + 1);
                int dot = name.lastIndexOf('.');
                if (dot <= 0 || dot == name.length() - 1 || !name.substring(dot + 1).matches(EXTENSION)) {
                    problems.add(field, "'" + name + "' needs a file extension of letters, digits, '_' and '-'");
                    return;
                }
                if (parts.length == 1 && !name.substring(0, dot).matches(COORDINATE)) {
                    problems.add(field, "'" + name + "' is also its classifier, which has letters, digits, '.', '_'"
                            + " and '-' only: add a classifier after the name");
                    return;
                }
                if (!seen.add(name)) {
                    problems.add(field, "two artifacts would be downloaded as " + name);
                    return;
                }
            }
        }
    }

    /** The artifacts as the library reads them: a path and, when the line names one, a classifier. */
    private static List<Map<String, String>> entries(List<String> lines) {
        List<Map<String, String>> entries = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.split("\\s+");
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("path", parts[0]);
            if (parts.length > 1) {
                entry.put("classifier", parts[1]);
            }
            entries.add(entry);
        }
        return entries;
    }
}
