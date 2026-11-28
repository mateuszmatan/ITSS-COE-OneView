package com.bbh.itss.coe.oneview.application.jenkins.port.in;

import com.bbh.itss.coe.oneview.domain.discovery.DiscoveredStack;
import com.bbh.itss.coe.oneview.domain.jenkins.BuildRetention;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSnapshot;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsfileFacts;
import com.bbh.itss.coe.oneview.domain.jenkins.JobKind;
import com.bbh.itss.coe.oneview.domain.jenkins.JobParameter;

import java.util.List;

/** What the guided set-up of a pipeline does outside OneView: read the sources, read a Jenkins job, create one. */
public interface GuidedPipelineSetupUseCase {

    /** Reads the sources of the repository and detects what they show. Changes nothing. */
    SourceInspection inspectSources(String repositoryUrl, String branch);

    /** Reads a Jenkins job and its last build, or the build its address names. Changes nothing. */
    JenkinsJobSnapshot readJenkinsJob(String jobUrl);

    /** Creates the Jenkins job of the pipeline as the generic user and notes its address on the pipeline. */
    CreatedJenkinsJob createJenkinsJob(long pipelineId, CreateJenkinsJobCommand command);

    record SourceInspection(DiscoveredStack stack, String branch, String jenkinsfilePath, JenkinsfileFacts jenkinsfile,
                            boolean readAsGenericUser) {
    }

    record CreateJenkinsJobCommand(String jenkinsUrl, String folder, String name, JobKind kind, String repositoryUrl,
                                   String branch, String scriptPath, String credentialsId, String description,
                                   List<JobParameter> parameters, BuildRetention retention,
                                   boolean concurrentBuildsDisabled, String timerSchedule) {
    }

    record CreatedJenkinsJob(long pipelineId, String jobUrl, String jobPath, JobKind kind, String trigger,
                             String jenkinsfilePath, String jenkinsfile, boolean libraryAttached) {
    }
}
