package com.bbh.itss.coe.oneview.domain.catalog;

import com.bbh.itss.coe.oneview.domain.shared.ConfigTree;
import com.bbh.itss.coe.oneview.domain.shared.ValidationProblems;
import lombok.Builder;

import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.LIQUIBASE;
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.MAVEN;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.ObjectUtils.anyNotNull;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * The unit tests of a service: their command and reports, the Cypress tests, and the Liquibase step that the library
 * runs after the unit tests of a Gradle, Android, Maven or .NET service (tests.unitTests.liquibase).
 */
@Builder
public record UnitTestSettings(ToolCommand command, String resultPattern, String rootDir, String reportOutDir,
                               Boolean allowEmptyResults, String coverageReportPath,
                               Boolean cypressEnabled, String cypressCommand, String cypressReportsDir,
                               ToolCommand liquibase) {

    public static final UnitTestSettings NONE = builder().build();

    public UnitTestSettings {
        command = getIfNull(command, ToolCommand.NONE);
        resultPattern = trimToNull(resultPattern);
        rootDir = trimToNull(rootDir);
        reportOutDir = trimToNull(reportOutDir);
        allowEmptyResults = isTrue(allowEmptyResults);
        coverageReportPath = trimToNull(coverageReportPath);
        cypressEnabled = isTrue(cypressEnabled);
        cypressCommand = trimToNull(cypressCommand);
        cypressReportsDir = trimToNull(cypressReportsDir);
        liquibase = getIfNull(liquibase, ToolCommand.NONE);
    }

    public UnitTestSettings(ToolCommand command, String resultPattern, String rootDir, String reportOutDir,
                            Boolean allowEmptyResults, String coverageReportPath) {
        this(command, resultPattern, rootDir, reportOutDir, allowEmptyResults, coverageReportPath, false, null, null);
    }

    public UnitTestSettings(ToolCommand command, String resultPattern, String rootDir, String reportOutDir,
                            Boolean allowEmptyResults, String coverageReportPath,
                            Boolean cypressEnabled, String cypressCommand, String cypressReportsDir) {
        this(command, resultPattern, rootDir, reportOutDir, allowEmptyResults, coverageReportPath, cypressEnabled,
                cypressCommand, cypressReportsDir, ToolCommand.NONE);
    }

    public void writeTo(ConfigTree config, BuildTool tool) {
        command.writeTo(config, "tests.unitTests", tool);
        if (BuildTool.liquibaseStep(tool)) {
            liquibase.writeTo(config, "tests.unitTests", LIQUIBASE);
        }
        config.set("tests.unitTests.unitTestResult", resultPattern)
                .set("tests.unitTests.rootDir", rootDir)
                .set("tests.unitTests.reportOutDir", reportOutDir)
                .flag("tests.unitTests.allowEmptyResults", allowEmptyResults)
                .set("coverage.reportPath", coverageReportPath);
        if (isTrue(cypressEnabled)) {
            config.set("tests.unitTests.cypress.enabled", true)
                    .set("tests.unitTests.cypress.command", cypressCommand)
                    .set("tests.unitTests.cypress.reportsDir", cypressReportsDir);
        }
    }

    /**
     * The rules of the unit tests. A Gradle, Android or Maven service whose unit tests are configured, a Liquibase step
     * among them, needs its own test tasks or goals: the library runs the unit tests whenever tests.unitTests is set,
     * and a run without tasks or goals fails, which turns the Unit tests stage orange (BuildRunner.run).
     */
    public void validate(ValidationProblems problems, BuildTool tool) {
        boolean configured = !command.isEmpty() || anyNotNull(resultPattern, rootDir, reportOutDir) || allowEmptyResults
                || isTrue(cypressEnabled) || !liquibase.isEmpty();
        if (BuildTool.needsTasks(tool) && configured && !isTrue(cypressEnabled) && command.tasks().isEmpty()) {
            problems.add("command.tasks", tool == MAVEN
                    ? "add the Maven goals of the unit tests, for example test jacoco:report"
                    : "add the Gradle tasks of the unit tests, for example test jacocoTestReport");
        }
        if (tool == BuildTool.SQL && !command.isEmpty()) {
            problems.add("command.tasks", BuildSettings.SQL_COMMAND);
        }
        command.validateScripted(problems.at("command"), "tests.unitTests", tool);
        command.validate(problems.at("command"));
        liquibase.validateLiquibaseStep(problems.at("liquibase"), tool, BuildTool.liquibaseStep(tool),
                "the library runs a Liquibase step only after the unit tests of a Gradle, Android, Maven or .NET"
                        + " service");
    }
}
