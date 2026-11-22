package com.bbh.itss.coe.oneview.application.monitoring.port.in;

import java.util.List;

public record DepartmentPipelines(List<PipelineHealth> pipelines, String metricsError) {

    public DepartmentPipelines {
        pipelines = List.copyOf(pipelines);
    }
}
