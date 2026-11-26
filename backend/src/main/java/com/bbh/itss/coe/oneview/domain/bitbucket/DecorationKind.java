package com.bbh.itss.coe.oneview.domain.bitbucket;

import java.util.Arrays;

import static java.util.Locale.ROOT;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

/** The pull request a decoration was posted on. */
public enum DecorationKind {

    /** The pull request of the branch the pipeline analysed. */
    BRANCH,

    /** The static security pull request of a service, with the findings of Nexus IQ, AppScan SAST and SonarQube. */
    STATIC,

    /** The DAST pull request of a service. */
    DAST;

    /** The kind of that name, in any case, or null for any other name, so that a kind OneView does not know is kept. */
    public static DecorationKind of(String name) {
        String wanted = trimToEmpty(name).toUpperCase(ROOT);
        return Arrays.stream(values()).filter(kind -> kind.name().equals(wanted)).findFirst().orElse(null);
    }
}
