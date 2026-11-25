package com.bbh.itss.coe.oneview.domain.catalog;

import com.bbh.itss.coe.oneview.domain.shared.ConfigTree;
import com.bbh.itss.coe.oneview.domain.shared.ValidationProblems;
import lombok.Builder;

import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.FLUTTER;
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.MAVEN;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * The SonarQube analysis of a service. The SonarScanner version (tools.sonar.sonarScannerVersion) picks the Jenkins tool
 * installation of the sonar-scanner CLI, sonar-scanner-cli-5.0.1.3006 for version 5.0.1.3006, for every run of the CLI
 * (SonarService.runScanner); a .NET service is analysed with the SonarScanner for .NET instead, which does not read it.
 */
@Builder
public record SonarSettings(String projectName, String projectKey, String installationName, String credentialsId,
                            String authTokenCredentialsId, String badgeToken, Boolean addBadges, Boolean fullBadges,
                            ToolCommand command, String serverUrl, String badgeCredentialsId,
                            String sonarScannerVersion) {

    public static final SonarSettings NONE = builder().build();
    /** The characters of a SonarScanner version, such as 5.0.1.3006, which names a Jenkins tool installation. */
    public static final String SCANNER_VERSION = "[0-9A-Za-z._-]{1,50}";

    public SonarSettings {
        projectName = trimToNull(projectName);
        projectKey = trimToNull(projectKey);
        installationName = trimToNull(installationName);
        credentialsId = trimToNull(credentialsId);
        authTokenCredentialsId = trimToNull(authTokenCredentialsId);
        badgeToken = trimToNull(badgeToken);
        addBadges = isTrue(addBadges);
        fullBadges = isTrue(fullBadges);
        command = getIfNull(command, ToolCommand.NONE);
        serverUrl = trimToNull(serverUrl);
        badgeCredentialsId = trimToNull(badgeCredentialsId);
        sonarScannerVersion = trimToNull(sonarScannerVersion);
    }

    public SonarSettings(String projectName, String projectKey, String installationName, String credentialsId,
                         String authTokenCredentialsId, String badgeToken, Boolean addBadges, Boolean fullBadges,
                         ToolCommand command, String serverUrl, String badgeCredentialsId) {
        this(projectName, projectKey, installationName, credentialsId, authTokenCredentialsId, badgeToken, addBadges,
                fullBadges, command, serverUrl, badgeCredentialsId, null);
    }

    public static SonarSettings of(String projectName, String projectKey, ToolCommand command) {
        return builder().projectName(projectName).projectKey(projectKey).command(command).build();
    }

    public void writeTo(ConfigTree config, BuildTool tool) {
        config.set("tools.sonar.serverUrl", serverUrl)
                .set("tools.sonar.projectName", projectName)
                .set("tools.sonar.projectKey", projectKey)
                .set("tools.sonar.installationName", installationName)
                .set("tools.sonar.credentialsId", credentialsId)
                .set("tools.sonar.authToken", authTokenCredentialsId)
                .set("tools.sonar.badgeToken", badgeToken)
                .set("tools.sonar.badgeCredentialsId", badgeCredentialsId)
                .flag("tools.sonar.addBadges", addBadges)
                .flag("tools.sonar.fullBadges", fullBadges);
        command.writeTo(config, "tools.sonar", tool);
        // The Flutter settings write the version of a Flutter service among its other SonarQube keys, where it was.
        if (tool != FLUTTER && !BuildTool.dotnet(tool)) {
            config.set("tools.sonar.sonarScannerVersion", sonarScannerVersion);
        }
    }

    public void validate(ValidationProblems problems, BuildTool tool) {
        if (BuildTool.needsTasks(tool) && (projectKey != null || !command.isEmpty()) && command.tasks().isEmpty()) {
            problems.add("command.tasks", tool == MAVEN
                    ? "add the Maven goals of the analysis, for example sonar:sonar"
                    : "add the Gradle tasks of the analysis, for example sonarqube");
        }
        if (badgeToken != null && badgeCredentialsId != null) {
            problems.add("badgeCredentialsId", "give the badge token or its credentials ID, not both");
        }
        command.validateScripted(problems.at("command"), "tools.sonar", tool);
        command.validate(problems.at("command"));
        if (sonarScannerVersion != null && BuildTool.dotnet(tool)) {
            problems.add("sonarScannerVersion", "must be empty for .NET: the library analyses a .NET service with the"
                    + " SonarScanner for .NET, which does not read it");
        } else if (sonarScannerVersion != null && !sonarScannerVersion.matches(SCANNER_VERSION)) {
            problems.add("sonarScannerVersion", "must be a SonarScanner version such as 5.0.1.3006: at most 50"
                    + " letters, digits, '.', '_' and '-'");
        }
    }
}
