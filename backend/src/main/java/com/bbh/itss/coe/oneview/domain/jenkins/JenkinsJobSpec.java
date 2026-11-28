package com.bbh.itss.coe.oneview.domain.jenkins;

import com.bbh.itss.coe.oneview.domain.shared.ValidationProblems;
import lombok.Builder;

import java.util.List;

import static com.bbh.itss.coe.oneview.domain.shared.Text.isUrl;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * The Jenkins job OneView creates for a pipeline: it takes its Jenkinsfile from the repository of the service and
 * builds when the sources change. A Pipeline job polls the repository on its schedule; a Multibranch Pipeline job
 * scans its branches at its interval and can carry the shared library itself.
 */
@Builder(toBuilder = true)
public record JenkinsJobSpec(JobKind kind, String name, String description, String repositoryUrl,
                             String credentialsId, String branch, String scriptPath, List<JobParameter> parameters,
                             BuildRetention retention, boolean concurrentBuildsDisabled, String pollSchedule,
                             String timerSchedule, FolderLibrary library) {

    public static final String DEFAULT_BRANCH = "main";
    public static final String DEFAULT_SCRIPT_PATH = "Jenkinsfile";
    public static final String DEFAULT_POLL_SCHEDULE = "H/5 * * * *";
    public static final String NAME_RULE = "must be a Jenkins job name of at most 200 characters without"
            + " ? * / \\ % ! @ # $ ^ & | < > [ ] : ;";
    public static final String SCHEDULE_PATTERN = "^[A-Za-z0-9 */,@()-]{1,100}$";
    public static final String SCHEDULE_RULE = "must be a Jenkins schedule such as H/5 * * * *";
    private static final String NAME_PATTERN = "^[^?*/\\\\%!@#$^&|<>\\[\\]:;]{1,200}$";

    public JenkinsJobSpec {
        name = trimToNull(name);
        description = trimToNull(description);
        repositoryUrl = trimToNull(repositoryUrl);
        credentialsId = trimToNull(credentialsId);
        branch = defaultIfBlank(branch, DEFAULT_BRANCH).trim().replaceFirst("^\\*/", "");
        scriptPath = defaultIfBlank(scriptPath, DEFAULT_SCRIPT_PATH).trim();
        parameters = List.copyOf(emptyIfNull(parameters));
        pollSchedule = defaultIfBlank(pollSchedule, DEFAULT_POLL_SCHEDULE).trim();
        timerSchedule = trimToNull(timerSchedule);
    }

    public void validate(ValidationProblems problems) {
        if (kind == null || kind == JobKind.OTHER) {
            problems.add("kind", "choose a Pipeline or a Multibranch Pipeline job");
        }
        if (name == null || !name.matches(NAME_PATTERN) || name.equals(".") || name.equals("..")) {
            problems.add("name", NAME_RULE);
        }
        if (!isUrl(repositoryUrl) && (repositoryUrl == null || !repositoryUrl.startsWith("ssh://"))) {
            problems.add("repositoryUrl", "the service needs the http, https or ssh address of its repository,"
                    + " which the job checks out");
        }
        if (scriptPath.startsWith("/") || scriptPath.contains("..")) {
            problems.add("scriptPath", "must be the path of the Jenkinsfile inside the repository");
        }
        if (!pollSchedule.matches(SCHEDULE_PATTERN)) {
            problems.add("pollSchedule", SCHEDULE_RULE);
        }
        if (timerSchedule != null && !timerSchedule.matches(SCHEDULE_PATTERN)) {
            problems.add("timerSchedule", SCHEDULE_RULE);
        }
    }

    /** The interval of the branch scan of a Multibranch Pipeline job in minutes, from the minutes of the schedule. */
    public int scanIntervalMinutes() {
        java.util.regex.Matcher every = java.util.regex.Pattern.compile("^(?:H|\\*)/(\\d{1,2}) ").matcher(pollSchedule);
        return every.find() ? Math.max(1, Integer.parseInt(every.group(1))) : 5;
    }
}
