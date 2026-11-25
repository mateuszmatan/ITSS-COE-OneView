package com.bbh.itss.coe.oneview.domain.catalog;

public enum BuildTool {
    GRADLE, MAVEN, FLUTTER, LIQUIBASE, DOTNET, SQL, NPM, PIP, ANDROID, IOS, MSBUILD;

    public static boolean needsTasks(BuildTool tool) {
        return tool == GRADLE || tool == MAVEN || tool == ANDROID;
    }

    public static boolean needsJdk(BuildTool tool) {
        return tool != LIQUIBASE && !dotnet(tool) && tool != SQL && !scripted(tool);
    }

    /** Whether the build tool can change a database: Liquibase with a changelog, or SQL and PL/SQL files without one. */
    public static boolean database(BuildTool tool) {
        return tool == LIQUIBASE || tool == SQL;
    }

    /**
     * Whether the library runs whole command lines for the build and the unit tests of the tool, and the SonarScanner
     * for its analysis: npm (Angular, Node.js), pip (Python) and iOS.
     */
    public static boolean scripted(BuildTool tool) {
        return tool == NPM || tool == PIP || tool == IOS;
    }

    /**
     * Whether the library builds the service as a .NET service: C# / .NET, whose mode (dotnet, MSBuild, both, Cake or
     * NUKE) it detects on the agent, and MSBuild, which it always builds with NuGet.exe and MSBuild.exe.
     */
    public static boolean dotnet(BuildTool tool) {
        return tool == DOTNET || tool == MSBUILD;
    }

    /**
     * Whether the library runs a Liquibase step of the service after its build and after its unit tests
     * (build.liquibase, tests.unitTests.liquibase): after a Gradle, Android or Maven build and after a .NET build,
     * C# / .NET or MSBuild (BuildService.buildArtifact and unitTests).
     */
    public static boolean liquibaseStep(BuildTool tool) {
        return tool == GRADLE || tool == MAVEN || tool == ANDROID || dotnet(tool);
    }

    /**
     * Whether the AppScan compile runs a Liquibase step after the compile (asoc.liquibase, else build.liquibase): only
     * after the Gradle or Maven compile on a Linux or macOS agent (AppScanService.generateIrx). The compile of an Android
     * service stops before it, and the .NET compile runs none.
     */
    public static boolean liquibaseCompileStep(BuildTool tool) {
        return tool == GRADLE || tool == MAVEN;
    }

    /**
     * The tool whose command blocks the service writes: an Android service builds with the Gradle wrapper, SQL and
     * PL/SQL files with Liquibase, and an MSBuild service writes the .NET blocks (build.dotnet and the others), which
     * the library reads for buildTool msbuild when the service has no msbuild block.
     */
    public static BuildTool commandsOf(BuildTool tool) {
        return tool == ANDROID ? GRADLE : tool == SQL ? LIQUIBASE : tool == MSBUILD ? DOTNET : tool;
    }
}
