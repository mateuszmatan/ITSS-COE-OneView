package com.bbh.itss.coe.oneview.domain.jenkins;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.apache.commons.collections4.MapUtils.emptyIfNull;

/** The build of a Jenkins job that OneView read: its number, its result and the parameter values it ran with. */
public record LastBuild(int number, String result, Map<String, String> parameters, String branch,
                        String repositoryUrl) {

    public LastBuild {
        parameters = Collections.unmodifiableMap(new LinkedHashMap<>(emptyIfNull(parameters)));
    }
}
