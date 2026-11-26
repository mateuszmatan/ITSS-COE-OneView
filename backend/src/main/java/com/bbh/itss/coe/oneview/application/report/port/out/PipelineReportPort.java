package com.bbh.itss.coe.oneview.application.report.port.out;

import com.bbh.itss.coe.oneview.domain.report.PipelineReport;

import java.util.List;
import java.util.Optional;

public interface PipelineReportPort extends PipelineLookupPort {

    PipelineReport save(PipelineReport report);

    Optional<PipelineReport> findLatestByPipelineId(Long pipelineId);

    Optional<PipelineReport> findByPipelineIdAndBuild(Long pipelineId, Long buildNumber);

    Optional<PipelineReport> findLatestByPipelineKey(String pipelineKey);

    Optional<PipelineReport> findLatestByJobName(String jobName);

    List<PipelineReport> findRecentByPipelineId(Long pipelineId, int limit);
}
