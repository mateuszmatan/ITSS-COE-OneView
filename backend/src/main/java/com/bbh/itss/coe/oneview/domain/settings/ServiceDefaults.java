package com.bbh.itss.coe.oneview.domain.settings;

import com.bbh.itss.coe.oneview.domain.catalog.BuildTool;
import com.bbh.itss.coe.oneview.domain.catalog.DeployTarget;
import com.bbh.itss.coe.oneview.domain.shared.ConfigTree;

import static com.bbh.itss.coe.oneview.domain.catalog.BuildSettings.DEFAULT_SOURCE_DIR;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ServiceDefaults(BuildTool buildTool, DeployTarget deployTarget, String sourceDir, Integer testsMaxParallel,
                              String nodeInstallationName, String npmRegistry, String npmrcCredentialsId,
                              Boolean pluginRepositoriesEnabled, String gradlePluginRepository,
                              String mavenPluginRepository, Boolean releaseEnabled, String releaseRepositoryUrl,
                              String releaseRepositoryId, String releaseCredentialsId) {

    /** The Gradle plugin repository of the BBH Nexus, the one of the library defaults. */
    public static final String BBH_GRADLE_PLUGINS = "https://tools.bbh.com/nexus/repository/gradle-plugins/";

    /** The Maven plugin repository of the BBH Nexus, the one of the library defaults. */
    public static final String BBH_MAVEN_PLUGINS = "https://tools.bbh.com/nexus/repository/maven-public/";

    public ServiceDefaults {
        sourceDir = defaultIfBlank(trim(sourceDir), DEFAULT_SOURCE_DIR);
        nodeInstallationName = trimToNull(nodeInstallationName);
        npmRegistry = trimToNull(npmRegistry);
        npmrcCredentialsId = trimToNull(npmrcCredentialsId);
        if (pluginRepositoriesEnabled == null) {
            pluginRepositoriesEnabled = true;
        }
        gradlePluginRepository = trimToNull(gradlePluginRepository);
        mavenPluginRepository = trimToNull(mavenPluginRepository);
        if (releaseEnabled == null) {
            releaseEnabled = true;
        }
        releaseRepositoryUrl = trimToNull(releaseRepositoryUrl);
        releaseRepositoryId = trimToNull(releaseRepositoryId);
        releaseCredentialsId = trimToNull(releaseCredentialsId);
    }

    public void writeTo(ConfigTree defaults) {
        defaults.set("buildTool", buildTool)
                .set("deployTarget", deployTarget)
                .set("sourceDir", sourceDir)
                .set("tests.maxParallel", testsMaxParallel)
                .set("pluginRepositories.enabled", pluginRepositoriesEnabled)
                .set("pluginRepositories.gradle", gradlePluginRepository)
                .set("pluginRepositories.maven", mavenPluginRepository);
        writeNodeTo(defaults);
        writeReleaseTo(defaults);
    }

    /**
     * The release of the safe artifact (stage 12) as the library reads it under delivery.release. Only what differs
     * from the library defaults is written: the switch when it is off, and the repository, its server ID and its
     * credentials when they are set. Without a repository the library releases nothing and the snapshot of stage 6
     * stays the delivered artifact.
     */
    public void writeReleaseTo(ConfigTree defaults) {
        if (!releaseEnabled) {
            defaults.set("delivery.release.enabled", false);
        }
        defaults.set("delivery.release.repositoryUrl", releaseRepositoryUrl)
                .set("delivery.release.repositoryId", releaseRepositoryId)
                .set("delivery.release.credentialsId", releaseCredentialsId);
    }

    /** The Node.js build values a service leaves to the Library defaults, as the library reads them. */
    public void writeNodeTo(ConfigTree config) {
        config.set("build.node.installationName", nodeInstallationName)
                .set("build.node.registry", npmRegistry)
                .set("build.node.npmrcCredentialsId", npmrcCredentialsId);
    }
}
