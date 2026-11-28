package com.bbh.itss.coe.oneview.domain.jenkins;

import lombok.Builder;

import java.util.List;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

/**
 * What OneView read from an existing Jenkins job and one of its builds, without changing anything in Jenkins. The
 * notes say what it could not read or does not carry over to a new job.
 */
@Builder(toBuilder = true)
public record JenkinsJobSnapshot(String jenkinsUrl, String jobPath, String jobUrl, JobKind kind, String description,
                                 String repositoryUrl, String branch, String credentialsId, String scriptPath,
                                 String agentLabel, List<JobParameter> parameters, BuildRetention retention,
                                 boolean concurrentBuildsDisabled, List<JobTrigger> triggers, List<String> libraries,
                                 JenkinsfileFacts inlineScript, LastBuild lastBuild, List<String> notes) {

    public JenkinsJobSnapshot {
        parameters = List.copyOf(emptyIfNull(parameters));
        triggers = List.copyOf(emptyIfNull(triggers));
        libraries = List.copyOf(emptyIfNull(libraries));
        notes = List.copyOf(emptyIfNull(notes));
    }
}
