package com.bbh.itss.coe.oneview.domain.jenkins;

import com.bbh.itss.coe.oneview.domain.pipeline.PipelineType;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

@NoArgsConstructor(access = PRIVATE)
public final class Jenkinsfiles {

    public static final String DEFAULT_LIBRARY = "DevSecOpsJenkinsLibrary";

    /** The Jenkinsfile of a pipeline: it loads the DevSecOps library and starts the pipeline with its key. */
    public static String withKey(String library, PipelineType type, String key) {
        return "@Library('" + defaultIfBlank(library, DEFAULT_LIBRARY).trim() + "') _\n\n" + type.entryPoint()
                + "(pipelineKey: '" + key + "')\n";
    }
}
