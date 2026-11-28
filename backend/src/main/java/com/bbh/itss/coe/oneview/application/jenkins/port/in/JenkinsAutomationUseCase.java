package com.bbh.itss.coe.oneview.application.jenkins.port.in;

import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomation;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomationSettings;

public interface JenkinsAutomationUseCase {

    JenkinsAutomation current();

    JenkinsAutomation update(Long expectedVersion, JenkinsAutomationSettings settings);

    /**
     * Fetches the secret of the generic user from OIS and signs in to the Jenkins with it, changing nothing. A failure
     * is the result, with its reason, not an exception.
     */
    ConnectionCheck check(String jenkinsUrl);

    record ConnectionCheck(boolean ok, String message) {
    }
}
