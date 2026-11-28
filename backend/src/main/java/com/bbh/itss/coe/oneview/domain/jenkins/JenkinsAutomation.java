package com.bbh.itss.coe.oneview.domain.jenkins;

import com.bbh.itss.coe.oneview.domain.shared.ValidationProblems;

import java.time.Instant;

import static com.bbh.itss.coe.oneview.domain.shared.Versions.requireReadAt;
import static java.util.Objects.requireNonNull;

/** The stored settings of the Jenkins automation with the version they were read at. */
public record JenkinsAutomation(JenkinsAutomationSettings settings, long version, Instant updatedAt) {

    public static final JenkinsAutomation NOT_SET = new JenkinsAutomation(JenkinsAutomationSettings.NONE, 0, null);

    public JenkinsAutomation {
        requireNonNull(settings, "settings");
    }

    /**
     * The OIS API key is never shown, so a change without one keeps the stored key while the generic user stays;
     * clearing the generic user removes the key.
     */
    public JenkinsAutomation change(Long expectedVersion, JenkinsAutomationSettings changed) {
        requireReadAt(expectedVersion, version);
        ValidationProblems problems = new ValidationProblems();
        changed.validate(problems);
        problems.throwIfAny();
        boolean keepsKey = changed.oisApiKey() == null && changed.genericUser() != null;
        return new JenkinsAutomation(keepsKey ? changed.withOisApiKey(settings.oisApiKey()) : changed, version,
                updatedAt);
    }
}
