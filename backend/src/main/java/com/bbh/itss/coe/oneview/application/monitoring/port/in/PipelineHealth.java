package com.bbh.itss.coe.oneview.application.monitoring.port.in;

import com.bbh.itss.coe.oneview.application.pipeline.port.in.PipelineView;
import com.bbh.itss.coe.oneview.domain.monitoring.PipelineRun;
import com.bbh.itss.coe.oneview.domain.monitoring.RunResult;

public record PipelineHealth(PipelineView pipeline, RunResult status, PipelineRun lastRun) {
}
