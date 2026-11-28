package com.bbh.itss.coe.oneview.domain.jenkins;

import com.bbh.itss.coe.oneview.domain.shared.ValidationProblems;
import lombok.Builder;
import lombok.With;

import java.util.ArrayList;
import java.util.List;

import static com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSpec.DEFAULT_POLL_SCHEDULE;
import static com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSpec.SCHEDULE_PATTERN;
import static com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSpec.SCHEDULE_RULE;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * How OneView reaches Jenkins and Bitbucket by itself: as a generic user whose secret it fetches from OIS for every
 * request and never stores. Without the generic user, the OIS host and the OIS API key, OneView reads and creates
 * nothing by itself and its users enter their settings and create their Jenkins jobs themselves.
 */
@Builder(toBuilder = true)
public record JenkinsAutomationSettings(String genericUser, String oisHost, @With String oisApiKey,
                                        String pollSchedule, String libraryRepositoryUrl, String libraryVersion,
                                        String libraryCredentialsId) {

    public static final JenkinsAutomationSettings NONE = builder().build();
    public static final String DEFAULT_LIBRARY_VERSION = "main";
    public static final String OIS_HOST_PATTERN = "^[A-Za-z0-9.-]+(:[0-9]{1,5})?$";
    public static final String OIS_HOST_RULE = "must be a host name, with :port when OIS does not listen on 443";

    public JenkinsAutomationSettings {
        genericUser = trimToNull(genericUser);
        oisHost = trimToNull(oisHost);
        oisApiKey = genericUser == null ? null : trimToNull(oisApiKey);
        pollSchedule = defaultIfBlank(pollSchedule, DEFAULT_POLL_SCHEDULE).trim();
        libraryRepositoryUrl = trimToNull(libraryRepositoryUrl);
        libraryVersion = defaultIfBlank(libraryVersion, DEFAULT_LIBRARY_VERSION).trim();
        libraryCredentialsId = trimToNull(libraryCredentialsId);
    }

    public boolean ready() {
        return missing().isEmpty();
    }

    /** The settings that are still needed before OneView can act as the generic user. */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (genericUser == null) {
            missing.add("the generic user");
        }
        if (oisHost == null) {
            missing.add("the OIS host");
        }
        if (oisApiKey == null) {
            missing.add("the OIS API key");
        }
        return missing;
    }

    public void validate(ValidationProblems problems) {
        problems.fits("genericUser", genericUser, 200).fits("oisHost", oisHost, 255)
                .fits("oisApiKey", oisApiKey, 500).fits("libraryRepositoryUrl", libraryRepositoryUrl, 1000)
                .fits("libraryVersion", libraryVersion, 200).fits("libraryCredentialsId", libraryCredentialsId, 200);
        if (oisHost != null && !oisHost.matches(OIS_HOST_PATTERN)) {
            problems.add("oisHost", OIS_HOST_RULE);
        }
        if (!pollSchedule.matches(SCHEDULE_PATTERN)) {
            problems.add("pollSchedule", SCHEDULE_RULE);
        }
    }

    /** The library a Multibranch Pipeline job carries itself, when its repository is set. */
    public FolderLibrary library(String name) {
        return libraryRepositoryUrl == null ? null
                : new FolderLibrary(name, libraryRepositoryUrl, libraryVersion, libraryCredentialsId);
    }
}
