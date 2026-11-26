package com.bbh.itss.coe.oneview.adapter.in.web;

import com.bbh.itss.coe.oneview.adapter.in.web.ConfigImportResponse.ImportedPipeline;
import com.bbh.itss.coe.oneview.adapter.in.web.ConfigImportResponse.ImportedService;
import com.bbh.itss.coe.oneview.domain.catalog.BitbucketAuthType;
import com.bbh.itss.coe.oneview.domain.catalog.BitbucketType;
import com.bbh.itss.coe.oneview.domain.catalog.BuildTool;
import com.bbh.itss.coe.oneview.domain.catalog.DatabaseTarget;
import com.bbh.itss.coe.oneview.domain.catalog.DeployTarget;
import com.bbh.itss.coe.oneview.domain.catalog.FlutterPlatform;
import com.bbh.itss.coe.oneview.domain.catalog.NexusDeliverySettings;
import com.bbh.itss.coe.oneview.domain.catalog.OracleUrl;
import com.bbh.itss.coe.oneview.domain.catalog.Region;
import com.bbh.itss.coe.oneview.domain.catalog.Service;
import com.bbh.itss.coe.oneview.domain.catalog.TestJobType;
import com.bbh.itss.coe.oneview.domain.catalog.TestStage;
import com.bbh.itss.coe.oneview.domain.pipeline.PipelineType;
import com.bbh.itss.coe.oneview.domain.settings.GlobalSettingsValues;
import com.bbh.itss.coe.oneview.domain.shared.ConfigTree;
import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException;
import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException.FieldProblem;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.bbh.itss.coe.oneview.adapter.in.web.ConfigFileParser.invalid;
import static com.bbh.itss.coe.oneview.adapter.in.web.ConfigInput.SECRET;
import static com.bbh.itss.coe.oneview.adapter.in.web.ConfigInput.UNKNOWN;
import static com.bbh.itss.coe.oneview.adapter.in.web.InputFormats.REPOSITORY_FILE;
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.DOTNET;
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.FLUTTER;
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.GRADLE;
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.LIQUIBASE;
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.SQL;
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.MAVEN;
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.MSBUILD;
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.NPM;
import static com.bbh.itss.coe.oneview.domain.catalog.BuildTool.PIP;
import static com.bbh.itss.coe.oneview.domain.catalog.DeployTarget.VM;
import static com.bbh.itss.coe.oneview.domain.catalog.NexusIqSettings.ONE_APPLICATION;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static com.bbh.itss.coe.oneview.adapter.in.web.ConfigInput.valueAt;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Reads a devsecops_config.yaml or config.yaml, a rendered product or pipeline config, or their JSON form into new
 * service forms: the inverse of the services' writeTo, as a table from config keys to request fields. A value the
 * portal writes into every project from the global settings, or one equal to the Library default the project inherits
 * (tests.maxParallel, GoldenFix, the Node.js installation and npm settings) or to the library's own default of a
 * Node.js build setting, comes back unset, so the service keeps inheriting it. A value the portal cannot hold becomes a
 * note, not an error, so a partial import still fills the form.
 */
final class ServiceConfigReader {

    static final String GLOBAL = "not imported: a global setting, the same for every service";
    static final String PIPELINE = "not imported: a pipeline setting, chosen when the pipeline is created";
    static final String APPSCAN_KEY = "not imported: the AppScan on Cloud API key of the functional user is set once in the defaults of the library";
    static final String RUN_TIME = "check: a run that builds the image replaces it";
    static final String NODE_WITH_FLUTTER = "check: the Node.js build is not used with Flutter";
    static final String NODE_WITH_DATABASE = "check: the Node.js build is not used with database changes";
    static final String LONG_PATHS = "not imported: longer than the 500 characters the portal keeps; name fewer"
            + " reports, or one pattern such as **/build/reports/jacoco/test/*.xml";
    static final String NO_PROJECT = "found no project: expected a projects map or the settings of one project";
    static final String NOT_FOR_DATABASE = "not imported: a Liquibase service with database changes has no deployment"
            + " of its own";
    static final String NOT_A_DATABASE = "not imported: only a Liquibase service with a database.changelogFile has"
            + " database settings";
    static final String NO_PRODUCTION = "not imported: the pipelines never deploy to production";
    static final String SECRET_IN_URL = "not imported: the URL holds a user or a password; the portal keeps only the"
            + " ID of the Jenkins credentials";
    static final String TOOLS_IN_DEFAULTS = "not imported: the portal sets the tools of Liquibase database changes for"
            + " every service in the Library defaults";
    static final String NO_LIQUIBASE_DELIVERY = "not imported: no stage of the library reads delivery.liquibase; a"
            + " Liquibase service with database changes delivers its package to Nexus with database.package, and any"
            + " other Liquibase service delivers what its Deployment section sets: its image on OpenShift, nothing on"
            + " virtual machines";
    static final String NO_LIQUIBASE_COMPILE_STEP = "not imported: the library runs asoc.liquibase only after a"
            + " Gradle, Android or Maven compile of the AppScan scan";
    static final String NO_LIQUIBASE_SONAR = "not imported: the library adds tools.sonar.liquibase only to the SonarQube"
            + " tasks or goals of a Gradle, Android or Maven service";
    static final String LIQUIBASE_SONAR_WITHOUT_TASKS = "not imported: the library adds tools.sonar.liquibase only to"
            + " the SonarQube tasks or goals of the service, and it has none, so the sonar-scanner CLI runs without it";
    static final String LIQUIBASE_SONAR_KEYS = "not imported: for the SonarQube tasks or goals of a Gradle, Android or"
            + " Maven service the library reads only the sources, inclusions, exclusions and flags of"
            + " tools.sonar.liquibase";
    static final String SKIPPED_LIQUIBASE_STEP = "not imported: without commands, tasks or flags the library skips the"
            + " Liquibase step";
    static final String LIQUIBASE_COMPILE_STEP_OFF = "not imported: without commands, tasks or flags this block keeps"
            + " the AppScan compile from running build.liquibase, which the portal cannot do; after the import the"
            + " compile on a Linux or macOS agent runs build.liquibase";
    static final String SCANNER_FOR_DOTNET = "not imported: a .NET service is analysed with the SonarScanner for .NET,"
            + " which does not read it; the library reads it only with tools.sonar.useGenericScanner, which the portal"
            + " does not set";
    static final int MAX_PROJECTS = 50;

    private static final List<String> DOCUMENT_KEYS = List.of("pipeline", "platform", "defaults");

    private static final Conversion TEXT = ServiceConfigReader::text;
    private static final Conversion FLAG = value -> switch (text(value).trim().toLowerCase(ROOT)) {
        case "true" -> true;
        case "false" -> false;
        default -> throw expected("true or false");
    };
    private static final Conversion NUMBER = value -> {
        try {
            return new BigDecimal(text(value).trim()).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw expected("a whole number");
        }
    };
    private static final Conversion LIST = value -> items(value, null);
    private static final Conversion FILES = value -> {
        List<String> files = items(value, null);
        files.stream().filter(file -> !file.trim().matches(REPOSITORY_FILE)).findFirst().ifPresent(folder -> {
            throw new NotImported("not imported: " + folder + " is a folder; name the file in it, such as"
                    + " platform/pom.xml");
        });
        return files;
    };
    private static final Conversion COMMAS = value -> items(value, ",");
    private static final Conversion PATHS = value -> {
        String paths = String.join("\n", items(value, null));
        if (paths.length() > 500) {
            throw new NotImported(LONG_PATHS);
        }
        return paths;
    };
    private static final Conversion WORDS = value -> items(value, "\\s+");
    private static final Conversion ENV = value -> {
        if (value instanceof Map<?, ?> variables) {
            return pairs(variables);
        }
        throw expected("a map of variables");
    };
    private static final Conversion ARTIFACTS = value -> {
        List<String> lines = new ArrayList<>();
        for (Object entry : value instanceof Collection<?> entries ? entries : List.of(value)) {
            Object path = entry instanceof Map<?, ?> map ? map.get("path") : entry;
            Object classifier = entry instanceof Map<?, ?> map ? map.get("classifier") : null;
            if (path == null || isBlank(text(path)) || text(path).trim().contains(" ")) {
                throw expected("a list of paths without spaces, each a text or a map with its path and classifier");
            }
            lines.add(text(path).trim() + (classifier == null || isBlank(text(classifier)) ? ""
                    : " " + text(classifier).trim()));
        }
        return lines;
    };
    private static final Conversion MAP = value -> {
        if (value instanceof Map<?, ?> parameters) {
            return pairs(parameters);
        }
        throw expected("a map of names to values");
    };
    private static final Conversion JDBC = value -> {
        String url = text(value);
        if (OracleUrl.problemOf(url.trim()).filter(OracleUrl.Problem.SECRET::equals).isPresent()) {
            throw new NotImported(SECRET_IN_URL);
        }
        return url;
    };
    private static final Conversion PARAMETERS = value ->
            String.join("\n", value instanceof Map<?, ?> parameters ? pairs(parameters) : items(value, null));
    private static final Conversion DISABLED = value -> !(Boolean) FLAG.apply(value);
    private static final Conversion IGNORED = value -> null;
    private static final Conversion BITBUCKET_AUTH = choice(BitbucketAuthType.values(), Enum::name);
    private static final Conversion AUTH_TYPE = value ->
            BITBUCKET_AUTH.apply("token".equalsIgnoreCase(text(value).trim()) ? "bearer" : value);
    private static final Conversion CREDENTIALS_AUTH = value -> {
        if (value instanceof Map<?, ?> auth && "CredentialsAuth".equals(auth.get("$class"))
                && auth.get("credentials") != null) {
            return text(auth.get("credentials"));
        }
        throw expected("CredentialsAuth with its credentials ID");
    };

    private static final Set<String> SQL_NAMES = Set.of("plsql", "pl/sql", "pl-sql");
    /** The other names the library accepts for a build tool (BuildUtils.normalizeToolName). */
    private static final Map<String, String> TOOL_NAMES = toolNames();
    private static final Conversion BUILD_TOOL = value -> choice(BuildTool.values(), Enum::name)
            .apply(SQL_NAMES.contains(text(value).trim().toLowerCase(ROOT)) ? "sql"
                    : TOOL_NAMES.getOrDefault(text(value).trim().toLowerCase(ROOT), text(value)));
    private static final Conversion COMMAND_LINES = value -> List.of(items(value, null).stream().map(String::trim)
            .filter(line -> !line.isEmpty()).collect(java.util.stream.Collectors.joining(" && ")).split("\\s+"));
    private static final String SQL_WITH_CHANGELOG = "imported as Liquibase: buildTool sql is for SQL and PL/SQL files"
            + " without a changelog, and the library runs this service as buildTool liquibase because %s is set";

    private static final List<Rule> DEFAULTED = List.of(
            rule("buildTool", "build.tool", BUILD_TOOL),
            rule("deployTarget", "deployment.target", choice(DeployTarget.values(), Enum::name)),
            rule("sourceDir", "build.sourceDir", TEXT));
    private static final List<Rule> PROJECT = projectRules();
    private static final Set<String> PROJECT_KEYS = Stream.concat(DEFAULTED.stream(), PROJECT.stream())
            .map(rule -> rule.key().split("\\.")[0]).collect(toSet());
    private static final List<Rule> DATABASE = databaseRules();
    private static final List<String> UNUSED_BY_DATABASE = List.of("deployTarget", "appName", "artifactName",
            "baseArtifactName", "deploy", "delivery");
    private static final List<Rule> DATABASE_PROJECT = PROJECT.stream()
            .filter(rule -> UNUSED_BY_DATABASE.stream().noneMatch(key -> (rule.key() + ".").startsWith(key + ".")))
            .toList();
    private static final String MAVEN_HOME = "build.maven.mvnPath";
    private static final String LIQUIBASE_DELIVERY = "delivery.liquibase";
    private static final String LIQUIBASE_SONAR = "tools.sonar.liquibase";
    private static final String SCANNER_VERSION = "tools.sonar.sonarScannerVersion";
    /** The keys of tools.sonar.liquibase that the library adds to the SonarQube flags of a Gradle or Maven analysis. */
    private static final List<String> LIQUIBASE_SONAR_PROPERTIES = List.of("sources", "inclusions", "exclusions");
    private static final String DELIVERY = "delivery.nexusDeliveryEnabled";
    private static final String LEGACY_DELIVERY = "deploy.%s.rd.nexusDeliveryEnabled";
    /** The tools of the command blocks, such as build.gradle, that the import reads or leaves out with a note. */
    private static final List<BuildTool> BLOCK_TOOLS = List.of(GRADLE, MAVEN, LIQUIBASE, DOTNET, MSBUILD);
    private static final String DOTNET_BLOCK = "imported as %s.dotnet: the library reads %s.dotnet for buildTool"
            + " dotnet and msbuild alike";
    private static final List<Block> COMMAND_BLOCKS = List.of(new Block("build", "build.command"),
            new Block("tests.unitTests", "unitTests.command"), new Block("tools.sonar", "sonar.command"),
            new Block("asoc", "appScan.compileCommand"));
    private static final List<Block> DATABASE_BLOCKS = COMMAND_BLOCKS.stream()
            .filter(block -> !UNUSED_BY_DATABASE.contains(block.key())).toList();
    private static final List<Rule> COMMAND = List.of(rule("flags", "flags", WORDS), rule("dir", "directory", TEXT),
            rule("env", "environment", ENV), rule("label", "label", TEXT), rule("returnStdout", "returnStdout", FLAG));
    private static final Map<BuildTool, List<Rule>> COMMANDS = Map.of(
            GRADLE, concat(List.of(rule("tasks", "tasks", WORDS)), COMMAND),
            MAVEN, concat(List.of(rule("goals", "tasks", WORDS), rule("mvnPath", "mavenHome", TEXT)), COMMAND),
            LIQUIBASE, COMMAND, DOTNET, COMMAND);
    /** The command keys of a Liquibase or .NET block in the order the library reads them; the first one set wins. */
    private static final Map<BuildTool, List<String>> COMMAND_KEYS = Map.of(LIQUIBASE, List.of("commands", "tasks"),
            DOTNET, List.of("commands", "tasks", "goals"));
    private static final List<Rule> TEST_JOB = concat(
            same(TEXT, "name", "remoteJenkins", "remoteJenkinsUrl", "credentialsId", "tokenCredentialsId"),
            same(FLAG, "abortTriggeredJob", "overrideTrustAllCertificates", "preventRemoteBuildQueue",
                    "trustAllCertificates", "useCrumbCache", "useJobInfoCache"),
            List.of(rule("type", "type", choice(TestJobType.values(), Enum::name)), rule("job", "job", TEXT),
                    rule("url", "job", TEXT), rule("timeoutMin", "timeoutMinutes", NUMBER),
                    rule("parameters", "parameters", PARAMETERS), rule("pollIntervalSec", "pollIntervalSec", NUMBER),
                    rule("auth", "credentialsId", CREDENTIALS_AUTH)));
    private static final List<Rule> URBAN_CODE_APPLICATION = concat(
            same(TEXT, "applicationName", "snapshotName", "siteName", "deployProcess", "deployDescription",
                    "description", "requestProperties"),
            same(FLAG, "skipWait", "deployWithSnapshot", "includeOnlyDeployVersions", "deployOnlyChanged"),
            List.of(rule("order", "order", NUMBER), rule("environments", "environments", LIST),
                    rule("updateSnapshotComp", "updateSnapshotComponents", FLAG)));
    private static final List<Rule> URBAN_CODE_COMPONENT = concat(
            same(TEXT, "componentName", "baseDir", "fileIncludePatterns", "fileExcludePatterns", "versionPrefix",
                    "version", "extensions", "charset", "pushDescription", "versionProperties", "versionDescription"),
            same(FLAG, "incrementalVersion"));
    private static final List<Rule> NEXUS_IQ_APPLICATION = List.of(rule("application", "application", TEXT),
            rule("scanPatterns", "scanPatterns", LIST), rule("stage", "stage", TEXT),
            rule("failOnNetworkError", "failOnNetworkError", FLAG));
    private static final List<Rule> PIPELINE_RULES = List.of(
            rule("type", "type", choice(PipelineType.values(), PipelineType::variant)),
            rule("agentNames", "agentLabels", COMMAS), rule("entryPoint", null, IGNORED),
            rule("product", null, IGNORED), rule("projectNames", null, IGNORED), note("securityPipeline", PIPELINE));

    private final GlobalSettingsValues global;
    private final JsonMapper json;

    ServiceConfigReader(GlobalSettingsValues global, JsonMapper json) {
        this.global = global;
        this.json = json;
    }

    ConfigImportResponse read(Object document, String serviceName) {
        Map<?, ?> root = document instanceof Map<?, ?> map ? map : Map.of();
        List<FieldProblem> notes = new ArrayList<>();
        ConfigInput file = new ConfigInput(root, Map.of(), notes);
        ConfigInput pipelineInput = file.child("pipeline", Map.of());
        Object name = isBlank(serviceName) ? pipelineInput.take("projectNames") : serviceName;
        ImportedPipeline pipeline = root.get("pipeline") instanceof Map<?, ?> ? pipeline(pipelineInput) : null;
        ConfigInput fileDefaults = file.child("defaults", global.defaultsConfig());
        Map<?, ?> projects = root.containsKey("projects")
                ? file.take("projects") instanceof Map<?, ?> named ? named : Map.of()
                : Collections.singletonMap(name, projectOf(root));
        if (projects.size() > MAX_PROJECTS) {
            throw InvalidRequestException.of("content", "holds more than %d projects".formatted(MAX_PROJECTS));
        }

        List<ImportedService> services = new ArrayList<>();
        projects.forEach((project, settings) -> {
            if (settings instanceof Map<?, ?> map && !map.isEmpty()) {
                services.add(service(project == null ? null : String.valueOf(project), map, fileDefaults));
            } else {
                file.note("projects." + project, "not imported: expected the settings of the project");
            }
        });
        if (services.isEmpty()) {
            throw invalid(1, 1, NO_PROJECT);
        }
        file.child("platform", global.platform().toConfig()).noteUnread(GLOBAL);
        fileDefaults.noteUnread(GLOBAL);
        if (root.containsKey("projects")) {
            file.noteUnread(UNKNOWN);
        }
        return new ConfigImportResponse(services, pipeline, notes.stream().distinct().toList());
    }

    private ImportedPipeline pipeline(ConfigInput input) {
        ConfigTree pipeline = read(input, PIPELINE_RULES, new ConfigTree());
        input.noteUnread(UNKNOWN);
        return json.convertValue(pipeline.toMap(), ImportedPipeline.class);
    }

    /** The values the portal writes into the project anyway, among them the package named after the project. */
    private Map<String, Object> inherited(String name) {
        ConfigTree inherited = new ConfigTree().set("deploy.vm.dod.applications", List.of(Map.of()));
        global.platform().writeProjectDefaults(inherited);
        global.deployment().fillIn(inherited, VM);
        global.goldenFix().writeTo(inherited);
        inherited.set("tests.maxParallel", global.serviceDefaults().testsMaxParallel());
        global.serviceDefaults().writeNodeTo(inherited);
        global.serviceDefaults().writeReleaseTo(inherited);
        inherited.set("delivery.release.enabled", global.serviceDefaults().releaseEnabled());
        inherited.set("build.node.commands", List.of("npm ci", "npm run build")).set("build.node.timeoutMin", 30)
                .set("build.node.nexusIqScan", true);
        global.database().writePackageTo(inherited);
        return inherited.set("database.package.artifactId", name).toMap();
    }

    private ImportedService service(String name, Map<?, ?> settings, ConfigInput fileDefaults) {
        List<FieldProblem> notes = new ArrayList<>();
        ConfigInput project = new ConfigInput(settings, inherited(name), notes);
        ConfigTree request = new ConfigTree();
        ConfigInput globalDefaults = new ConfigInput(global.defaultsConfig(), Map.of(), new ArrayList<>());
        for (Rule rule : DEFAULTED) {
            for (ConfigInput source : List.of(project, fileDefaults, globalDefaults)) {
                if (request.get(rule.field()) == null) {
                    read(source, List.of(rule), request);
                }
            }
        }
        BuildTool named = BuildTool.valueOf(String.valueOf(request.get("build.tool")));
        String own = named != SQL ? null
                : Stream.of("database.changelogFile", "build.liquibase", "tests.unitTests.liquibase")
                        .filter(project::unread).findFirst().orElse(null);
        if (own != null) {
            request.set("build.tool", LIQUIBASE.name());
            project.note("buildTool", SQL_WITH_CHANGELOG.formatted(own));
        }
        BuildTool tool = own != null ? LIQUIBASE : named;
        DeployTarget target = DeployTarget.valueOf(String.valueOf(request.get("deployment.target")));
        boolean database = tool == SQL || tool == LIQUIBASE && project.take("database.changelogFile") != null;
        if (database) {
            readDatabase(project, request);
        } else {
            read(project, PROJECT, request);
            if (project.take("database") != null) {
                project.note("database", NOT_A_DATABASE);
            }
        }
        readScannerVersion(project, tool, request);
        if ((tool == FLUTTER || database) && Boolean.TRUE.equals(request.get("nodeBuild.enabled"))) {
            request.set("nodeBuild.enabled", false);
            project.note("build.node.enabled", database ? NODE_WITH_DATABASE : NODE_WITH_FLUTTER);
        }
        readCommands(project, tool, database ? DATABASE_BLOCKS : COMMAND_BLOCKS, request);
        if (!database) {
            readDelivery(project, target, tool, request);
            request.set("urbanCodeApplications", project.entries("deploy.vm.dod.applications", null, null).stream()
                    .map(application -> entry(application, URBAN_CODE_APPLICATION, new ConfigTree()
                            .set("components", application.entries("components", null, null).stream()
                                    .map(component -> entry(component, URBAN_CODE_COMPONENT, new ConfigTree()))
                                    .toList())))
                    .toList());
        }
        request.set("testJobs", testJobs(project)).set("nexusIqApplications", nexusIqApplications(project, settings));
        boolean delivers = !Boolean.TRUE.equals(request.get("deployment.nexusDeliveryDisabled"));
        for (String key : List.of("buildTag", "internalDockerUrl")) {
            if (request.get("openShiftTargets.RD." + key) != null && delivers) {
                project.note("deploy.openshift.rd." + key, RUN_TIME);
            }
        }
        project.noteUnread(UNKNOWN);

        Map<String, Object> fields = request.toMap();
        fields.putIfAbsent("appScan", Map.of());
        Service service = new Service(null, name, null, 0,
                json.convertValue(fields, ServiceDto.class).toDraft().settings());
        return new ImportedService(name, ServiceDto.from(service), notes);
    }

    /**
     * Reads a Liquibase service with database changes: its database block, the project keys it shares with every
     * service, and a note for each deployment key. The Maven home of build.maven fills that of the package, as in the
     * library.
     */
    private static void readDatabase(ConfigInput project, ConfigTree request) {
        read(project, DATABASE, request);
        read(project, DATABASE_PROJECT, request);
        if (request.get("database.mvnPath") == null) {
            read(project, List.of(rule(MAVEN_HOME, "database.mvnPath", TEXT)), request);
        }
        noteLiquibaseDelivery(project);
        for (String key : UNUSED_BY_DATABASE) {
            if ((!"delivery".equals(key) || project.unread(key)) && project.take(key) != null) {
                project.note(key, NOT_FOR_DATABASE);
            }
        }
    }

    /** Notes delivery.liquibase with its own reason: no stage of the library reads it, whatever the build tool. */
    private static void noteLiquibaseDelivery(ConfigInput project) {
        if (project.unread(LIQUIBASE_DELIVERY) && project.take(LIQUIBASE_DELIVERY) != null) {
            project.note(LIQUIBASE_DELIVERY, NO_LIQUIBASE_DELIVERY);
        }
    }

    /**
     * Reads the SonarScanner version, which picks the Jenkins tool of every sonar-scanner CLI run; a .NET service is
     * analysed with the SonarScanner for .NET, so its version is left out with a note.
     */
    private static void readScannerVersion(ConfigInput project, BuildTool tool, ConfigTree request) {
        if (!BuildTool.dotnet(tool)) {
            read(project, List.of(rule(SCANNER_VERSION, "sonar.sonarScannerVersion", TEXT)), request);
        } else if (project.take(SCANNER_VERSION) != null) {
            project.note(SCANNER_VERSION, SCANNER_FOR_DOTNET);
        }
    }

    /**
     * Reads the command blocks of the service's build tool; a block of another tool is left out with a note, unless
     * what it holds was read already, and so is a block that is not a map. Of the keys that hold the commands of a
     * Liquibase or .NET block, the one the library reads first is imported and a later one is noted. A .NET service,
     * C# / .NET or MSBuild, reads the block the library takes and writes it as the dotnet block, which the library
     * reads for buildTool msbuild as well. The Liquibase block of a Gradle, Android, Maven or .NET service is the
     * Liquibase step that the library runs after the build, after the unit tests and, for Gradle, Android and Maven,
     * after the compile of the AppScan scan; under tools.sonar it adds to the SonarQube flags of a Gradle, Android or
     * Maven service.
     */
    private static void readCommands(ConfigInput project, BuildTool tool, List<Block> blocks, ConfigTree request) {
        for (Block block : blocks) {
            String taken = commandBlock(project, block.key(), tool);
            String step = liquibaseStepField(block, tool);
            for (BuildTool blockTool : BLOCK_TOOLS) {
                String key = block.key() + "." + blockTool.name().toLowerCase(ROOT);
                if (blockTool == LIQUIBASE && step != null && !key.equals(taken)) {
                    readLiquibaseStep(project, key, step, request);
                } else if (blockTool == LIQUIBASE && LIQUIBASE_SONAR.equals(key) && BuildTool.needsTasks(tool)) {
                    readLiquibaseSonarFlags(project, request);
                } else if (key.equals(taken)) {
                    if (!project.holdsMap(key) && project.take(key) != null) {
                        project.note(key, "not imported: expected a map");
                        continue;
                    }
                    BuildTool rules = BuildTool.commandsOf(blockTool);
                    readFirst(project, COMMAND_KEYS.getOrDefault(rules, List.of()).stream()
                            .map(name -> key + "." + name).toList(), block.field() + ".tasks", WORDS, request);
                    read(project, prefixed(key + ".", block.field() + ".", COMMANDS.get(rules)), request);
                    if (blockTool == MSBUILD && request.get(block.field()) != null) {
                        project.note(key, DOTNET_BLOCK.formatted(block.key(), block.key()));
                    }
                } else if (project.unread(key) && project.take(key) != null) {
                    project.note(key, blockTool == LIQUIBASE && BuildTool.liquibaseStep(tool)
                            ? "asoc".equals(block.key()) ? NO_LIQUIBASE_COMPILE_STEP : NO_LIQUIBASE_SONAR
                            : !BuildTool.dotnet(tool) || !BuildTool.dotnet(blockTool)
                            ? "not imported: the service builds with " + tool.name().toLowerCase(ROOT)
                            : project.holdsMap(key) ? "not imported: " + taken + " wins over it"
                            : "not imported: expected a map");
                }
            }
        }
        if (tool == LIQUIBASE || tool == SQL) {
            readLiquibaseSonar(project, request);
        }
        if (BuildTool.scripted(tool)) {
            readScripted(project, tool, request);
        }
    }

    /**
     * The block whose commands the library runs for the build tool, such as build.gradle. For a .NET service it is the
     * one DotnetProjectResolver.toolCfg takes: the msbuild block for buildTool msbuild when it is a map, else the
     * dotnet block when it is one, else the msbuild block; for buildTool dotnet the dotnet block comes first.
     */
    private static String commandBlock(ConfigInput project, String path, BuildTool tool) {
        String own = path + "." + BuildTool.commandsOf(tool).name().toLowerCase(ROOT);
        if (!BuildTool.dotnet(tool)) {
            return own;
        }
        String msbuild = path + "." + MSBUILD.name().toLowerCase(ROOT);
        List<String> order = tool == MSBUILD ? List.of(msbuild, own) : List.of(own, msbuild);
        return order.stream().filter(project::holdsMap).findFirst().orElse(own);
    }

    /**
     * The request field of the Liquibase step that the library runs in the block for the service's build tool: after
     * the build and after the unit tests of a Gradle, Android, Maven or .NET service, and after the AppScan compile of
     * a Gradle, Android or Maven service; none for any other block or tool.
     */
    private static String liquibaseStepField(Block block, BuildTool tool) {
        return switch (block.key()) {
            case "build" -> BuildTool.liquibaseStep(tool) ? "build.liquibase" : null;
            case "tests.unitTests" -> BuildTool.liquibaseStep(tool) ? "unitTests.liquibase" : null;
            case "asoc" -> BuildTool.liquibaseCompileStep(tool) ? "appScan.liquibaseCompileCommand" : null;
            default -> null;
        };
    }

    /**
     * Reads a Liquibase step of a Gradle, Android, Maven or .NET service, such as build.liquibase, as
     * LiquibaseBuildRunner runs it: its commands, else its tasks, and its flags, folder, variables, label and output
     * switch. A step that is not a map, or one without commands, tasks and flags, is skipped by the library and left
     * out with a note. An asoc.liquibase map of a Gradle, Android or Maven service without commands, tasks and flags,
     * also one whose values are all empty, keeps the AppScan compile from running build.liquibase, which the portal
     * cannot do, so while build.liquibase runs and the compile is on, its note says that the compile runs
     * build.liquibase after the import.
     */
    private static void readLiquibaseStep(ConfigInput project, String key, String field, ConfigTree request) {
        boolean given = project.unread(key);
        boolean runs = given && project.holdsMap(key) && Stream.concat(COMMAND_KEYS.get(LIQUIBASE).stream(),
                Stream.of("flags")).anyMatch(name -> project.unread(key + "." + name));
        if (runs) {
            readFirst(project, COMMAND_KEYS.get(LIQUIBASE).stream().map(name -> key + "." + name).toList(),
                    field + ".tasks", WORDS, request);
            read(project, prefixed(key + ".", field + ".", COMMANDS.get(LIQUIBASE)), request);
        } else if (stopsBuildStep(project, key, field, request)) {
            project.note(key, LIQUIBASE_COMPILE_STEP_OFF);
        } else if (given) {
            project.take(key);
            project.note(key, project.holdsMap(key) ? SKIPPED_LIQUIBASE_STEP : "not imported: expected a map");
        }
    }

    /**
     * Whether asoc.liquibase keeps the AppScan compile from running build.liquibase: the library takes asoc.liquibase,
     * else build.liquibase (AppScanService.generateIrx), so a map with keys wins even when its values are all empty,
     * and the compile then runs no Liquibase step, while the imported service, which has no such block, would run
     * build.liquibase there. The build block and asoc.doCompile are read before the asoc block.
     */
    private static boolean stopsBuildStep(ConfigInput project, String key, String field, ConfigTree request) {
        return "appScan.liquibaseCompileCommand".equals(field) && project.holdsMap(key) && project.take(key) != null
                && !Boolean.FALSE.equals(request.get("appScan.compile"))
                && (request.get("build.liquibase.tasks") != null || request.get("build.liquibase.flags") != null);
    }

    /**
     * Adds tools.sonar.liquibase to the SonarQube flags of a Gradle, Android or Maven service, as the library does for
     * its SonarQube tasks or goals (SonarService.effectiveSonarCfg): the sources, inclusions and exclusions as -Dsonar.*
     * flags, then the flags of the block, each unless a flag of the same name is set already. Without SonarQube tasks
     * or goals the sonar-scanner CLI runs and reads none of it, and the other keys of the block are not read either.
     */
    private static void readLiquibaseSonarFlags(ConfigInput project, ConfigTree request) {
        if (!project.unread(LIQUIBASE_SONAR)) {
            return;
        }
        Object block = project.take(LIQUIBASE_SONAR);
        if (!(block instanceof Map<?, ?> values)) {
            project.note(LIQUIBASE_SONAR, "not imported: expected a map");
            return;
        }
        if (!(request.get("sonar.command.tasks") instanceof List<?> tasks) || tasks.isEmpty()) {
            project.note(LIQUIBASE_SONAR, LIQUIBASE_SONAR_WITHOUT_TASKS);
            return;
        }
        List<String> extra = new ArrayList<>();
        for (String property : LIQUIBASE_SONAR_PROPERTIES) {
            String key = LIQUIBASE_SONAR + "." + property;
            try {
                Object value = project.take(key);
                String joined = value == null ? "" : String.join(",", items(value, ","));
                if (!joined.isBlank()) {
                    extra.add("-Dsonar." + property + "=" + joined);
                }
            } catch (NotImported e) {
                project.note(key, e.getMessage());
            }
        }
        try {
            Object flags = project.take(LIQUIBASE_SONAR + ".flags");
            if (flags != null) {
                items(flags, "\\s+").stream().filter(flag -> !flag.isBlank()).forEach(extra::add);
            }
        } catch (NotImported e) {
            project.note(LIQUIBASE_SONAR + ".flags", e.getMessage());
        }
        List<String> flags = new ArrayList<>(request.get("sonar.command.flags") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of());
        for (String flag : extra) {
            String name = flag.contains("=") ? flag.substring(0, flag.indexOf('=') + 1) : flag;
            if (flags.stream().noneMatch(kept -> kept.startsWith(name))) {
                flags.add(flag);
            }
        }
        if (!flags.isEmpty()) {
            request.set("sonar.command.flags", List.copyOf(flags));
        }
        values.forEach((name, value) -> {
            boolean merged = LIQUIBASE_SONAR_PROPERTIES.contains(String.valueOf(name)) || "flags".equals(name);
            if (!merged && !(value == null || value instanceof String text && text.isBlank()
                    || value instanceof Collection<?> list && list.isEmpty()
                    || value instanceof Map<?, ?> map && map.isEmpty())) {
                project.note(LIQUIBASE_SONAR + "." + name, LIQUIBASE_SONAR_KEYS);
            }
        });
    }

    /**
     * Reads what the library runs for npm, pip and iOS: the command lines of the build (build.pip.commands or
     * build.ios.commands, else build.commands; an npm service builds with build.node) and of the unit tests
     * (tests.unitTests.commands, else tests.unitTests.tasks) with their folder, and the SonarScanner arguments of
     * tools.sonar.<tool> (tools.sonar.node for npm as well). Several command lines become one, joined with &&.
     */
    private static void readScripted(ConfigInput project, BuildTool tool, ConfigTree request) {
        String name = tool.name().toLowerCase(ROOT);
        if (tool != BuildTool.NPM) {
            readFirst(project, List.of("build." + name + ".commands", "build.commands"), "build.command.tasks",
                    COMMAND_LINES, request);
        }
        readFirst(project, List.of("tests.unitTests.commands", "tests.unitTests.tasks"), "unitTests.command.tasks",
                COMMAND_LINES, request);
        read(project, List.of(rule("tests.unitTests.dir", "unitTests.command.directory", TEXT)), request);
        List<String> blocks = tool == BuildTool.NPM ? List.of("tools.sonar.npm", "tools.sonar.node")
                : List.of("tools.sonar." + name);
        List<String> flagKeys = new ArrayList<>(blocks.stream().map(block -> block + ".flags").toList());
        flagKeys.add("tools.sonar.flags");
        readFirst(project, flagKeys, "sonar.command.flags", WORDS, request);
        List<String> flags = new ArrayList<>(request.get("sonar.command.flags") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of());
        for (String block : blocks) {
            Object sources = project.take(block + ".sources");
            if (sources != null && flags.stream().noneMatch(flag -> flag.startsWith("-Dsonar.sources="))) {
                flags.add("-Dsonar.sources=" + String.join(",", items(sources, ",")));
            }
        }
        if (!flags.isEmpty()) {
            request.set("sonar.command.flags", List.copyOf(flags));
        }
    }

    /** Reads the first of the keys that is set; a later one that is set too is left out with a note. */
    private static void readFirst(ConfigInput project, List<String> keys, String field, Conversion conversion,
                                  ConfigTree request) {
        String winner = null;
        for (String key : keys) {
            if (winner == null && project.unread(key)) {
                read(project, List.of(rule(key, field, conversion)), request);
                winner = key;
            } else if (winner != null && project.unread(key) && project.take(key) != null) {
                project.note(key, "not imported: " + winner + " wins over it");
            }
        }
    }

    private static Map<String, String> toolNames() {
        Map<String, String> names = new LinkedHashMap<>();
        List.of("python", "poetry", "pipenv").forEach(name -> names.put(name, "pip"));
        List.of("node", "nodejs", "yarn", "pnpm", "angular", "react", "vue").forEach(name -> names.put(name, "npm"));
        List.of("xcode", "swift", "cocoapods").forEach(name -> names.put(name, "ios"));
        List.of("vs", "visualstudio", "netfx").forEach(name -> names.put(name, "msbuild"));
        List.of("csharp", "c#", ".net", "net", "dotnet-cli", "fsharp", "vbnet", "cake", "nuke")
                .forEach(name -> names.put(name, "dotnet"));
        names.put("mvn", "maven");
        names.put("gradlew", "gradle");
        names.put("dart", "flutter");
        return Map.copyOf(names);
    }

    /** Reads Liquibase SonarQube sources, inclusions and exclusions as -Dsonar.* flags on the SonarQube command. */
    private static void readLiquibaseSonar(ConfigInput project, ConfigTree request) {
        List<String> flags = new ArrayList<>(request.get("sonar.command.flags") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of());
        Object topSources = request.get("flutter.sonarSources");
        for (String prop : List.of("sources", "inclusions", "exclusions")) {
            String key = "tools.sonar.liquibase." + prop;
            Object raw = project.take(key);
            if (raw != null) {
                try {
                    String joined = String.join(",", items(raw, ","));
                    if (!joined.isBlank() && flags.stream().noneMatch(f -> f.startsWith("-Dsonar." + prop + "="))) {
                        flags.add("-Dsonar." + prop + "=" + joined);
                    }
                } catch (NotImported e) {
                    project.note(key, e.getMessage());
                }
            } else if ("sources".equals(prop) && topSources instanceof String s && !s.isBlank()
                    && flags.stream().noneMatch(f -> f.startsWith("-Dsonar.sources="))) {
                flags.add("-Dsonar.sources=" + s.trim());
            }
        }
        if (!flags.isEmpty()) {
            request.set("sonar.command.flags", List.copyOf(flags));
        }
    }

    /**
     * Reads the delivery switch, delivery.nexusDeliveryEnabled or, without it, that of the service's deploy target,
     * and the Nexus delivery of the service's build tool. The portal keeps one group, artifact and repository for the
     * upload and the download, so a differing second value is left out with a note.
     */
    private static void readDelivery(ConfigInput project, DeployTarget target, BuildTool tool, ConfigTree request) {
        read(project, List.of(rule(DELIVERY, "deployment.nexusDeliveryDisabled", DISABLED)), request);
        boolean switched = request.get("deployment.nexusDeliveryDisabled") != null;
        for (DeployTarget each : DeployTarget.values()) {
            String key = LEGACY_DELIVERY.formatted(each.name().toLowerCase(ROOT));
            if (each == target && !switched) {
                read(project, List.of(rule(key, "deployment.nexusDeliveryDisabled", DISABLED)), request);
            } else if (project.take(key) != null) {
                project.note(key, each == target ? "not imported: " + DELIVERY + " wins over it"
                        : "not imported: the service deploys to " + target.name().toLowerCase(ROOT));
            }
        }
        noteLiquibaseDelivery(project);
        for (BuildTool blockTool : List.of(GRADLE, MAVEN, DOTNET)) {
            String key = "delivery." + blockTool.name().toLowerCase(ROOT);
            boolean own = blockTool == tool || blockTool == DOTNET && tool == MSBUILD;
            if ((!own || tool == GRADLE) && project.unread(key) && project.take(key) != null) {
                project.note(key, tool == GRADLE && blockTool == GRADLE
                        ? "not imported: a Gradle service uploads with delivery.push.gradle"
                        : "not imported: the service builds with " + tool.name().toLowerCase(ROOT));
            }
        }
        if (tool == MAVEN) {
            readMavenDelivery(project, request);
        } else if (tool == GRADLE) {
            readGradleDelivery(project, request);
        } else if (tool == NPM || tool == PIP) {
            read(project, concat(prefixed("delivery.push.maven.", "delivery.", COMMANDS.get(MAVEN)),
                    List.of(rule("delivery.push.maven.artifacts", "nexusDelivery.artifacts", ARTIFACTS))), request);
        } else if (BuildTool.dotnet(tool)) {
            readNugetDelivery(project, request);
        }
        if (!BuildTool.dotnet(tool) && project.unread("delivery.nuget") && project.take("delivery.nuget") != null) {
            project.note("delivery.nuget", "not imported: the service builds with " + tool.name().toLowerCase(ROOT));
        }
        boolean releases = NexusDeliverySettings.releases(tool);
        if (releases && target == VM) {
            readRelease(project, tool, request);
        } else if (project.unread("delivery.release") && project.take("delivery.release") != null) {
            project.note("delivery.release", BuildTool.dotnet(tool)
                    ? "not imported: a .NET service releases its NuGet package to delivery.nuget.releaseSource"
                    : releases ? "not imported: an OpenShift service delivers its image, and stage 12 releases no"
                    + " artifact of it"
                    : "not imported: the library releases no artifact of a " + tool.name().toLowerCase(ROOT)
                    + " service in stage 12");
        }
    }

    /**
     * The release of stage 12, delivery.release: a value equal to that of the Library defaults comes back unset. A Maven
     * upload logs in with the server ID, a Gradle upload with the credentials, so the other one is left out with a note.
     */
    private static void readRelease(ConfigInput project, BuildTool tool, ConfigTree request) {
        String unused = tool == GRADLE ? "delivery.release.repositoryId" : "delivery.release.credentialsId";
        read(project, List.of(rule("delivery.release.enabled", "nexusDelivery.releaseEnabled", FLAG),
                rule("delivery.release.repositoryUrl", "nexusDelivery.releaseRepositoryUrl", TEXT),
                rule(tool == GRADLE ? "delivery.release.credentialsId" : "delivery.release.repositoryId",
                        tool == GRADLE ? "nexusDelivery.releaseCredentialsId" : "nexusDelivery.releaseRepositoryId",
                        TEXT)), request);
        if (project.take(unused) != null) {
            project.note(unused, tool == GRADLE
                    ? "not imported: a Gradle upload logs in to the release repository with delivery.release.credentialsId"
                    : "not imported: a Maven upload logs in to the release repository with delivery.release.repositoryId");
        }
    }

    /**
     * The NuGet feeds of a .NET service: delivery.nuget or, without it, delivery.dotnet, with the other names the
     * library reads (repositoryUrl for source, apiKeyCredentialsId for credentialsId).
     */
    private static void readNugetDelivery(ConfigInput project, ConfigTree request) {
        boolean nuget = project.unread("delivery.nuget");
        String block = nuget ? "delivery.nuget." : "delivery.dotnet.";
        for (List<String> names : List.of(List.of("source", "repositoryUrl"),
                List.of("credentialsId", "apiKeyCredentialsId"))) {
            String field = "nexusDelivery.nuget" + capitalize(names.get(0));
            read(project, List.of(rule(block + names.get(0), field, TEXT)), request);
            if (request.get(field) == null) {
                read(project, List.of(rule(block + names.get(1), field, TEXT)), request);
            } else if (project.take(block + names.get(1)) != null) {
                project.note(block + names.get(1), "not imported: " + block + names.get(0) + " wins over it");
            }
        }
        read(project, List.of(rule(block + "releaseSource", "nexusDelivery.nugetReleaseSource", TEXT),
                rule(block + "releaseCredentialsId", "nexusDelivery.nugetReleaseCredentialsId", TEXT),
                rule(block + "packagePattern", "nexusDelivery.nugetPackagePattern", TEXT)), request);
        if (nuget && project.take("delivery.dotnet") != null) {
            project.note("delivery.dotnet", "not imported: delivery.nuget replaces it");
        }
    }

    /** The upload of delivery.push.maven or, without it, delivery.maven, and the download of delivery.pull.maven. */
    private static void readMavenDelivery(ConfigInput project, ConfigTree request) {
        boolean push = project.unread("delivery.push.maven");
        read(project, prefixed(push ? "delivery.push.maven." : "delivery.maven.", "delivery.", COMMANDS.get(MAVEN)),
                request);
        if (push) {
            read(project, List.of(rule("delivery.push.maven.artifacts", "nexusDelivery.artifacts", ARTIFACTS)),
                    request);
            if (project.unread("delivery.maven") && project.take("delivery.maven") != null) {
                project.note("delivery.maven", "not imported: delivery.push.maven replaces it");
            }
        }
        readCoordinates(project, request, List.of("group", "artifact"), "delivery.pull.maven.", "delivery.pull.");
        read(project, concat(prefixed("delivery.pull.maven.", "nexusDelivery.pull.", COMMANDS.get(MAVEN)),
                pullRules("delivery.pull.maven.")), request);
    }

    /** The upload of delivery.push.gradle and the download of delivery.pull.gradle, with their flags. */
    private static void readGradleDelivery(ConfigInput project, ConfigTree request) {
        readCoordinates(project, request, List.of("group", "artifact", "repositoryUrl"), "delivery.push.gradle.",
                "delivery.pull.gradle.", "delivery.pull.");
        readCoordinates(project, request, List.of("allowInsecureProtocol"), "delivery.push.gradle.",
                "delivery.pull.gradle.");
        read(project, List.of(rule("delivery.push.gradle.artifacts", "nexusDelivery.artifacts", ARTIFACTS),
                rule("delivery.push.gradle.flags", "delivery.flags", WORDS),
                rule("delivery.push.gradle.env", "delivery.environment", ENV),
                rule("delivery.push.gradle.tasks", null, gradleTask(NexusDeliverySettings.GRADLE_PUSH_TASK)),
                rule("delivery.pull.gradle.flags", "nexusDelivery.pull.flags", WORDS),
                rule("delivery.pull.gradle.env", "nexusDelivery.pull.environment", ENV),
                rule("delivery.pull.gradle.tasks", null, gradleTask(NexusDeliverySettings.GRADLE_PULL_TASK))), request);
        read(project, pullRules("delivery.pull.gradle."), request);
    }

    private static List<Rule> pullRules(String block) {
        return List.of(rule(block + "artifacts", "nexusDelivery.pullArtifacts", ARTIFACTS),
                rule("delivery.pull.outputDirectory", "nexusDelivery.pullOutputDirectory", TEXT));
    }

    /**
     * Reads the group, artifact, repository URL and insecure switch from the first block that has each; the same value
     * in a later block is read along, a different one is left out with a note.
     */
    private static void readCoordinates(ConfigInput project, ConfigTree request, List<String> keys,
                                        String... blocks) {
        for (String key : keys) {
            Conversion conversion = "allowInsecureProtocol".equals(key) ? FLAG : TEXT;
            String field = "nexusDelivery." + key;
            for (String block : blocks) {
                Object value = project.take(block + key);
                if (value == null) {
                    continue;
                }
                try {
                    Object converted = conversion.apply(value);
                    Object kept = request.get(field);
                    if (kept == null) {
                        request.set(field, converted);
                    } else if (!String.valueOf(kept).trim().equals(String.valueOf(converted).trim())) {
                        project.note(block + key, "not imported: the portal uploads and downloads with one " + key
                                + ", " + kept);
                    }
                } catch (NotImported e) {
                    project.note(block + key, e.getMessage());
                }
            }
        }
    }

    /** The tasks of a Gradle step of the Nexus delivery: only its one task, which the portal does not keep. */
    private static Conversion gradleTask(String task) {
        return value -> {
            if (!items(value, "\\s+").equals(List.of(task))) {
                throw new NotImported("not imported: the Gradle step of the Nexus delivery runs only " + task);
            }
            return null;
        };
    }

    /** The jobs of every test stage, with the stage's defaults under each and its URLs as jobs of their own. */
    private static List<Map<String, Object>> testJobs(ConfigInput project) {
        List<Map<String, Object>> jobs = new ArrayList<>();
        for (TestStage stage : TestStage.values()) {
            String path = "tests." + stage.configKey() + ".";
            ConfigInput defaults = project.child(path + "defaults", Map.of());
            for (String list : List.of("jobs", "urls")) {
                project.entries(path + list, "url", defaults).forEach(job ->
                        jobs.add(entry(job, TEST_JOB, new ConfigTree().set("stage", stage.name()))));
            }
            defaults.noteUnread(UNKNOWN);
        }
        return jobs;
    }

    /**
     * The Nexus IQ application: a name with its settings beside it, or, in the form of legacy services, a map of
     * applications whose first entry is taken. Each entry repeats the server, which is inherited from the section.
     */
    private static List<Map<String, Object>> nexusIqApplications(ConfigInput project, Map<?, ?> settings) {
        String path = "tools.nexusIq.";
        if (!(project.take(path + "application") instanceof Map<?, ?> applications)) {
            ConfigTree application = read(project, prefixed(path, "", NEXUS_IQ_APPLICATION), new ConfigTree());
            return application.get("application") == null ? List.of() : List.of(application.toMap());
        }
        Map<String, Object> server = new ConfigTree().set("serverUrl", valueAt(settings, path + "serverUrl"))
                .set("credentialsId", valueAt(settings, path + "credentialsId")).toMap();
        List<Map<String, Object>> taken = new ArrayList<>();
        applications.forEach((name, entry) -> {
            String key = path + "application." + name;
            if (taken.isEmpty()) {
                ConfigInput input = project.nested(key, entry instanceof Map<?, ?> map ? map : Map.of(), server, null);
                taken.add(entry(input, NEXUS_IQ_APPLICATION, new ConfigTree().set("application", name)));
            } else {
                project.note(key, "not imported: " + ONE_APPLICATION);
            }
        });
        return taken;
    }

    private static Map<String, Object> entry(ConfigInput input, List<Rule> rules, ConfigTree entry) {
        read(input, rules, entry);
        input.noteUnread(UNKNOWN);
        return entry.toMap();
    }

    private static ConfigTree read(ConfigInput input, List<Rule> rules, ConfigTree request) {
        for (Rule rule : rules) {
            Object value = input.take(rule.key());
            if (value != null) {
                try {
                    request.set(rule.field(), rule.conversion().apply(value));
                } catch (NotImported e) {
                    input.note(rule.key(), e.getMessage());
                }
            }
        }
        return request;
    }

    /** The settings of the one project a file without a projects map describes: none when it has no project key. */
    private static Map<?, ?> projectOf(Map<?, ?> root) {
        Map<Object, Object> project = new LinkedHashMap<>(root);
        project.keySet().removeAll(DOCUMENT_KEYS);
        return project.keySet().stream().anyMatch(PROJECT_KEYS::contains) ? project : Map.of();
    }

    private static List<Rule> projectRules() {
        List<Rule> rules = new ArrayList<>(List.of(
                rule("javaPath", "build.javaPath", TEXT),
                rule("buildToolAutoSetup", "build.autoSetup", FLAG),
                rule("build.buildPath", "build.buildPath", TEXT),
                rule("appId", "appScan.applicationId", TEXT),
                rule("includedDirs", "appScan.includedDirs", COMMAS),
                rule("excludedDirs", "appScan.excludedDirs", COMMAS),
                rule("appscanPath", "appScan.clientPath", TEXT),
                note("asoc.url", GLOBAL),
                note("staticAnalysis.parallel", GLOBAL),
                note("dynamicAnalysis.parallel", GLOBAL),
                note("pluginRepositories.enabled", GLOBAL),
                note("pluginRepositories.gradle", GLOBAL),
                note("pluginRepositories.maven", GLOBAL),
                note("asoc.keyId", APPSCAN_KEY),
                note("asoc.token", APPSCAN_KEY),
                rule("asoc.doCompile", "appScan.compile", FLAG),
                rule("asoc.useAppScanConfig", "appScan.useConfigFile", FLAG),
                rule("sast.scanName", "appScan.scanName", TEXT),
                rule("dast.enabled", "appScan.dastEnabled", FLAG),
                rule("dast.targetUrl", "appScan.dastTargetUrl", TEXT),
                rule("dast.presenceId", "appScan.dastPresenceId", TEXT),
                note("jenkins.pipeline.extendedPipeline", PIPELINE),
                rule("influx.enabled", "metrics.enabled", FLAG),
                rule("influx.url", "metrics.influxUrl", TEXT),
                rule("influx.credentialsId", "metrics.influxCredentialsId", TEXT),
                rule("influx.project", "metrics.influxProject", TEXT),
                rule("influx.env", "metrics.influxEnv", TEXT),
                rule("tests.unitTests.unitTestResult", "unitTests.resultPattern", TEXT),
                rule("coverage.reportPath", "unitTests.coverageReportPath", PATHS),
                rule("tests.maxParallel", "tests.maxParallel", NUMBER),
                rule("tools.sonar.authToken", "sonar.authTokenCredentialsId", TEXT),
                rule("scm.bitbucket.url", "scm.repositoryUrl", TEXT),
                rule("scm.bitbucket.authType", "scm.authType", AUTH_TYPE),
                rule("scm.bitbucket.type", "scm.type", choice(BitbucketType.values(), Enum::name)),
                rule("scm.bitbucket.reviewers", "scm.reviewers", COMMAS),
                rule("goldenFix.minThreatLevel", "goldenFix.minThreatLevel", NUMBER),
                rule("goldenFix.verify.enabled", null, IGNORED),
                rule("goldenFix.verify.maxAttempts", "goldenFix.verifyMaxAttempts", NUMBER),
                rule("goldenFix.verify.timeoutMinutes", "goldenFix.verifyTimeoutMinutes", NUMBER),
                rule("deploy.vm.dod.updateSnapshotComp", "urbanCode.updateSnapshotComponents", FLAG),
                rule("flutter.platform", "flutter.platform", choice(FlutterPlatform.values(), Enum::name)),
                rule("tools.flutter.flutterModules", "flutter.modules", LIST),
                rule("tests.modules", "flutter.testModules", LIST),
                rule("tests.submodules", "flutter.testSubmodules", LIST),
                rule("tests.subplugins", "flutter.testSubplugins", LIST),
                rule("build.credentialsId.0", "flutter.signingPasswordCredentialsId", TEXT),
                rule("build.credentialsId.1", "flutter.prodLicenseCredentialsId", TEXT),
                rule("build.credentialsId.2", "flutter.testLicenseCredentialsId", TEXT),
                rule("delivery.group", "flutter.deliveryGroup", TEXT),
                rule("delivery.artifact", "flutter.deliveryArtifact", TEXT),
                rule("delivery.plugin", "flutter.deliveryPlugin", TEXT),
                rule("tools.sonar.sources", "flutter.sonarSources", TEXT),
                rule("tools.sonar.tests", "flutter.sonarTests", TEXT),
                rule("tools.sonar.dartAnalyzeCommand", "flutter.dartAnalyzeCommand", TEXT),
                rule("tools.sonar.flutterPlugin", "flutter.sonarFlutterPlugin", FLAG)));
        rules.addAll(prefixed("", "deployment.", same(TEXT, "appName", "artifactName", "baseArtifactName")));
        rules.addAll(prefixed("build.node.", "nodeBuild.", concat(same(FLAG, "enabled", "nexusIqScan"),
                same(TEXT, "installationName", "nodePath", "registry", "npmrcCredentialsId"),
                List.of(rule("dir", "directory", TEXT), rule("commands", "commands", LIST),
                        rule("env", "environment", ENV), rule("timeoutMin", "timeoutMinutes", NUMBER)))));
        rules.addAll(prefixed("asoc.", "appScan.", same(FLAG, "sourceCodeOnly", "insecureTls")));
        rules.addAll(prefixed("tests.unitTests.", "unitTests.", concat(same(TEXT, "rootDir", "reportOutDir"),
                same(FLAG, "allowEmptyResults"))));
        List<Rule> cypressRules = List.of(
                rule("enabled", "cypressEnabled", FLAG),
                rule("command", "cypressCommand", TEXT),
                rule("reportsDir", "cypressReportsDir", TEXT));
        rules.addAll(prefixed("tests.unitTests.cypress.", "unitTests.", cypressRules));
        rules.addAll(prefixed("unitTests.cypress.", "unitTests.", cypressRules));
        rules.addAll(prefixed("build.node.cypress.", "unitTests.", cypressRules));
        rules.addAll(prefixed("cypress.", "unitTests.", cypressRules));
        rules.addAll(prefixed("tools.sonar.", "sonar.", concat(same(TEXT, "serverUrl", "projectName", "projectKey",
                "installationName", "credentialsId", "badgeToken", "badgeCredentialsId"),
                same(FLAG, "addBadges", "fullBadges"))));
        rules.addAll(prefixed("tools.nexusIq.", "nexusIq.", same(TEXT, "serverUrl", "credentialsId")));
        rules.addAll(prefixed("scm.bitbucket.", "scm.", same(TEXT, "credentialsId", "targetBranch", "cloneUrl",
                "apiUrl", "workspace", "projectKey", "repoSlug")));
        rules.addAll(prefixed("goldenFix.", "goldenFix.", concat(same(FLAG, "enabled"),
                same(LIST, "ecosystems", "goldenVersionTypes", "excludeDirs"),
                same(TEXT, "commitAuthorName", "commitAuthorEmail", "timeZone"))));
        rules.add(rule("goldenFix.onlyDirectDependencies", null, IGNORED));
        rules.add(rule("goldenFix.versionCatalogs", "versionCatalogs", FILES));
        rules.add(rule("versionCatalogs", "versionCatalogs", FILES));
        rules.addAll(prefixed("goldenFix.versionCatalogRepo.", "versionCatalogRepo.", same(TEXT, "repositoryUrl", "credentialsId", "branch", "path")));
        rules.addAll(prefixed("goldenFix.versionCatalogRepository.", "versionCatalogRepo.", same(TEXT, "repositoryUrl", "credentialsId", "branch", "path")));
        rules.addAll(prefixed("versionCatalogRepo.", "versionCatalogRepo.", same(TEXT, "repositoryUrl", "credentialsId", "branch", "path")));
        rules.addAll(prefixed("versionCatalogRepository.", "versionCatalogRepo.", same(TEXT, "repositoryUrl", "credentialsId", "branch", "path")));
        rules.add(rule("goldenFix.versionCatalogRepo.url", "versionCatalogRepo.repositoryUrl", TEXT));
        rules.add(rule("goldenFix.versionCatalogRepository.url", "versionCatalogRepo.repositoryUrl", TEXT));
        rules.add(rule("versionCatalogRepo.url", "versionCatalogRepo.repositoryUrl", TEXT));
        rules.add(rule("versionCatalogRepository.url", "versionCatalogRepo.repositoryUrl", TEXT));
        rules.add(rule("goldenFix.versionCatalogRepo.targetBranch", "versionCatalogRepo.branch", TEXT));
        rules.add(rule("goldenFix.versionCatalogRepository.targetBranch", "versionCatalogRepo.branch", TEXT));
        rules.add(rule("versionCatalogRepo.targetBranch", "versionCatalogRepo.branch", TEXT));
        rules.add(rule("versionCatalogRepository.targetBranch", "versionCatalogRepo.branch", TEXT));
        for (String tool : List.of("maven", "gradle", "npm", "pip", "pub", "nuget")) {
            rules.add(rule("goldenFix.verify.commands." + tool, "goldenFix.verify" + capitalize(tool) + "Command",
                    TEXT));
        }
        rules.addAll(prefixed("deploy.vm.dod.", "urbanCode.", concat(
                same(TEXT, "siteName", "deployProcess", "deployDescription", "requestProperties"),
                same(FLAG, "skipWait", "deployWithSnapshot", "includeOnlyDeployVersions", "deployOnlyChanged"))));
        for (TestStage stage : TestStage.values()) {
            rules.addAll(prefixed("tests." + stage.configKey() + ".", "tests." + stage.configKey(), List.of(
                    rule("required", "Required", FLAG), rule("maxParallel", "MaxParallel", NUMBER),
                    rule("pollIntervalSec", "PollIntervalSec", NUMBER))));
        }
        List<Rule> openShiftTarget = concat(
                same(TEXT, "buildConfigPath", "dockerFilePath", "buildContext", "addFile", "deployConfigPath",
                        "healthCheckUrl", "deploymentPath", "buildTag", "internalDockerUrl"),
                same(FLAG, "skipConfigDeploy"),
                List.of(rule("projectBuildR", "projectBuild", TEXT), rule("qcDockerRepoPush", "dockerRepoPush", TEXT),
                        rule("qcDockerRepoPull", "dockerRepoPull", TEXT), rule("openshiftCertDir", "certDir", TEXT),
                        rule("nexus.authfile", "nexusAuthFile", TEXT),
                        rule("projectDeploymentR", "projectDeployment", TEXT), rule("configPathR", "configPath", TEXT),
                        rule("routeHostnameR", "routeHostname", TEXT),
                        rule("deploymentRepo.url", "deploymentRepoUrl", TEXT),
                        rule("deploymentRepo.branch", "deploymentRepoBranch", TEXT),
                        rule("deploymentRepo.credentials", "deploymentRepoCredentialsId", TEXT)));
        for (Region region : Region.values()) {
            rules.addAll(prefixed("deploy.vm." + region.configKey() + ".", "sshTargets." + region + ".",
                    same(TEXT, "host", "user", "deployDir", "deployScript", "versionFile")));
            rules.addAll(prefixed("deploy.openshift." + region.configKey() + ".", "openShiftTargets." + region + ".",
                    openShiftTarget));
        }
        return List.copyOf(rules);
    }

    private static List<Rule> databaseRules() {
        List<Rule> database = new ArrayList<>(concat(
                same(TEXT, "changelogFile", "searchPath", "oracleVersion", "defaultSchema", "liquibaseSchema",
                        "contexts", "labels"),
                same(FLAG, "requireRollback", "tag"),
                List.of(rule("parameters", "parameters", MAP), rule("lockWaitMinutes", "lockWaitMinutes", NUMBER),
                        rule("compileCheck.enabled", "compileCheck", FLAG),
                        rule("compileCheck.recompile", "recompile", FLAG),
                        rule("compileCheck.ignoreObjects", "ignoreObjects", LIST),
                        rule("checks.ignoreFiles", "ignoreFiles", LIST), rule("package.groupId", "groupId", TEXT),
                        rule("package.artifactId", "artifactId", TEXT),
                        rule("package.nexusDelivery", "nexusDelivery", FLAG),
                        rule("package.mvnPath", "mvnPath", TEXT), rule("test.reset", "testReset", FLAG),
                        rule("test.utplsql.enabled", "utplsql", FLAG), note("allowedTargets", GLOBAL)),
                prefixed("test.utplsql.", "utplsql", List.of(rule("paths", "Paths", TEXT),
                        rule("owner", "Owner", TEXT), rule("sourcePath", "SourcePath", TEXT),
                        rule("testPath", "TestPath", TEXT), rule("timeoutMinutes", "TimeoutMinutes", NUMBER)))));
        for (String repository : List.of("snapshotRepository", "releaseRepository")) {
            database.addAll(prefixed("package." + repository + ".", repository,
                    List.of(rule("id", "Id", TEXT), rule("url", "Url", TEXT))));
        }
        for (String production : List.of("prod", "production", "prd", "live")) {
            database.add(note(production, NO_PRODUCTION));
        }
        List<Rule> rules = new ArrayList<>(prefixed("database.", "database.", database));
        for (DatabaseTarget target : DatabaseTarget.values()) {
            rules.addAll(prefixed("database." + target.configKey() + ".", "databaseTargets." + target + ".", concat(
                    same(TEXT, "credentialsId", "contexts", "labels", "defaultSchema", "liquibaseSchema"),
                    List.of(rule("url", "url", JDBC), rule("parameters", "parameters", MAP),
                            rule("timeoutMinutes", "timeoutMinutes", NUMBER), note("password", SECRET),
                            note("username", SECRET), note("user", SECRET)))));
        }
        rules.addAll(List.of(note("tools.liquibase", TOOLS_IN_DEFAULTS), note("tools.oracleJdbc", TOOLS_IN_DEFAULTS),
                note("tools.utplsql", TOOLS_IN_DEFAULTS)));
        return List.copyOf(rules);
    }

    private static String text(Object value) {
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
            throw expected("a single value");
        }
        return String.valueOf(value);
    }

    private static List<String> items(Object value, String separator) {
        if (value instanceof Collection<?> items) {
            return items.stream().filter(Objects::nonNull).map(ServiceConfigReader::text).toList();
        }
        return separator == null ? List.of(text(value)) : List.of(text(value).trim().split(separator));
    }

    private static List<String> pairs(Map<?, ?> map) {
        return map.entrySet().stream()
                .map(pair -> pair.getKey() + "=" + text(Objects.requireNonNullElse(pair.getValue(), "")))
                .toList();
    }

    /** A value spelled as the config writes it, in any case, read as the request's spelling. */
    private static <E extends Enum<E>> Conversion choice(E[] constants, Function<E, String> spelling) {
        Map<String, String> choices = Arrays.stream(constants).collect(toMap(constant ->
                spelling.apply(constant).toLowerCase(ROOT), Enum::name, (first, second) -> first, LinkedHashMap::new));
        return value -> {
            String choice = choices.get(text(value).trim().toLowerCase(ROOT));
            if (choice == null) {
                throw expected("one of " + String.join(", ", choices.keySet()));
            }
            return choice;
        };
    }

    private static NotImported expected(String what) {
        return new NotImported("not imported: expected " + what);
    }

    private static Rule rule(String key, String field, Conversion conversion) {
        return new Rule(key, field, conversion);
    }

    private static Rule note(String key, String message) {
        return new Rule(key, null, value -> {
            throw new NotImported(message);
        });
    }

    private static List<Rule> same(Conversion conversion, String... keys) {
        return Arrays.stream(keys).map(key -> rule(key, key, conversion)).toList();
    }

    private static List<Rule> prefixed(String keyPrefix, String fieldPrefix, List<Rule> rules) {
        return rules.stream()
                .map(rule -> rule(keyPrefix + rule.key(), fieldPrefix + rule.field(), rule.conversion()))
                .toList();
    }

    @SafeVarargs
    private static List<Rule> concat(List<Rule>... lists) {
        return Stream.of(lists).flatMap(List::stream).toList();
    }

    /** A config key, the request field it fills and how its value converts. */
    private record Rule(String key, String field, Conversion conversion) {
    }

    /** A command block, such as build.gradle, and the request field of its command. */
    private record Block(String key, String field) {
    }

    @FunctionalInterface
    private interface Conversion {

        Object apply(Object value);
    }

    /** A value the import leaves out; its message is the note. */
    private static final class NotImported extends RuntimeException {

        NotImported(String message) {
            super(message, null, false, false);
        }
    }
}
