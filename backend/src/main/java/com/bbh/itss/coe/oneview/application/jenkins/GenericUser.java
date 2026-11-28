package com.bbh.itss.coe.oneview.application.jenkins;

import com.bbh.itss.coe.oneview.application.jenkins.port.out.GenericUserSecretPort;
import com.bbh.itss.coe.oneview.domain.jenkins.GenericLogin;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomationSettings;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

/** Signs the generic user in: its secret comes from OIS for this one request and is kept nowhere. */
@NoArgsConstructor(access = PRIVATE)
public final class GenericUser {

    public static final String NOT_SET_UP = "OneView cannot act in Jenkins by itself yet: an administrator has to set"
            + " %s under Admin, Jenkins automation. Until then, skip this step and do it yourself.";

    /** The login of the generic user; refuses when the automation is not set up. */
    public static GenericLogin signIn(JenkinsAutomationSettings settings, GenericUserSecretPort secrets) {
        if (!settings.ready()) {
            throw new IllegalStateException(NOT_SET_UP.formatted(String.join(", ", settings.missing())));
        }
        return new GenericLogin(settings.genericUser(), secrets.fetch(settings.oisHost(), settings.oisApiKey()));
    }

    /** The login of the generic user, or null to read anonymously when the automation is not set up. */
    public static GenericLogin signInIfSetUp(JenkinsAutomationSettings settings, GenericUserSecretPort secrets) {
        return settings.ready() ? signIn(settings, secrets) : null;
    }
}
