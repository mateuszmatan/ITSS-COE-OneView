package com.bbh.itss.coe.oneview.application.report.port.in;

import com.bbh.itss.coe.oneview.domain.monitoring.RunResult;
import com.bbh.itss.coe.oneview.domain.report.AutoResolvedSetting;
import com.bbh.itss.coe.oneview.domain.report.PipelineReport;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ManagePipelineReportUseCase {

    PipelineReport recordReport(RecordReportCommand command);

    Optional<PipelineReport> getLatestReport(Long pipelineId);

    Optional<PipelineReport> getReportByBuild(Long pipelineId, Long buildNumber);

    String getLatestReportHtml(Long pipelineId);

    String getReportHtmlByBuild(Long pipelineId, Long buildNumber);

    List<PipelineReport> getRecentReports(Long pipelineId, int limit);

    record RecordReportCommand(String pipelineKey, String jobName, Long buildNumber, RunResult result,
                               String branch, String commitHash, Long durationSeconds, Instant finishedAt,
                               Long stagesTotal, Long passed, Long warned, Long failed, Long blocked,
                               Long skipped, String reportHtml, List<AutoResolvedSetting> autoResolved) {
    }
}
