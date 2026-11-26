package com.bbh.itss.coe.oneview.application.report.port.out;

import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * Finds the pipeline that a Jenkins run reports for. The pipeline report and the report of a pull request decoration
 * find it the same way.
 */
public interface PipelineLookupPort {

    Optional<Long> findPipelineIdByKey(String key);

    /** The pipeline that runs the job, when exactly one pipeline runs it. */
    Optional<Long> findPipelineIdByJobName(String jobName);

    /**
     * The pipeline of a run: the one whose active key the run sent, else the one pipeline that runs its job, else
     * none. A blank key or job is not looked up.
     */
    default Optional<Long> findPipelineOfRun(String key, String jobName) {
        String active = trimToNull(key);
        String job = trimToNull(jobName);
        Optional<Long> byKey = active == null ? Optional.empty() : findPipelineIdByKey(active);
        return byKey.isPresent() || job == null ? byKey : findPipelineIdByJobName(job);
    }
}
