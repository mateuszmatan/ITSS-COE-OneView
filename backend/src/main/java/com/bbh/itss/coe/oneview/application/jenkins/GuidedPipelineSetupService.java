package com.bbh.itss.coe.oneview.application.jenkins;

import com.bbh.itss.coe.oneview.application.UseCase;
import com.bbh.itss.coe.oneview.application.WithoutTransaction;
import com.bbh.itss.coe.oneview.application.discovery.port.out.BitbucketRepositoryPort;
import com.bbh.itss.coe.oneview.application.jenkins.port.in.GuidedPipelineSetupUseCase;
import com.bbh.itss.coe.oneview.application.jenkins.port.out.GenericUserSecretPort;
import com.bbh.itss.coe.oneview.application.jenkins.port.out.JenkinsAutomationRepositoryPort;
import com.bbh.itss.coe.oneview.application.jenkins.port.out.JenkinsPort;
import com.bbh.itss.coe.oneview.application.pipeline.port.out.PipelineRepositoryPort;
import com.bbh.itss.coe.oneview.application.settings.port.out.GlobalSettingsRepositoryPort;
import com.bbh.itss.coe.oneview.domain.bitbucket.RepositoryAddress;
import com.bbh.itss.coe.oneview.domain.discovery.RepoFileTree;
import com.bbh.itss.coe.oneview.domain.discovery.RepoStackDetector;
import com.bbh.itss.coe.oneview.domain.jenkins.GenericLogin;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomation;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomationSettings;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobAddress;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSnapshot;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSpec;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsfileFacts;
import com.bbh.itss.coe.oneview.domain.jenkins.Jenkinsfiles;
import com.bbh.itss.coe.oneview.domain.jenkins.JobKind;
import com.bbh.itss.coe.oneview.domain.pipeline.Pipeline;
import com.bbh.itss.coe.oneview.domain.settings.GlobalSettings;
import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException;
import com.bbh.itss.coe.oneview.domain.shared.ValidationProblems;
import lombok.RequiredArgsConstructor;

import static com.bbh.itss.coe.oneview.application.jenkins.GenericUser.signIn;
import static com.bbh.itss.coe.oneview.application.jenkins.GenericUser.signInIfSetUp;
import static com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSpec.DEFAULT_BRANCH;
import static com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSpec.DEFAULT_SCRIPT_PATH;
import static com.bbh.itss.coe.oneview.domain.shared.Failures.notFound;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;

@UseCase
@RequiredArgsConstructor
public class GuidedPipelineSetupService implements GuidedPipelineSetupUseCase {

    static final String NO_KEY = "The pipeline has no active key. Issue a new key on the page of the pipeline first:"
            + " the Jenkinsfile of the job needs it.";

    private final JenkinsAutomationRepositoryPort automation;
    private final GenericUserSecretPort secrets;
    private final BitbucketRepositoryPort bitbucket;
    private final JenkinsPort jenkins;
    private final PipelineRepositoryPort pipelines;
    private final GlobalSettingsRepositoryPort globalSettings;

    @Override
    @WithoutTransaction
    public SourceInspection inspectSources(String repositoryUrl, String branch) {
        if (isBlank(repositoryUrl)) {
            throw InvalidRequestException.of("repositoryUrl", "give the address of the Bitbucket repository");
        }
        String read = defaultIfBlank(branch, DEFAULT_BRANCH).trim();
        GenericLogin login = signInIfSetUp(settings(), secrets);
        RepoFileTree sources = bitbucket.readSources(repositoryUrl.trim(), read, login);
        String jenkinsfile = sources.readFile(DEFAULT_SCRIPT_PATH).orElse(null);
        return new SourceInspection(RepoStackDetector.detect(sources), read,
                sources.fileExists(DEFAULT_SCRIPT_PATH) ? DEFAULT_SCRIPT_PATH : null,
                JenkinsfileFacts.read(jenkinsfile), login != null);
    }

    @Override
    @WithoutTransaction
    public JenkinsJobSnapshot readJenkinsJob(String jobUrl) {
        return jenkins.readJob(JenkinsJobAddress.ofJob("jobUrl", jobUrl), signInIfSetUp(settings(), secrets));
    }

    @Override
    public CreatedJenkinsJob createJenkinsJob(long pipelineId, CreateJenkinsJobCommand command) {
        JenkinsAutomationSettings settings = settings();
        Pipeline pipeline = pipelines.loadForUpdate(pipelineId).orElseThrow(() -> notFound("Pipeline", pipelineId));
        String key = pipeline.activeKey().orElseThrow(() -> new IllegalStateException(NO_KEY)).value();
        String library = globalSettings.load().map(GlobalSettings::platform).map(platform -> platform.jenkinsLibrary())
                .orElse(Jenkinsfiles.DEFAULT_LIBRARY);
        JenkinsJobSpec job = job(command, settings, library);
        JenkinsJobAddress folder = JenkinsJobAddress.ofFolder("jenkinsUrl", command.jenkinsUrl(), command.folder());
        JenkinsJobAddress address = folder.child(job.name());

        GenericLogin login = signIn(settings, secrets);
        if (jenkins.exists(address, login)) {
            throw new IllegalStateException("Jenkins already has " + address.url() + ". OneView never changes an"
                    + " existing job: choose another name or folder.");
        }
        jenkins.createJob(folder, job, login);
        pipeline.reconfigure(pipeline.version(), pipeline.type(),
                pipeline.settings().toBuilder().jenkinsJob(address.url()).build());
        pipelines.save(pipeline);
        return new CreatedJenkinsJob(pipelineId, address.url(), address.path(), job.kind(), trigger(job),
                job.scriptPath(), Jenkinsfiles.withKey(library, pipeline.type(), key), job.library() != null);
    }

    private static JenkinsJobSpec job(CreateJenkinsJobCommand command, JenkinsAutomationSettings settings,
                                      String library) {
        JenkinsJobSpec job = JenkinsJobSpec.builder().kind(command.kind()).name(command.name())
                .description(command.description())
                .repositoryUrl(isBlank(command.repositoryUrl()) ? null
                        : RepositoryAddress.cloneUrlOf(command.repositoryUrl()))
                .credentialsId(command.credentialsId()).branch(command.branch()).scriptPath(command.scriptPath())
                .parameters(command.parameters()).retention(command.retention())
                .concurrentBuildsDisabled(command.concurrentBuildsDisabled()).pollSchedule(settings.pollSchedule())
                .timerSchedule(command.timerSchedule())
                .library(command.kind() == JobKind.MULTIBRANCH ? settings.library(library) : null).build();
        ValidationProblems problems = new ValidationProblems();
        job.validate(problems);
        problems.throwIfAny();
        return job;
    }

    private static String trigger(JenkinsJobSpec job) {
        return job.kind() == JobKind.MULTIBRANCH
                ? "Jenkins scans the branches of the repository every " + job.scanIntervalMinutes()
                + " minutes and builds every branch that changed and has a " + job.scriptPath() + "."
                : "Jenkins polls branch " + job.branch() + " of the repository on the schedule " + job.pollSchedule()
                + " and builds when it changed.";
    }

    private JenkinsAutomationSettings settings() {
        return automation.load().map(JenkinsAutomation::settings).orElse(JenkinsAutomationSettings.NONE);
    }
}
