package com.bbh.itss.coe.oneview.domain.qa;

import com.bbh.itss.coe.oneview.domain.shared.ValidationProblems;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.bbh.itss.coe.oneview.domain.shared.Failures.staleVersion;
import static java.util.regex.Pattern.UNICODE_CHARACTER_CLASS;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * How an application (a product of the catalog) takes part in the QM Metric Tool: whether it does, its QA lead,
 * where its quality data is (SonarQube project, defect project, Grafana dashboard) and the targets it sets for
 * itself. The version is {@code null} while the profile has never been saved.
 */
public record QualityProfile(long productId, boolean enabled, String qaLead, String sonarProjectKey,
                             String defectProjectKey, String dashboardUrl, QualityTargets targets, Long version,
                             Instant updatedAt) {

    public static final int MAX_NAME = 200;
    public static final int MAX_SONAR_KEY = 400;
    public static final int MAX_DEFECT_KEY = 100;
    public static final int MAX_URL = 1000;
    public static final String DASHBOARD_URL_MESSAGE =
            "must be an http:// or https:// address without spaces, double quotes, backslashes, $ or backticks";
    /** The addresses the form of the portal takes: no blank of any kind, double quote, backslash, $ or backtick. */
    private static final Pattern DASHBOARD_URL = Pattern.compile("^https?://[^\\s\\x{FEFF}$`\"\\\\]+$",
            UNICODE_CHARACTER_CLASS);

    public QualityProfile {
        qaLead = trimToNull(qaLead);
        sonarProjectKey = trimToNull(sonarProjectKey);
        defectProjectKey = trimToNull(defectProjectKey);
        dashboardUrl = trimToNull(dashboardUrl);
        targets = getIfNull(targets, QualityTargets.NONE);
    }

    /** The profile of an application that has never been set up for the QM Metric Tool. */
    public static QualityProfile none(long productId) {
        return new QualityProfile(productId, false, null, null, null, null, QualityTargets.NONE, null, null);
    }

    public QualityProfile change(Long expectedVersion, QualityProfile changed) {
        if (!Objects.equals(expectedVersion, version)) {
            throw staleVersion();
        }
        QualityProfile next = new QualityProfile(productId, changed.enabled(), changed.qaLead(),
                changed.sonarProjectKey(), changed.defectProjectKey(), changed.dashboardUrl(), changed.targets(),
                version, updatedAt);
        next.check();
        return next;
    }

    /** The targets of this application, with the ones of BBH where it sets none. */
    public QualityTargets effectiveTargets(QualityDefaults defaults) {
        return targets.over(defaults.targets());
    }

    private void check() {
        ValidationProblems problems = new ValidationProblems()
                .fits("qaLead", qaLead, MAX_NAME)
                .fits("sonarProjectKey", sonarProjectKey, MAX_SONAR_KEY)
                .fits("defectProjectKey", defectProjectKey, MAX_DEFECT_KEY)
                .fits("dashboardUrl", dashboardUrl, MAX_URL);
        if (dashboardUrl != null && !DASHBOARD_URL.matcher(dashboardUrl).matches()) {
            problems.add("dashboardUrl", DASHBOARD_URL_MESSAGE);
        }
        targets.check(problems.at("targets"), false);
        problems.throwIfAny();
    }
}
