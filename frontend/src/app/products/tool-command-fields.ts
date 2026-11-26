import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { BuildTool, CommandPath, commandParts, dotnetTool, scripted } from '../core/models';
import { Field, Fields, area, check, line, mono } from '@common/shared/fields';
import { SHELL_SAFE_ERROR } from '@common/shared/form-controls';
import { ToolCommandForm } from './product-form-model';

const SCRIPTED_NAMES: Partial<Record<BuildTool, string>> = {
  NPM: 'npm',
  PIP: 'pip',
  IOS: 'ios',
};

const SCRIPTED_BUILD: Partial<Record<BuildTool, string>> = {
  PIP: 'pip install -r requirements.txt',
  IOS: 'xcodebuild -scheme MyApp -configuration Release build',
};

const SCRIPTED_TEST: Partial<Record<BuildTool, string>> = {
  NPM: 'npm test',
  PIP: 'pytest --junitxml=junit.xml',
  IOS: 'xcodebuild test -scheme MyApp -destination "platform=iOS Simulator,name=iPhone 15"',
};

/**
 * The fields of a command of npm, pip or iOS: one command line for the build and the unit tests, and the flags of the
 * SonarScanner. The parts the library does not read for these tools are left out.
 */
function scriptedFields(tool: BuildTool, path: string): Field[] {
  const name = SCRIPTED_NAMES[tool];
  const parts = path
    ? (commandParts(tool, path as CommandPath) ?? [])
    : ['tasks', 'directory', 'flags'];
  const tests = path === 'tests.unitTests';
  const fields: Field[] = [
    mono(
      'tasks',
      tests ? 'Test command' : 'Build command',
      tests ? `${path}.commands` : `build.${name}.commands`,
      6,
      {
        placeholder: (tests ? SCRIPTED_TEST : SCRIPTED_BUILD)[tool] ?? '',
        hint: `one command line; chain several with &&${tests ? `; left empty: ${SCRIPTED_TEST[tool] ?? 'no tests'}` : ''}`,
        help: tests
          ? 'The whole command line the Unit tests stage runs, with sh on Linux agents and bat on Windows agents. Write its options in the line itself.'
          : 'The whole command line the Build artifact stage runs in the source folder of the service, with sh on Linux agents and bat on Windows agents. Left empty, the stage builds nothing.',
      },
    ),
    mono('directory', 'Directory', `${path}.dir`, 6, {
      placeholder: '.',
      hint: 'for a service in a subfolder: that folder',
      help: 'The folder of the workspace in which the command runs. Left empty: the workspace root.',
    }),
    area('flags', 'SonarScanner flags', `${path}.${name}.flags`, 6, {
      mono: true,
      placeholder: '-Dsonar.sources=src\n-Dsonar.exclusions=**/node_modules/**',
      hint: 'one per line',
      help: 'The library runs the SonarScanner for this build tool; these properties are added to its command line, such as -Dsonar.sources=src or -Dsonar.exclusions=... A property given here wins over the one the library adds.',
    }),
  ];
  return fields.filter((field) => parts.includes(field.key));
}

/** The three places of a Liquibase step of a Gradle, Android, Maven or .NET service, by the start of their keys. */
export type StepPath = 'build' | 'tests.unitTests' | 'asoc';

/** The name the library gives a Liquibase step without a label of its own, followed by its commands. */
const STEP_NAMES: Record<StepPath, string> = {
  build: 'Liquibase',
  'tests.unitTests': 'Liquibase test',
  asoc: 'Liquibase SAST',
};

const STEP_COMMANDS: Record<StepPath, string> = {
  build:
    'The commands the step passes to liquibase after the build, for example validate or updateSQL. Left empty while Flags are set, it runs validate. A failure of the step fails the build.',
  'tests.unitTests':
    'The commands the step passes to liquibase after the unit tests, for example validate or status. Left empty while Flags are set, it runs validate. A failure of the step does not stop the pipeline: the Unit tests stage turns orange and the build unstable.',
  asoc: 'The commands the step passes to liquibase after the SAST compile of a Gradle, Android or Maven service, for example validate or updateSQL. Left empty while Flags are set, it runs validate. With the whole step left empty, the scan runs the Liquibase step of the build, if the service has one.',
};

/**
 * The fields of the Liquibase step that the library runs after the build, after the unit tests or after the AppScan
 * compile of a Gradle, Android, Maven or .NET service: the liquibase command with its flags and commands, under
 * build.liquibase, tests.unitTests.liquibase or asoc.liquibase. The step runs no Maven, so it has no Maven home, and
 * its labels name Liquibase, so that they differ from those of the command of the build tool next to them.
 */
export function liquibaseStepFields(path: StepPath): Field[] {
  const key = `${path}.liquibase`;
  return [
    mono('tasks', 'Liquibase commands', `${key}.commands`, 6, {
      placeholder: path === 'asoc' ? 'updateSQL' : 'validate',
      hint: 'separated by spaces',
      help: STEP_COMMANDS[path],
    }),
    mono('directory', 'Liquibase directory', `${key}.dir`, 6, {
      placeholder: '.',
      hint: 'the paths of the Flags start in this folder',
      help: 'The folder of the workspace in which liquibase runs; a relative --changelog-file of the Flags starts there. Left empty: the workspace root.',
    }),
    area('flags', 'Liquibase flags', `${key}.flags`, 6, {
      mono: true,
      placeholder: '--changelog-file=db/changelog.xml\n--url=offline:oracle',
      hint: 'one per line',
      help: 'Liquibase options added before the commands, such as --changelog-file=..., --defaults-file=... or --url=offline:oracle.',
    }),
    area('environment', 'Liquibase environment variables', `${key}.env`, 6, {
      mono: true,
      placeholder: 'LIQUIBASE_LOG_LEVEL=INFO',
      hint: 'one NAME=value per line',
      help: 'Environment variables set for the liquibase command only.',
    }),
    line('label', 'Liquibase step label', `${key}.label`, 6, {
      hint: 'the name Jenkins shows for the step',
      help: `Left empty, the step is named ${STEP_NAMES[path]}: and its commands, for example ${STEP_NAMES[path]}: validate.`,
    }),
    check('returnStdout', 'Return the Liquibase output to the pipeline', `${key}.returnStdout`, 6, {
      help: 'Captures the console output of liquibase instead of printing it to the build log; the pipeline does not use the captured output.',
    }),
  ];
}

/** The fields of a Gradle or Maven command whose devsecops_config.yaml keys start with path. */
export function commandFields(
  tool: BuildTool,
  path: string,
  gradleExample = '',
  mavenExample = '',
  liquibaseExample = 'updateSQL',
): Field[] {
  if (scripted(tool)) {
    return scriptedFields(tool, path);
  }
  const maven = tool === 'MAVEN';
  const liquibase = tool === 'LIQUIBASE' || tool === 'SQL';
  const dotnet = dotnetTool(tool);
  const msbuild = tool === 'MSBUILD';
  const unitTests = path === 'tests.unitTests';
  const sonar = path === 'tools.sonar';
  /** The library runs the automatic .NET unit tests only when the service writes a value under tests.unitTests. */
  const dotnetSkip =
    ' The Unit tests stage runs them only when the service sets a value under tests.unitTests, such as Test results, a Directory or Flags under Advanced settings, or a Liquibase step after the unit tests; with none set, it skips the unit tests.';
  const toolName = dotnet ? 'dotnet' : liquibase ? 'liquibase' : maven ? 'maven' : 'gradle';
  const key = `${path}.${toolName}`;
  const taskKey = liquibase || dotnet ? 'commands' : maven ? 'goals' : 'tasks';
  const taskLabel = dotnet
    ? '.NET commands'
    : liquibase
      ? 'Liquibase commands'
      : maven
        ? 'Maven goals'
        : 'Gradle tasks';
  const taskPlaceholder = msbuild
    ? 'msbuild MySolution.sln /t:Build /p:Configuration=Release'
    : dotnet
      ? 'dotnet build MySolution.sln -c Release'
      : liquibase
        ? liquibaseExample
        : maven
          ? mavenExample
          : gradleExample;
  const taskHelp = msbuild
    ? unitTests
      ? 'Optional full commands that replace the automatic MSBuild unit tests. Left empty, the library runs the test assemblies with vstest.console.exe (dotnet test for SDK-style projects without them).' +
        dotnetSkip
      : sonar
        ? 'Optional full commands that replace the build of the analysis. Left empty, the library wraps the .NET commands of the build in SonarScanner.MSBuild.exe begin and end, or, when those are empty too, its MSBuild build: NuGet.exe restore, then MSBuild.exe /t:Rebuild /p:Configuration=Release.'
        : path === 'asoc'
          ? 'Optional full commands that replace the automatic MSBuild build before the SAST scan. Left empty, the library runs the .NET commands of the build, or, when those are empty too, restores with NuGet.exe and builds with MSBuild.exe /t:Build /p:Configuration=Release.'
          : 'Optional full commands that replace the automatic MSBuild build. Left empty, the library restores with NuGet.exe and builds with MSBuild.exe /t:Build /p:Configuration=Release.'
    : dotnet
      ? unitTests
        ? 'Optional full commands that replace the automatic .NET unit tests. Left empty, the library runs the tests of the mode it detects from the repository (dotnet test, vstest.console.exe or both, Cake or NUKE) with coverage.' +
          dotnetSkip
        : 'Optional full commands that replace the automatic .NET step. Left empty, the library detects the mode from the repository (dotnet SDK CLI, classic MSBuild + NuGet, hybrid, Cake or NUKE) and runs restore and build, or the SonarScanner for .NET begin/build/end.'
      : liquibase
        ? path === 'tools.sonar'
          ? 'SonarScanner runs automatically for SQL and PL/SQL files; set Flags under Advanced settings when overriding -Dsonar.sources, -Dsonar.inclusions, or -Dsonar.plsql.file.suffixes.'
          : path === 'tests.unitTests'
            ? 'The Liquibase command run during the Unit tests stage (for example validate or status). Line coverage and JUnit XML checks are skipped automatically when empty.'
            : path === 'asoc'
              ? 'The Liquibase command run before AppScan SAST packaging (for example updateSQL or validate) so rendered SQL and changelog scripts are scanned together with PL/SQL sources.'
              : 'The commands passed to liquibase, for example updateSQL, validate, or status. When left empty, the library runs updateSQL, with --changelog-file= and the changelog it finds at the repository root (else src/main/resources/changelog.xml) when Flags is empty too; for a service with database changes it runs validate when Flags is set, else nothing. A new service is prefilled with updateSQL.'
        : maven
          ? 'The goals the step passes to Maven: the mvn of the Maven home, otherwise ./mvnw when the repository has it, otherwise the mvn of the agent.'
          : 'The tasks the step passes to the Gradle wrapper (./gradlew) of the repository.';
  /** The library adds the flags of a .NET step to its automatic commands only, never to given .NET commands. */
  const dotnetFlagsHelp = sonar
    ? `Added to the begin step of the SonarScanner for .NET, such as /d:sonar.exclusions=**/Migrations/**. While neither the SonarQube nor the build .NET commands are given, the library also adds them to the build of the analysis (${msbuild ? 'MSBuild.exe' : 'MSBuild.exe or dotnet build'}), in place of the Flags of the build; to pass /d: properties to the begin step only, write the build of the analysis in the .NET commands of SonarQube.`
    : unitTests
      ? `Options added to the automatic ${msbuild ? 'vstest.console.exe or dotnet test' : 'test runner'} command; not used when .NET commands are given, which run as they are written.`
      : path === 'asoc'
        ? `Options added to the automatic ${msbuild ? 'MSBuild.exe' : '.NET'} build before the SAST scan, such as /m; left empty, the Flags of the build apply. Not used when the AppScan or the build .NET commands are given.`
        : `Options added to the automatic ${msbuild ? 'MSBuild.exe' : 'MSBuild.exe, dotnet build, Cake or NUKE'} command, such as /m; not used when .NET commands are given, which run as they are written.`;
  return [
    mono('tasks', taskLabel, `${key}.${taskKey}`, 6, {
      placeholder: taskPlaceholder,
      hint: 'separated by spaces',
      help: taskHelp,
    }),
    mono('directory', 'Directory', `${key}.dir`, maven ? 3 : 6, {
      placeholder: '.',
      hint: 'for a service in a subfolder: that folder',
      help:
        liquibase && path === 'tools.sonar'
          ? 'The folder of the workspace in which sonar-scanner runs; SonarQube analyses only the files under it. For a service with database changes it must be "." or a folder that holds the package folder and, while the utPLSQL tests run, the folder of the test packages. Left empty: the workspace root.'
          : 'The folder of the workspace in which the command runs. Left empty: the workspace root.',
    }),
    ...(maven
      ? [
          mono('mavenHome', 'Maven home', `${key}.mvnPath`, 3, {
            placeholder: '/opt/maven',
            error: SHELL_SAFE_ERROR,
            help: 'The Maven installation on the agent. The step then runs its bin/mvn with M2_HOME set to this folder.',
          }),
        ]
      : []),
    area('flags', 'Flags', `${key}.flags`, 6, {
      mono: true,
      placeholder: liquibase
        ? path === 'tools.sonar'
          ? '-Dsonar.sources=db\n-Dsonar.plsql.file.suffixes=.sql,.pls,.pks,.pkb'
          : '--changelog-file=src/main/resources/changelog.xml'
        : maven
          ? '-B'
          : dotnet
            ? sonar
              ? '/d:sonar.exclusions=**/Migrations/**'
              : '-p:TreatWarningsAsErrors=false'
            : '--refresh-dependencies',
      hint: 'one per line',
      help:
        liquibase && path === 'tools.sonar'
          ? 'SonarScanner properties passed to sonar-scanner, such as -Dsonar.sources=db, -Dsonar.inclusions=**/*.sql,**/*.pkb, or -Dsonar.plsql.file.suffixes=.sql,.pls,.pks,.pkb.'
          : liquibase
            ? 'Liquibase options added before the command, such as --changelog-file=..., --defaults-file=..., or --url=offline:oracle.'
            : dotnet
              ? dotnetFlagsHelp
              : 'Command line options added after the goals or tasks, such as system properties or profiles.',
    }),
    area('environment', 'Environment variables', `${key}.env`, 6, {
      mono: true,
      placeholder: 'JAVA_OPTS=-Xmx1g',
      hint: 'one NAME=value per line',
      help: 'Environment variables set for this command only.',
    }),
    line('label', 'Step label', `${key}.label`, 6, {
      hint: dotnet
        ? 'the name Jenkins shows for the .NET commands'
        : 'the name Jenkins shows for the step',
      help: dotnet
        ? `Names the given .NET commands in Jenkins; left empty: .NET ${unitTests ? 'Test' : 'Build'}: and the commands. The automatic steps keep their own names, such as ${unitTests ? 'VSTest unit tests and dotnet test' : 'NuGet restore and MSBuild Build'}.`
        : 'Left empty, the step is named after the tool and the command, for example Gradle: clean build.',
    }),
    check('returnStdout', 'Return the output to the pipeline', `${key}.returnStdout`, 6, {
      help: dotnet
        ? 'Not used for .NET: the library prints the output of every .NET step to the build log.'
        : 'Captures the console output of the command instead of printing it to the build log; the pipeline does not use the captured output.',
    }),
  ];
}

@Component({
  selector: 'ov-tool-command-fields',
  imports: [Fields],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `<ov-fields [group]="group()" [fields]="fields()" />`,
  styles: `
    :host {
      display: contents;
    }
  `,
})
export class ToolCommandFields {
  readonly group = input.required<ToolCommandForm>();
  readonly tool = input.required<BuildTool>();
  readonly path = input.required<string>();
  readonly gradleExample = input('');
  readonly mavenExample = input('');
  readonly liquibaseExample = input('updateSQL');
  readonly part = input<'all' | 'tasks' | 'options' | 'flags'>('all');
  /** Whether the group is the Liquibase step after the command of the path, rather than that command. */
  readonly step = input(false);

  protected fields(): Field[] {
    const part = this.part();
    const fields = this.step()
      ? liquibaseStepFields(this.path() as StepPath)
      : commandFields(
          this.tool(),
          this.path(),
          this.gradleExample(),
          this.mavenExample(),
          this.liquibaseExample(),
        );
    if (part === 'flags') {
      return fields.filter((field) => field.key === 'flags' || field.key === 'environment');
    }
    return part === 'all'
      ? fields
      : fields.filter((field) => (field.key === 'tasks') === (part === 'tasks'));
  }
}
