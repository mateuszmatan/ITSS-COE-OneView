package com.bbh.itss.coe.oneview.domain.catalog;

import com.bbh.itss.coe.oneview.domain.shared.ConfigTree;
import com.bbh.itss.coe.oneview.domain.shared.ValidationProblems;

import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.FLUTTER;
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.LIQUIBASE;
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.MAVEN;
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.SQL;
import static com.bbh.itss.coe.oneview.domain.catalog.ToolCommand.NONE;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * The build of a service: its tool and folders, the command of the build, and the Liquibase step that the library runs
 * after the build of a Gradle, Android, Maven or .NET service (build.liquibase).
 */
public record BuildSettings(BuildTool tool, String sourceDir, String javaPath, Boolean autoSetup, String buildPath,
                            ToolCommand command, ToolCommand liquibase) {

    public static final String DEFAULT_SOURCE_DIR = ".";
    public static final String SQL_COMMAND = "must be empty for SQL and PL/SQL files without a changelog: the library"
            + " writes the changelog and runs Liquibase itself; choose the build tool Liquibase for commands of your own";
    /** Why a build tool other than Gradle, Android, Maven or .NET has no Liquibase step. */
    public static final String NO_LIQUIBASE_STEP = "the library runs a Liquibase step only after a Gradle, Android,"
            + " Maven or .NET build";

    public BuildSettings {
        sourceDir = defaultIfBlank(trim(sourceDir), DEFAULT_SOURCE_DIR);
        javaPath = trimToNull(javaPath);
        autoSetup = isTrue(autoSetup);
        buildPath = trimToNull(buildPath);
        command = getIfNull(command, NONE);
        liquibase = getIfNull(liquibase, NONE);
    }

    public BuildSettings(BuildTool tool, String sourceDir, String javaPath, Boolean autoSetup, String buildPath,
                         ToolCommand command) {
        this(tool, sourceDir, javaPath, autoSetup, buildPath, command, NONE);
    }

    public void writeTo(ConfigTree config) {
        config.set("buildTool", tool).set("sourceDir", sourceDir).set("javaPath", javaPath)
                .flag("buildToolAutoSetup", autoSetup).set("build.buildPath", buildPath);
        command.writeTo(config, "build", tool);
        if (BuildTool.liquibaseStep(tool)) {
            liquibase.writeTo(config, "build", LIQUIBASE);
        }
    }

    public void validate(ValidationProblems problems) {
        if (tool == FLUTTER) {
            validateFlutter(problems);
        } else if (BuildTool.needsJdk(tool) && javaPath == null && !autoSetup) {
            problems.add("javaPath", "set the JDK path or enable automatic build tool setup, the unit tests stage needs one of them");
        }
        if (BuildTool.needsTasks(tool) && command.tasks().isEmpty()) {
            problems.add("command.tasks", tool == MAVEN
                    ? "add the Maven goals of the build, for example clean verify"
                    : "add the Gradle tasks of the build, for example clean build");
        }
        if (tool == SQL && !command.isEmpty()) {
            problems.add("command.tasks", SQL_COMMAND);
        }
        command.validateScripted(problems.at("command"), "build", tool);
        command.validate(problems.at("command"));
        liquibase.validateLiquibaseStep(problems.at("liquibase"), tool, BuildTool.liquibaseStep(tool),
                NO_LIQUIBASE_STEP);
    }

    private void validateFlutter(ValidationProblems problems) {
        problems.require("javaPath", javaPath,
                "is required for Flutter: the library refuses a Flutter build without it");
        if (autoSetup) {
            problems.add("autoSetup", "must be off for Flutter: the automatic build tool setup cannot prepare a Flutter build");
        }
    }
}
